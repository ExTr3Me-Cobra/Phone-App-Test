package com.vipervision.editor

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.abs

/**
 * The Adjust dials for video: one GPU pass with the same maths as the photo editor's
 * [applyAdjustments] (Aire's brightness and contrast, then Image Toolbox's GPU saturation,
 * exposure, wide-range highlights/shadows, white balance, sharpen and vignette filters, with the
 * same dial ranges). [adjustments] is read for every frame, so the preview follows the dials.
 */
@OptIn(UnstableApi::class)
class VideoAdjustEffect(private val adjustments: () -> Adjustments) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Program(useHdr, adjustments)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false

    private class Program(useHdr: Boolean, private val adjustments: () -> Adjustments) :
        BaseGlShaderProgram(useHdr, 1) {

        private val program = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER).apply {
                setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                )
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
        private var width = 1
        private var height = 1

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            width = inputWidth
            height = inputHeight
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            val a = adjustments()
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatsUniform("uTexelSize", floatArrayOf(1f / width, 1f / height))
                program.setFloatUniform("uBrightness", a.t(Adjustment.Brightness) * 0.6f)
                program.setFloatUniform("uContrast", 1f + a.t(Adjustment.Contrast) * 0.8f)
                program.setFloatUniform("uSaturation", 1f + a.t(Adjustment.Saturation))
                program.setFloatUniform("uExposure", a.t(Adjustment.Exposure) * 2f)
                program.setFloatUniform("uShadows", 1f + a.t(Adjustment.Shadows))
                program.setFloatUniform("uHighlights", 1f + a.t(Adjustment.Highlights))
                // White balance as in [applyAdjustments]: kelvin, then GPUImage's temperature scale.
                val warmth = a.t(Adjustment.Warmth)
                val kelvin = if (warmth > 0f) 5000f + warmth * 13300f else 5000f + warmth * 2000f
                val temperature = if (kelvin < 5000f) 0.0004f * (kelvin - 5000f) else 0.00006f * (kelvin - 5000f)
                program.setFloatUniform("uTemperature", temperature)
                program.setFloatUniform("uTint", a.t(Adjustment.Tint))
                program.setFloatUniform("uSharpness", a.t(Adjustment.Sharpness))
                val vignette = a.t(Adjustment.Vignette)
                val start = 0.85f - 0.55f * abs(vignette)
                program.setFloatUniform("uVignette", if (vignette == 0f) 0f else 1f)
                program.setFloatUniform("uVignetteStart", start)
                program.setFloatUniform("uVignetteEnd", start + 0.45f)
                program.setFloatUniform("uVignetteColor", if (vignette > 0f) 0f else 1f)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GlUtil.checkGlError()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
    }

    private companion object {
        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""

        const val FRAGMENT_SHADER = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uExposure;
uniform float uShadows;
uniform float uHighlights;
uniform float uTemperature;
uniform float uTint;
uniform float uSharpness;
uniform float uVignette;
uniform float uVignetteStart;
uniform float uVignetteEnd;
uniform float uVignetteColor;
varying vec2 vTexSamplingCoord;

const mat3 RGB_TO_YIQ = mat3(0.299, 0.587, 0.114, 0.596, -0.274, -0.322, 0.212, -0.523, 0.311);
const mat3 YIQ_TO_RGB = mat3(1.0, 0.956, 0.621, 1.0, -0.272, -0.647, 1.0, -1.105, 1.702);
const vec3 WARM = vec3(0.93, 0.54, 0.0);

// Everything up to sharpening, per pixel (sharpening needs it for the neighbours too).
vec3 adjusted(vec2 uv) {
  vec3 c = texture2D(uTexSampler, uv).rgb;
  // Brightness and contrast (Aire).
  c = clamp(c + uBrightness, 0.0, 1.0);
  c = clamp((c - 0.5) * uContrast + 0.5, 0.0, 1.0);
  // Saturation (GPUImage).
  float lum = dot(c, vec3(0.2125, 0.7154, 0.0721));
  c = mix(vec3(lum), c, uSaturation);
  // Exposure (GPUImage).
  c = clamp(c * pow(2.0, uExposure), 0.0, 1.0);
  // Highlights and shadows, wide range (Image Toolbox).
  if (uShadows != 1.0 || uHighlights != 1.0) {
    float l = max(dot(c, vec3(0.3)), 0.0001);
    float shadow = clamp((pow(l, 1.0 / uShadows) - 0.76 * pow(l, 2.0 / uShadows)) - l, 0.0, 1.0);
    float highlight = clamp((1.0 - (pow(1.0 - l, 1.0 / (2.0 - uHighlights)) - 0.8 * pow(1.0 - l, 2.0 / (2.0 - uHighlights)))) - l, -1.0, 0.0);
    c = (l + shadow + highlight) * (c / l);
    float cl = (l - 0.5) * 1.5 + 0.5;
    c = mix(c, vec3(1.0), cl * cl * cl * (clamp(uHighlights, 1.0, 2.0) - 1.0));
    float icl = 1.0 - cl;
    c = mix(c, vec3(0.0), icl * icl * icl * (1.0 - clamp(uShadows, 0.0, 1.0)));
  }
  // White balance (GPUImage).
  if (uTemperature != 0.0 || uTint != 0.0) {
    vec3 yiq = RGB_TO_YIQ * c;
    yiq.b = clamp(yiq.b + uTint * 0.5226 * 0.1, -0.5226, 0.5226);
    vec3 rgb = YIQ_TO_RGB * yiq;
    vec3 warm = vec3(
      rgb.r < 0.5 ? 2.0 * rgb.r * WARM.r : 1.0 - 2.0 * (1.0 - rgb.r) * (1.0 - WARM.r),
      rgb.g < 0.5 ? 2.0 * rgb.g * WARM.g : 1.0 - 2.0 * (1.0 - rgb.g) * (1.0 - WARM.g),
      rgb.b < 0.5 ? 2.0 * rgb.b * WARM.b : 1.0 - 2.0 * (1.0 - rgb.b) * (1.0 - WARM.b));
    c = mix(rgb, warm, uTemperature);
  }
  return c;
}

void main() {
  vec3 c = adjusted(vTexSamplingCoord);
  // Sharpen (GPUImage): the pixel against its four neighbours.
  if (uSharpness != 0.0) {
    vec3 n = adjusted(vTexSamplingCoord - vec2(uTexelSize.x, 0.0))
           + adjusted(vTexSamplingCoord + vec2(uTexelSize.x, 0.0))
           + adjusted(vTexSamplingCoord - vec2(0.0, uTexelSize.y))
           + adjusted(vTexSamplingCoord + vec2(0.0, uTexelSize.y));
    c = c * (1.0 + 4.0 * uSharpness) - n * uSharpness;
  }
  // Vignette (GPUImage), black for positive and white for negative.
  if (uVignette > 0.0) {
    float d = distance(vTexSamplingCoord, vec2(0.5));
    c = mix(c, vec3(uVignetteColor), smoothstep(uVignetteStart, uVignetteEnd, d));
  }
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
    }
}
