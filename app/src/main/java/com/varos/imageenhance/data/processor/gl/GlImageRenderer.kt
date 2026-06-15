package com.varos.imageenhance.data.processor.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Self-contained OpenGL ES 2.0 off-screen image renderer.
 *
 * Holds one persistent EGL context (created lazily on the calling thread) and
 * runs a chain of fragment-shader passes over an input bitmap using two
 * ping-pong FBO textures, then reads the result back to a [Bitmap]. Every pass
 * gets the standard uniforms `uTexture`, `uValue` and `uTexelSize`, so a filter
 * is fully described by its fragment shader alone.
 *
 * NOT thread-safe; all methods must run on the same single thread.
 */
class GlImageRenderer {

    private var egl: EglCore? = null
    private var quad: FloatBuffer? = null
    private val programs = HashMap<String, Int>() // fragment-shader source -> program id

    // Ping-pong FBO targets + the input texture, (re)allocated when size changes.
    private val fbo = IntArray(2)
    private val fboTex = IntArray(2)
    private var inputTex = 0
    private var width = -1
    private var height = -1

    /** Runs [passes] (fragmentShader to value) over [source]; returns a new bitmap. */
    fun render(source: Bitmap, passes: List<Pair<String, Float>>): Bitmap {
        ensureContext()
        ensureSize(source.width, source.height)

        // Upload source into the input texture.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTex)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, source, 0)

        GLES20.glViewport(0, 0, width, height)
        val texelSize = floatArrayOf(1f / width, 1f / height)

        var srcTex = inputTex
        passes.forEachIndexed { i, (shader, value) ->
            val target = i % 2
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[target])
            drawPass(programFor(shader), srcTex, value, texelSize)
            srcTex = fboTex[target] // output becomes next input
        }

        val result = readPixels()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        return result
    }

    private fun drawPass(program: Int, texture: Int, value: Float, texelSize: FloatArray) {
        GLES20.glUseProgram(program)

        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        val buffer = quad!!
        buffer.position(0)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, buffer)
        GLES20.glEnableVertexAttribArray(posLoc)
        buffer.position(2)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, buffer)
        GLES20.glEnableVertexAttribArray(texLoc)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uValue"), value)
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(program, "uTexelSize"), texelSize[0], texelSize[1],
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun readPixels(): Bitmap {
        val buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        buf.rewind()
        return createBitmap(width, height).apply { copyPixelsFromBuffer(buf) }
    }

    private fun ensureContext() {
        if (egl != null) return
        egl = EglCore().also { it.makeCurrent() }
        quad = ByteBuffer.allocateDirect(QUAD.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(QUAD); position(0) }
        GLES20.glGenTextures(1, IntArray(1).also { inputTex = it[0] }, 0)
        configureTexture(inputTex)
    }

    private fun ensureSize(w: Int, h: Int) {
        if (width == w && height == h && fbo[0] != 0) return
        if (fbo[0] != 0) {
            GLES20.glDeleteFramebuffers(2, fbo, 0)
            GLES20.glDeleteTextures(2, fboTex, 0)
        }
        width = w
        height = h
        GLES20.glGenFramebuffers(2, fbo, 0)
        GLES20.glGenTextures(2, fboTex, 0)
        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex[i])
            configureTexture(fboTex[i])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
            )
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, fboTex[i], 0,
            )
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun configureTexture(tex: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun programFor(fragmentShader: String): Int = programs.getOrPut(fragmentShader) {
        val vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { "Program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private companion object {
        // x, y, u, v — a full-screen triangle strip.
        val QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTex;
            void main() {
                gl_Position = aPosition;
                vTex = aTexCoord;
            }
        """
    }
}
