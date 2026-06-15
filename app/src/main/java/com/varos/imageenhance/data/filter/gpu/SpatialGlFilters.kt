package com.varos.imageenhance.data.filter.gpu

import com.varos.imageenhance.domain.model.FilterParameter
import kotlin.math.roundToInt

/** 3x3 unsharp-mask sharpening, value 0..1. */
class SharpenFilter : GlImageFilter {
    override val id = "sharpen"
    override val displayName = "Sharpen"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()
    override val fragmentShader = SHADER_HEADER + """
        void main() {
            vec2 t = uTexelSize;
            vec3 c = texture2D(uTexture, vTex).rgb;
            vec3 l = texture2D(uTexture, vTex + vec2(-t.x, 0.0)).rgb;
            vec3 r = texture2D(uTexture, vTex + vec2( t.x, 0.0)).rgb;
            vec3 u = texture2D(uTexture, vTex + vec2(0.0, -t.y)).rgb;
            vec3 d = texture2D(uTexture, vTex + vec2(0.0,  t.y)).rgb;
            vec3 s = c * (1.0 + 4.0 * uValue) - uValue * (l + r + u + d);
            gl_FragColor = vec4(clamp(s, 0.0, 1.0), 1.0);
        }
    """
}

/** Sobel edge enhancement (gradient magnitude added back), value 0..1. */
class EdgeEnhanceFilter : GlImageFilter {
    override val id = "edge"
    override val displayName = "Edges"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()
    override val fragmentShader = SHADER_HEADER + """
        float lum(vec3 p) { return dot(p, vec3(0.299, 0.587, 0.114)); }
        void main() {
            vec2 t = uTexelSize;
            float tl = lum(texture2D(uTexture, vTex + vec2(-t.x, -t.y)).rgb);
            float tc = lum(texture2D(uTexture, vTex + vec2( 0.0, -t.y)).rgb);
            float tr = lum(texture2D(uTexture, vTex + vec2( t.x, -t.y)).rgb);
            float ml = lum(texture2D(uTexture, vTex + vec2(-t.x,  0.0)).rgb);
            float mr = lum(texture2D(uTexture, vTex + vec2( t.x,  0.0)).rgb);
            float bl = lum(texture2D(uTexture, vTex + vec2(-t.x,  t.y)).rgb);
            float bc = lum(texture2D(uTexture, vTex + vec2( 0.0,  t.y)).rgb);
            float br = lum(texture2D(uTexture, vTex + vec2( t.x,  t.y)).rgb);
            float gx = (tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl);
            float gy = (bl + 2.0 * bc + br) - (tl + 2.0 * tc + tr);
            float e = (abs(gx) + abs(gy)) * uValue;
            vec3 c = texture2D(uTexture, vTex).rgb;
            gl_FragColor = vec4(clamp(c + e, 0.0, 1.0), 1.0);
        }
    """
}

/** Box blur whose spread grows with value, value 0..1. */
class BlurFilter : GlImageFilter {
    override val id = "blur"
    override val displayName = "Blur"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()
    override val fragmentShader = SHADER_HEADER + """
        void main() {
            vec2 o = uTexelSize * (1.0 + uValue * 6.0);
            vec3 sum = vec3(0.0);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    sum += texture2D(uTexture, vTex + vec2(float(x) * o.x, float(y) * o.y)).rgb;
                }
            }
            gl_FragColor = vec4(sum / 9.0, 1.0);
        }
    """
}

/** Edge-preserving denoise (small bilateral filter), value 0..1 blends strength. */
class DenoiseFilter : GlImageFilter {
    override val id = "denoise"
    override val displayName = "Denoise"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()
    override val fragmentShader = SHADER_HEADER + """
        void main() {
            vec3 c = texture2D(uTexture, vTex).rgb;
            vec3 sum = c;
            float wsum = 1.0;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    if (x == 0 && y == 0) continue;
                    vec3 s = texture2D(uTexture, vTex + vec2(float(x) * uTexelSize.x, float(y) * uTexelSize.y)).rgb;
                    vec3 diff = s - c;
                    float w = exp(-dot(diff, diff) * 20.0);
                    sum += s * w;
                    wsum += w;
                }
            }
            vec3 res = sum / wsum;
            gl_FragColor = vec4(mix(c, res, uValue), 1.0);
        }
    """
}
