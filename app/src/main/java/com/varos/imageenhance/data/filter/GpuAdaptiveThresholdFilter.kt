package com.varos.imageenhance.data.filter

import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * Custom GLES adaptive-threshold filter (GPUImage 2.1.0 has no built-in one).
 *
 * Each pixel is binarized against the mean luminance of a 5x5 neighbourhood
 * (sampled in the fragment shader using the texel size), so uneven lighting and
 * shadows on documents binarize cleanly — the GPU equivalent of Bradley/local
 * thresholding. The texel size uniforms are refreshed whenever the output size
 * changes.
 */
class GpuAdaptiveThresholdFilter : GPUImageFilter(NO_FILTER_VERTEX_SHADER, FRAGMENT_SHADER) {

    private var texelWidthLocation = 0
    private var texelHeightLocation = 0

    override fun onInit() {
        super.onInit()
        texelWidthLocation = GLES20.glGetUniformLocation(program, "texelWidth")
        texelHeightLocation = GLES20.glGetUniformLocation(program, "texelHeight")
    }

    override fun onOutputSizeChanged(width: Int, height: Int) {
        super.onOutputSizeChanged(width, height)
        setFloat(texelWidthLocation, 1.0f / width)
        setFloat(texelHeightLocation, 1.0f / height)
    }

    private companion object {
        const val FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform float texelWidth;
            uniform float texelHeight;

            const vec3 W = vec3(0.299, 0.587, 0.114);

            void main() {
                float lum = dot(texture2D(inputImageTexture, textureCoordinate).rgb, W);
                float sum = 0.0;
                for (int x = -2; x <= 2; x++) {
                    for (int y = -2; y <= 2; y++) {
                        vec2 off = vec2(float(x) * texelWidth, float(y) * texelHeight) * 2.0;
                        sum += dot(texture2D(inputImageTexture, textureCoordinate + off).rgb, W);
                    }
                }
                float mean = sum / 25.0;
                float v = lum < mean * 0.9 ? 0.0 : 1.0;
                gl_FragColor = vec4(vec3(v), 1.0);
            }
        """
    }
}
