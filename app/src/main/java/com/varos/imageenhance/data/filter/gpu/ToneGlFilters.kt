package com.varos.imageenhance.data.filter.gpu

import com.varos.imageenhance.domain.model.FilterParameter
import kotlin.math.roundToInt

/** Additive brightness; ±0.5 is a meaningful range, shown as -100..100. */
class BrightnessFilter : GlImageFilter {
    override val id = "brightness"
    override val displayName = "Brightness"
    override val parameter = FilterParameter(min = -0.5f, max = 0.5f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 200).roundToInt().toString()
    override val fragmentShader = SHADER_HEADER + """
        void main() {
            vec3 c = texture2D(uTexture, vTex).rgb;
            gl_FragColor = vec4(clamp(c + uValue, 0.0, 1.0), 1.0);
        }
    """
}

/** Contrast around mid-gray, 0.6..1.8 (1 = neutral). */
class ContrastFilter : GlImageFilter {
    override val id = "contrast"
    override val displayName = "Contrast"
    override val parameter = FilterParameter(min = 0.6f, max = 1.8f, default = 1f, neutral = 1f)
    override fun format(value: Float) = String.format("%.2f", value)
    override val fragmentShader = SHADER_HEADER + """
        void main() {
            vec3 c = texture2D(uTexture, vTex).rgb;
            gl_FragColor = vec4(clamp((c - 0.5) * uValue + 0.5, 0.0, 1.0), 1.0);
        }
    """
}

/** Grayscale (toggle). */
class GrayscaleFilter : GlImageFilter {
    override val id = "grayscale"
    override val displayName = "Grayscale"
    override val parameter = FilterParameter(
        min = 0f, max = 1f, default = 0f, neutral = 0f, kind = FilterParameter.Kind.TOGGLE,
    )
    override val fragmentShader = SHADER_HEADER + """
        void main() {
            vec3 c = texture2D(uTexture, vTex).rgb;
            float g = dot(c, vec3(0.299, 0.587, 0.114));
            gl_FragColor = vec4(vec3(g), 1.0);
        }
    """
}
