package com.yujian.ai.ui.recognition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.RecognitionPhase
import kotlin.math.PI
import kotlin.math.sin

private val AiBlueCore = Color(0xFFBFEAF2)
private val AiBlueHot = Color(0xFFD8F5FA)
private val AiGoldCore = Color(0xFFF6D99B)
private val AiGoldHot = Color(0xFFFFE7AE)

private data class FieldIntensity(val edge: Float, val filaments: Float, val particles: Float, val speed: Float)
private data class Cubic(val x: Float, val y: Float)
private data class Filament(
    val id: String,
    val color: Color,
    val hot: Color,
    val width: Dp,
    val offset: Float,
    val start: Cubic,
    val control1: Cubic,
    val control2: Cubic,
    val end: Cubic,
)

/** Fixed V1 ambient field. Geometry is intentionally data, not generated art. */
@Composable
fun RecognitionAmbientField(
    phase: RecognitionPhase,
    modifier: Modifier = Modifier,
    visualClockMs: Long? = null,
    lowPerformance: Boolean = false,
) {
    val clock = ambientClock(visualClockMs)
    val intensity = remember(phase) { intensityFor(phase) }
    Canvas(modifier.fillMaxSize()) {
        val paths = rememberAmbientPaths(size)
        drawEdgeBloom(intensity.edge)
        FILAMENTS.forEachIndexed { index, filament ->
            // Three to five paths are normally visible; the others remain below the reveal threshold.
            val visibility = activeMultiplier(index, clock, intensity.filaments)
            if (visibility > 0.01f) drawFilament(paths[index], filament, clock, intensity.speed, visibility, lowPerformance)
        }
        drawParticles(clock, intensity, lowPerformance)
    }
}

@Composable
private fun ambientClock(override: Long?): Long {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ambient-field")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(10_000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "ambient-clock",
    )
    return override ?: (fraction * 10_000f).toLong()
}

private fun intensityFor(phase: RecognitionPhase) = when (phase) {
    RecognitionPhase.CAPTURED -> FieldIntensity(.20f, .15f, .05f, 1f)
    RecognitionPhase.DETECTING -> FieldIntensity(.30f, .35f, .20f, 1f)
    RecognitionPhase.OUTLINE -> FieldIntensity(.22f, .24f, .12f, .82f)
    RecognitionPhase.CLASSIFYING -> FieldIntensity(.18f, .20f, .08f, .65f)
    else -> FieldIntensity(0f, 0f, 0f, 0f)
}

private fun DrawScope.rememberAmbientPaths(size: Size): List<Path> = FILAMENTS.map { filament ->
    Path().apply {
        moveTo(filament.start.x * size.width, filament.start.y * size.height)
        cubicTo(
            filament.control1.x * size.width, filament.control1.y * size.height,
            filament.control2.x * size.width, filament.control2.y * size.height,
            filament.end.x * size.width, filament.end.y * size.height,
        )
    }
}

private fun DrawScope.drawEdgeBloom(alpha: Float) {
    if (alpha <= 0f) return
    // Narrow corner/edge gradients preserve the photo and never tint the full frame.
    drawCircle(AiBlueCore.copy(alpha = alpha * .055f), radius = size.minDimension * .42f, center = Offset(size.width, size.height * .20f))
    drawCircle(AiGoldCore.copy(alpha = alpha * .05f), radius = size.minDimension * .32f, center = Offset(0f, size.height * .16f))
}

private fun DrawScope.drawFilament(path: Path, filament: Filament, clock: Long, speed: Float, alpha: Float, lowPerformance: Boolean) {
    val phase = ((clock / 10_000f * speed + filament.offset) % 1f + 1f) % 1f
    val length = size.maxDimension * 1.7f
    val visible = length * (.18f + ((filament.offset * 100).toInt() % 18) / 100f)
    val effect = PathEffect.dashPathEffect(floatArrayOf(visible, length - visible), -phase * length)
    fun stroke(width: Dp, multiplier: Float) = Stroke(
        width = width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect,
    )
    drawPath(path, filament.color.copy(alpha = alpha * .08f * if (lowPerformance) .8f else 1f), style = stroke(10.dp, .08f))
    drawPath(path, filament.color.copy(alpha = alpha * .22f), style = stroke(4.dp, .22f))
    drawPath(path, filament.hot.copy(alpha = (alpha * .46f).coerceAtMost(.50f)), style = stroke(filament.width, 1f))
}

private fun DrawScope.drawParticles(clock: Long, intensity: FieldIntensity, lowPerformance: Boolean) {
    val count = if (lowPerformance) 4 else 8
    repeat(count) { index ->
        val anchor = PARTICLE_ANCHORS[index]
        val t = ((clock % 5_000L) / 5_000f + index * .137f) % 1f
        val travel = 20f + (index % 5) * 11f
        val x = anchor.x * size.width + sin((t + index) * 2f * PI).toFloat() * travel
        val y = anchor.y * size.height + (t - .5f) * travel
        val hot = index >= 6
        val peak = if (hot) .50f else .35f
        val pulse = sin(t * PI).toFloat().coerceAtLeast(0f)
        drawCircle(
            color = (if (hot) AiGoldHot else AiBlueHot).copy(alpha = (intensity.particles * peak * pulse).coerceAtMost(peak)),
            radius = if (hot) 3.dp.toPx() else 1.5f.dp.toPx(),
            center = Offset(x, y),
        )
    }
}

private fun activeMultiplier(index: Int, clock: Long, base: Float): Float {
    val wave = ((sin((clock / 10_000f + index * .19f) * 2f * PI) + 1.0) / 2.0).toFloat()
    return base * if (wave > .27f) (.65f + wave * .35f) else .10f
}

private val PARTICLE_ANCHORS = listOf(
    Cubic(.84f, .10f), Cubic(.96f, .33f), Cubic(.92f, .64f), Cubic(.73f, .88f),
    Cubic(.08f, .18f), Cubic(.04f, .51f), Cubic(.18f, .90f), Cubic(.48f, .97f),
)

private val FILAMENTS = listOf(
    Filament("B1", AiBlueCore, AiBlueHot, 1.2.dp, .00f, Cubic(.78f, -.02f), Cubic(.94f, .06f), Cubic(1.02f, .22f), Cubic(.96f, .42f)),
    Filament("B2", AiBlueCore, AiBlueHot, 1.0.dp, .23f, Cubic(1.01f, .18f), Cubic(.92f, .31f), Cubic(.95f, .52f), Cubic(1.02f, .68f)),
    Filament("B3", AiBlueCore, AiBlueHot, 1.2.dp, .47f, Cubic(1.02f, .61f), Cubic(.92f, .75f), Cubic(.84f, .89f), Cubic(.66f, 1.02f)),
    Filament("B4", AiBlueCore, AiBlueHot, .8.dp, .71f, Cubic(.22f, 1.02f), Cubic(.38f, .95f), Cubic(.52f, .95f), Cubic(.66f, 1.01f)),
    Filament("G1", AiGoldCore, AiGoldHot, 1.1.dp, .12f, Cubic(-.02f, .18f), Cubic(.03f, .08f), Cubic(.12f, .02f), Cubic(.26f, -.02f)),
    Filament("G2", AiGoldCore, AiGoldHot, .9.dp, .39f, Cubic(-.02f, .33f), Cubic(.04f, .47f), Cubic(.02f, .62f), Cubic(-.01f, .78f)),
    Filament("G3", AiGoldCore, AiGoldHot, 1.2.dp, .64f, Cubic(-.01f, .74f), Cubic(.08f, .86f), Cubic(.20f, .95f), Cubic(.38f, 1.02f)),
)
