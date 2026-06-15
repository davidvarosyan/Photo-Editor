package com.varos.imageenhance.data.filter.gpu

import com.varos.imageenhance.domain.model.ImageFilter

/**
 * A GPU filter implemented as a single OpenGL ES fragment shader (run by our own
 * [com.varos.imageenhance.data.processor.GlImageProcessor] — no third-party GL
 * library). The renderer supplies the standard uniforms every shader can use:
 *
 *  - `sampler2D uTexture`  — the input image
 *  - `float uValue`        — this filter's current value
 *  - `vec2 uTexelSize`     — (1/width, 1/height), for neighbour sampling
 *  - `varying vec2 vTex`   — the texture coordinate
 *
 * So a new filter is just metadata + a shader string.
 */
interface GlImageFilter : ImageFilter {
    val fragmentShader: String
}

/** Common shader preamble shared by all filters. */
const val SHADER_HEADER = """
    precision highp float;
    uniform sampler2D uTexture;
    uniform float uValue;
    uniform vec2 uTexelSize;
    varying vec2 vTex;
"""
