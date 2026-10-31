package com.yujian.ai.ui.waterlight.renderer

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Kotlin port of ethanfox/water-caustics src/caustic-field.ts at
 * de856eec62819b7c4176366db73b8fd37a1b1455 (MIT).
 * Changes: JS number -> Kotlin Double and RGBA tuple -> immutable sample.
 * The original license is retained at licenses/water-caustics-MIT.txt.
 */
object WaterCausticsField {
    data class Sample(val red: Double, val green: Double, val blue: Double, val alpha: Double)
    data class Rgb(val red: Double, val green: Double, val blue: Double)

    fun rotate(x: Double, y: Double, angle: Double): Pair<Double, Double> =
        Pair(cos(angle) * x - sin(angle) * y, sin(angle) * x + cos(angle) * y)

    fun octave(uvX: Double, uvY: Double, t: Double, fi: Double): Double {
        val rotated = rotate(uvX, uvY, 0.41 + fi * 2.39996323)
        var px = rotated.first
        var py = rotated.second
        for (k in 0..1) {
            val fk = k.toDouble()
            val wx = sin(py * (1.17 + fi * 0.19 + fk * 0.13) + px * (0.23 + fi * 0.07) +
                t * (0.31 + fi * 0.05) + fi * 1.61 + fk)
            val wy = cos(px * (0.89 + fi * 0.17 + fk * 0.11) - py * (0.29 + fi * 0.09) -
                t * (0.27 + fi * 0.04) + fi * 1.13)
            val amp = 0.46 / (1 + fi * 0.16 + fk * 0.11)
            px += wx * amp
            py += wy * amp
        }
        val d1x = 1.0 + fi * 0.21
        val d1y = 0.33 + fi * 0.13
        val d2x = -0.51 - fi * 0.11
        val d2y = 1.09 + fi * 0.08
        val ridge = sin(px * d1x + py * d1y + t * 0.09) * sin(px * d2x + py * d2y - t * 0.07)
        return 0.165 / (abs(ridge) * 5.6 + 0.22)
    }

    fun field(uvX: Double, uvY: Double, t: Double): Double {
        val px = uvX + sin(uvY * 0.37 + t * 0.19) * 0.42
        val py = uvY + cos(uvX * 0.31 - t * 0.15) * 0.42
        val a = octave(px, py, t, 0.0)
        val p2 = rotate(px * 1.61803399 + 1.83, py * 1.61803399 - 1.27, 1.17809725)
        val b = octave(p2.first, p2.second, t * 0.79, 1.0)
        val p3 = rotate(px * 2.61803399 + 2.54, py * 2.61803399 - 0.74, 1.54809725)
        val c = octave(p3.first, p3.second, t * 0.62, 2.0)
        val acc = max(a * 1.12, b * 0.78) + c * 0.34
        return min(max(acc, 0.0), 1.95).pow(1.45)
    }

    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = min(max((x - edge0) / (edge1 - edge0), 0.0), 1.0)
        return t * t * (3 - 2 * t)
    }

    fun sampleCaustic(
        fragX: Double, fragY: Double, resX: Double, resY: Double,
        time: Double, scale: Double, intensity: Double, color: Rgb, tint: Rgb, tintStrength: Double,
    ): Sample {
        val denom = max(resY, 1.0)
        val uvX = ((fragX * 2 - resX) / denom) * 1.72 * scale
        val uvY = ((fragY * 2 - resY) / denom) * 1.72 * scale
        val c = field(uvX, uvY, time * 0.36)
        val veins = smoothstep(0.15, 1.08, c)
        val haze = smoothstep(0.03, 0.78, c) * 0.3
        val glow = (veins * 0.9 + haze) * intensity
        val mix = min(max(glow, 0.0), 1.0)
        val alpha = min(max(tintStrength + glow * (0.88 - tintStrength * 0.35), 0.0), 1.0)
        return Sample(tint.red + (color.red - tint.red) * mix,
            tint.green + (color.green - tint.green) * mix,
            tint.blue + (color.blue - tint.blue) * mix, alpha)
    }
}
