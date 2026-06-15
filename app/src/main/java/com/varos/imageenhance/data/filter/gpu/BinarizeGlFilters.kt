package com.varos.imageenhance.data.filter.gpu

import com.varos.imageenhance.domain.model.FilterParameter

/**
 * One-tap document readability: adaptive thresholding in a fragment shader. Each
 * pixel is compared to the mean luminance of its 5x5 neighbourhood, so uneven
 * lighting/shadows on receipts, notes and pages binarize cleanly — far better
 * than a global cut. Rendered as a toggle.
 */
class DocumentScanFilter : GlImageFilter {
    override val id = "document"
    override val displayName = "Document"
    override val parameter = FilterParameter(
        min = 0f, max = 1f, default = 0f, neutral = 0f, kind = FilterParameter.Kind.TOGGLE,
    )
    override val fragmentShader = SHADER_HEADER + """
        float lum(vec3 p) { return dot(p, vec3(0.299, 0.587, 0.114)); }
        void main() {
            float c = lum(texture2D(uTexture, vTex).rgb);
            float sum = 0.0;
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    vec2 off = vec2(float(x) * uTexelSize.x, float(y) * uTexelSize.y) * 2.0;
                    sum += lum(texture2D(uTexture, vTex + off).rgb);
                }
            }
            float mean = sum / 25.0;
            float v = c < mean * 0.9 ? 0.0 : 1.0;
            gl_FragColor = vec4(vec3(v), 1.0);
        }
    """
}
