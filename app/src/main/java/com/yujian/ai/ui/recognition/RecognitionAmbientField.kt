package com.yujian.ai.ui.recognition

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.ai.RecognitionPhase
import kotlin.math.PI
import kotlin.math.sin

private val AiBlueCore = Color(0xFFBFEAF2)
private val AiBlueHot = Color(0xFFD8F5FA)
private val AiGoldCore = Color(0xFFF6D99B)
private val AiGoldHot = Color(0xFFFFE7AE)

private data class FieldIntensity(
    val edge: Float,
    val primary: Float,
    val secondary: Float,
    val particles: Float,
    val speed: Float,
)

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

/**
 * Recognition V1.2 ambient AI field.
 *
 * The photo always stays fully opaque. Energy is constrained to the perimeter:
 * primary blue/gold filaments provide the readable AI field while secondary
 * hairline filaments and sparse nodes supply the high-fidelity texture.
 */
@Composable
fun RecognitionAmbientField(
    phase: RecognitionPhase,
    modifier: Modifier = Modifier,
    visualClockMs: Long? = null,
    lowPerformance: Boolean = false,
    reduceMotion: Boolean = false,
    resolveProgress: Float = 0f,
) {
    val clock = ambientClock(visualClockMs, reduceMotion)
    val intensity = remember(phase) { intensityFor(phase) }
    val remaining = 1f - resolveProgress.coerceIn(0f, 1f)
    // RESULT should feel like the AI field releases quickly rather than lingering
    // over the normal result screen.
    val fade = remaining * remaining
    val evidenceTag = "recognition-ambient-" +
        (if (reduceMotion) "reduced-motion" else "motion") + "-" +
        (if (lowPerformance) "low-performance" else "normal-performance")

    Canvas(modifier.fillMaxSize().testTag(evidenceTag)) {
        val primaryPaths = rememberAmbientPaths(size, PRIMARY_FILAMENTS)
        val secondaryPaths = rememberAmbientPaths(size, SECONDARY_FILAMENTS)

        drawEdgeBloom(intensity.edge * fade)

        PRIMARY_FILAMENTS.forEachIndexed { index, filament ->
            val visibility = activeMultiplier(index, clock, intensity.primary) * fade
            if (visibility > 0.01f) {
                drawPrimaryFilament(
                    path = primaryPaths[index],
                    filament = filament,
                    clock = clock,
                    speed = intensity.speed,
                    alpha = visibility,
                    lowPerformance = lowPerformance,
                )
            }
        }

        if (!lowPerformance) {
            SECONDARY_FILAMENTS.forEachIndexed { index, filament ->
                val visibility = activeMultiplier(index + PRIMARY_FILAMENTS.size, clock, intensity.secondary) * fade
                if (visibility > 0.008f) {
                    drawSecondaryFilament(
                        path = secondaryPaths[index],
                        filament = filament,
                        clock = clock,
                        speed = intensity.speed * .84f,
                        alpha = visibility,
                    )
                }
            }
        }

        drawEnergyNodes(clock, intensity, fade, lowPerformance)
        drawParticles(clock, intensity.copy(particles = intensity.particles * fade), lowPerformance)
    }
}

@Composable
private fun ambientClock(override: Long?, reduceMotion: Boolean): Long {
    if (override != null) return override
    if (reduceMotion) return 0L
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ambient-field")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 10_000,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "ambient-clock",
    )
    return (fraction * 10_000f).toLong()
}

/**
 * Perceptual V1.2 energy curve:
 * CAPTURED is already clearly alive, DETECTING remains energetic,
 * OUTLINE gives visual priority to the real fish, CLASSIFYING slows down.
 */
private fun intensityFor(phase: RecognitionPhase) = when (phase) {
    RecognitionPhase.CAPTURED -> FieldIntensity(.70f, .72f, .34f, .24f, 1.00f)
    RecognitionPhase.DETECTING -> FieldIntensity(.64f, .78f, .30f, .26f, .92f)
    RecognitionPhase.OUTLINE -> FieldIntensity(.50f, .56f, .20f, .17f, .78f)
    RecognitionPhase.CLASSIFYING -> FieldIntensity(.34f, .42f, .12f, .10f, .52f)
    else -> FieldIntensity(0f, 0f, 0f, 0f, 0f)
}

private fun DrawScope.rememberAmbientPaths(size: Size, filaments: List<Filament>): List<Path> =
    filaments.map { filament ->
        Path().apply {
            moveTo(filament.start.x * size.width, filament.start.y * size.height)
            cubicTo(
                filament.control1.x * size.width,
                filament.control1.y * size.height,
                filament.control2.x * size.width,
                filament.control2.y * size.height,
                filament.end.x * size.width,
                filament.end.y * size.height,
            )
        }
    }

private fun DrawScope.drawEdgeBloom(alpha: Float) {
    if (alpha <= 0f) return

    // Local corner/edge bloom only. Never tint the whole image.
    drawCircle(
        AiBlueCore.copy(alpha = (alpha * .16f).coerceAtMost(.12f)),
        radius = size.minDimension * .34f,
        center = Offset(size.width * 1.02f, size.height * .20f),
    )
    drawCircle(
        AiGoldCore.copy(alpha = (alpha * .14f).coerceAtMost(.11f)),
        radius = size.minDimension * .30f,
        center = Offset(-size.width * .02f, size.height * .14f),
    )
    drawCircle(
        AiBlueCore.copy(alpha = (alpha * .12f).coerceAtMost(.09f)),
        radius = size.minDimension * .30f,
        center = Offset(-size.width * .03f, size.height * .78f),
    )
    drawCircle(
        AiGoldCore.copy(alpha = (alpha * .10f).coerceAtMost(.08f)),
        radius = size.minDimension * .34f,
        center = Offset(size.width * 1.03f, size.height * .82f),
    )
}

private fun DrawScope.drawPrimaryFilament(
    path: Path,
    filament: Filament,
    clock: Long,
    speed: Float,
    alpha: Float,
    lowPerformance: Boolean,
) {
    val phase = ((clock / 10_000f * speed + filament.offset) % 1f + 1f) % 1f
    val length = size.maxDimension * 1.7f
    val visible = length * (.18f + ((filament.offset * 100).toInt() % 18) / 100f)
    val effect = PathEffect.dashPathEffect(floatArrayOf(visible, length - visible), -phase * length)

    fun stroke(width: Dp) = Stroke(
        width = width.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = effect,
    )

    val perf = if (lowPerformance) .80f else 1f
    drawPath(
        path,
        filament.color.copy(alpha = (alpha * .10f * perf).coerceAtMost(.08f)),
        style = stroke(10.dp),
    )
    drawPath(
        path,
        filament.color.copy(alpha = (alpha * .28f).coerceAtMost(.22f)),
        style = stroke(4.dp),
    )
    drawPath(
        path,
        filament.hot.copy(alpha = (alpha * 1.08f).coerceAtMost(.58f)),
        style = stroke(filament.width),
    )
}

private fun DrawScope.drawSecondaryFilament(
    path: Path,
    filament: Filament,
    clock: Long,
    speed: Float,
    alpha: Float,
) {
    val phase = ((clock / 10_000f * speed + filament.offset) % 1f + 1f) % 1f
    val length = size.maxDimension * 1.8f
    val visible = length * (.20f + ((filament.offset * 100).toInt() % 12) / 100f)
    val effect = PathEffect.dashPathEffect(floatArrayOf(visible, length - visible), -phase * length)

    fun stroke(width: Dp) = Stroke(
        width = width.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = effect,
    )

    drawPath(
        path,
        filament.color.copy(alpha = (alpha * .20f).coerceAtMost(.10f)),
        style = stroke(2.4.dp),
    )
    drawPath(
        path,
        filament.hot.copy(alpha = (alpha * .90f).coerceAtMost(.34f)),
        style = stroke(filament.width),
    )
}

private fun DrawScope.drawEnergyNodes(
    clock: Long,
    intensity: FieldIntensity,
    fade: Float,
    lowPerformance: Boolean,
) {
    val count = if (lowPerformance) 2 else ENERGY_NODES.size
    repeat(count) { index ->
        val node = ENERGY_NODES[index]
        val t = ((clock % 4_800L) / 4_800f + index * .21f) % 1f
        val pulse = (.55f + .45f * sin(t * 2f * PI).toFloat()).coerceIn(.18f, 1f)
        val base = intensity.primary * fade * pulse
        val center = Offset(node.x * size.width, node.y * size.height)
        val color = if (index % 2 == 0) AiGoldHot else AiBlueHot
        drawCircle(color.copy(alpha = (base * .08f).coerceAtMost(.065f)), 12.dp.toPx(), center)
        drawCircle(color.copy(alpha = (base * .34f).coerceAtMost(.28f)), 2.2.dp.toPx(), center)
    }
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
            color = (if (hot) AiGoldHot else AiBlueHot).copy(
                alpha = (intensity.particles * peak * pulse).coerceAtMost(peak),
            ),
            radius = if (hot) 3.dp.toPx() else 1.5f.dp.toPx(),
            center = Offset(x, y),
        )
    }
}

private fun activeMultiplier(index: Int, clock: Long, base: Float): Float {
    val wave = ((sin((clock / 10_000f + index * .19f) * 2f * PI) + 1.0) / 2.0).toFloat()
    return base * if (wave > .27f) (.65f + wave * .35f) else .10f
}

private val ENERGY_NODES = listOf(
    Cubic(.96f, .13f),
    Cubic(.98f, .54f),
    Cubic(.10f, .88f),
    Cubic(.03f, .28f),
)

private val PARTICLE_ANCHORS = listOf(
    Cubic(.84f, .10f),
    Cubic(.96f, .33f),
    Cubic(.92f, .64f),
    Cubic(.73f, .88f),
    Cubic(.08f, .18f),
    Cubic(.04f, .51f),
    Cubic(.18f, .90f),
    Cubic(.48f, .97f),
)

/** Frozen V1.1 primary paths; V1.2 changes visibility, not the identity of these paths. */
private val PRIMARY_FILAMENTS = listOf(
    Filament("B1", AiBlueCore, AiBlueHot, 1.2.dp, .00f, Cubic(.78f, -.02f), Cubic(.94f, .06f), Cubic(1.02f, .22f), Cubic(.96f, .42f)),
    Filament("B2", AiBlueCore, AiBlueHot, 1.0.dp, .23f, Cubic(1.01f, .18f), Cubic(.92f, .31f), Cubic(.95f, .52f), Cubic(1.02f, .68f)),
    Filament("B3", AiBlueCore, AiBlueHot, 1.2.dp, .47f, Cubic(1.02f, .61f), Cubic(.92f, .75f), Cubic(.84f, .89f), Cubic(.66f, 1.02f)),
    Filament("B4", AiBlueCore, AiBlueHot, .8.dp, .71f, Cubic(.22f, 1.02f), Cubic(.38f, .95f), Cubic(.52f, .95f), Cubic(.66f, 1.01f)),
    Filament("G1", AiGoldCore, AiGoldHot, 1.1.dp, .12f, Cubic(-.02f, .18f), Cubic(.03f, .08f), Cubic(.12f, .02f), Cubic(.26f, -.02f)),
    Filament("G2", AiGoldCore, AiGoldHot, .9.dp, .39f, Cubic(-.02f, .33f), Cubic(.04f, .47f), Cubic(.02f, .62f), Cubic(-.01f, .78f)),
    Filament("G3", AiGoldCore, AiGoldHot, 1.2.dp, .64f, Cubic(-.01f, .74f), Cubic(.08f, .86f), Cubic(.20f, .95f), Cubic(.38f, 1.02f)),
)

/**
 * Hairline paths stay near the perimeter and intentionally do not connect into
 * a closed ring. They add the dense photographic "energy field" feel from the
 * approved high-fidelity authority without becoming HUD chrome.
 */
private val SECONDARY_FILAMENTS = listOf(
    Filament("SB1", AiBlueCore, AiBlueHot, .65.dp, .08f, Cubic(.58f, -.01f), Cubic(.76f, .01f), Cubic(.91f, .08f), Cubic(1.01f, .20f)),
    Filament("SB2", AiBlueCore, AiBlueHot, .55.dp, .31f, Cubic(1.01f, .32f), Cubic(.98f, .46f), Cubic(.99f, .59f), Cubic(1.01f, .76f)),
    Filament("SB3", AiBlueCore, AiBlueHot, .70.dp, .56f, Cubic(.98f, .73f), Cubic(.91f, .88f), Cubic(.80f, .97f), Cubic(.58f, 1.01f)),
    Filament("SB4", AiBlueCore, AiBlueHot, .55.dp, .78f, Cubic(.03f, .62f), Cubic(.02f, .75f), Cubic(.08f, .89f), Cubic(.24f, 1.01f)),
    Filament("SG1", AiGoldCore, AiGoldHot, .60.dp, .17f, Cubic(-.01f, .10f), Cubic(.10f, .03f), Cubic(.24f, .00f), Cubic(.42f, -.01f)),
    Filament("SG2", AiGoldCore, AiGoldHot, .55.dp, .44f, Cubic(-.01f, .24f), Cubic(.02f, .39f), Cubic(.01f, .54f), Cubic(.00f, .67f)),
    Filament("SG3", AiGoldCore, AiGoldHot, .65.dp, .67f, Cubic(.00f, .79f), Cubic(.10f, .91f), Cubic(.25f, .98f), Cubic(.46f, 1.01f)),
    Filament("SG4", AiGoldCore, AiGoldHot, .55.dp, .88f, Cubic(.70f, 1.01f), Cubic(.83f, .97f), Cubic(.94f, .89f), Cubic(1.01f, .78f)),
)
