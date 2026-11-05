Warning: truncated output (original token count: 13918)
Total output lines: 1110

package com.yujian.ai.ui.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.identify.DisplayRect
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneMaskState
import com.yujian.ai.ui.waterlight.core.SceneResourceState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private const val PERIMETER_LOG_TAG = "RecognitionPerimeter"
private const val NODE_POSITION_FLOATS = RecognitionPerimeterShaderMath.SPARKLE_COUNT * 2

internal fun recognitionPerimeterOpacity(
    processingClockStarted: Boolean,
    reduceMotion: Boolean,
    frame: WaterLightFrame,
): Float {
    if (!processingClockStarted) return 0f
    val scene = frame.sceneFrame
    val waitingForModelJoin = scene.stage == RecognitionSceneStage.PATH_TO_FISH &&
        scene.modelGates.detector == SceneResourceState.READY &&
        (scene.modelGates.classifier == SceneResourceState.WAITING ||
            scene.modelGates.slimSam == SceneMaskState.PENDING)
    if (waitingForModelJoin) return if (reduceMotion) .06f else .12f
    if (reduceMotion) return when (scene.stage) {
        RecognitionSceneStage.PHOTO_STABLE,
        RecognitionSceneStage.ORB_WAKE,
        RecognitionSceneStage.ORB_RELEASE -> 0f
        RecognitionSceneStage.ORB_RETURN,
        RecognitionSceneStage.ORB_TO_NAME,
        RecognitionSceneStage.SPECIES_NAME_REVEAL,
        RecognitionSceneStage.RESIDUE_SETTLE -> .04f
        else -> .10f
    }
    return when (scene.stage) {
        RecognitionSceneStage.PHOTO_STABLE,
        RecognitionSceneStage.ORB_WAKE,
        RecognitionSceneStage.ORB_RELEASE -> 0f
        RecognitionSceneStage.ORB_RETURN -> 1f - .82f * scene.stageProgress.coerceIn(0f, 1f)
        RecognitionSceneStage.ORB_TO_NAME -> .18f * (1f - scene.stageProgress.coerceIn(0f, 1f))
        RecognitionSceneStage.SPECIES_NAME_REVEAL -> .08f
        RecognitionSceneStage.RESIDUE_SETTLE -> .08f * (1f - scene.stageProgress.coerceIn(0f, 1f))
        else -> 1f
    }
}

internal fun recognitionPerimeterProgress(
    frame: WaterLightFrame,
    profile: RecognitionPerimeterOpticalProfileV1 = RecognitionPerimeterOpticalProfileV1.EFFECT_02,
    perimeterStartOpticalMs: Long? = null,
    spreadDurationMs: Long = 1_200L,
): Float = RecognitionPerimeterShaderMath.expansionProgress(
    frame.sceneFrame, profile, perimeterStartOpticalMs, spreadDurationMs,
)

internal fun recognitionPerimeterActive(processingClockStarted: Boolean, phase: RecognitionPhase): Boolean =
    processingClockStarted && phase != RecognitionPhase.RESULT && phase != RecognitionPhase.FAILURE

/**
 * The TextureView renders the actual photo plus perimeter in one surface. Compose keeps the
 * unchanged photo underneath until the matching GPU frame has been presented.
 */
@Composable
internal fun RecognitionPerimeterShaderOverlay(
    waterLightFrame: WaterLightFrame,
    bitmap: Bitmap,
    photoTransform: RecognitionImageTransform,
    elapsedMs: Long,
    processingClockStarted: Boolean,
    sessionKey: Any?,
    recognitionSessionId: String,
    recognitionAttemptId: String,
    imageId: String,
    photoSha256: String,
    phase: RecognitionPhase,
    reduceMotion: Boolean = false,
    referenceMetrics: RecognitionPerimeterReferenceMetrics? = null,
    material: WaterLightMaterialProfile = WaterLightMaterialProfile.PERIMETER,
    opticalProfile: RecognitionPerimeterOpticalProfileV1 = RecognitionPerimeterOpticalProfileV1.EFFECT_02,
    energyEvent: EnergyEvent? = null,
    modifier: Modifier = Modifier,
) {
    if (waterLightFrame.sessionId != recognitionSessionId || waterLightFrame.photoSha256 != photoSha256 ||
        !waterLightFrame.sceneFrame.isIdentityValid) return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val identity = sessionKey ?: photoTransform
    val view = remember(context, identity) { RecognitionPerimeterTextureView(context) }
    val photoRect = photoTransform.photoContentRect
    val mapping = RecognitionPerimeterShaderMath.coordinateScale(
        metrics = referenceMetrics,
        targetWidthPx = photoRect.width,
        targetHeightPx = photoRect.height,
    )
    val sourceCssWidth = referenceMetrics?.cssWidthPx ?: photoRect.width
    val sourceCssHeight = referenceMetrics?.cssHeightPx ?: photoRect.height
    val path = remember(
        sourceCssWidth,
        sourceCssHeight,
        opticalProfile.marginRatio,
        opticalProfile.cornerRadiusRatio,
        mapping.isMeasured,
    ) {
        RecognitionPerimeterShaderMath.generatePerimeterPath(
            width = sourceCssWidth,
            height = sourceCssHeight,
            margin = minOf(sourceCssWidth, sourceCssHeight) * opticalProfile.marginRatio,
            cornerRadius = minOf(sourceCssWidth, sourceCssHeight) * opticalProfile.cornerRadiusRatio,
        )
    }
    val sceneFrame = waterLightFrame.sceneFrame
    val effectiveReduceMotion = reduceMotion || waterLightFrame.reducedMotion
    val timeSec = if (effectiveReduceMotion) 0f else
        RecognitionPerimeterShaderMath.sourceTimeSec(sceneFrame, opticalProfile.speed)
    val stageStartReferenceMs = (sceneFrame.opticalReferenceElapsedMs - sceneFrame.stageElapsedMs).coerceAtLeast(0L)
    var perimeterStartOpticalMs by remember(
        recognitionSessionId, recognitionAttemptId, imageId, photoSha256, sceneFrame.geometryVersion,
    ) { mutableStateOf<Long?>(null) }
    LaunchedEffect(
        recognitionSessionId, recognitionAttemptId, imageId, photoSha256,
        sceneFrame.geometryVersion, sceneFrame.stage, stageStartReferenceMs,
    ) {
        when (sceneFrame.stage) {
            RecognitionSceneStage.PHOTO_STABLE,
            RecognitionSceneStage.ORB_WAKE,
            RecognitionSceneStage.ORB_RELEASE -> perimeterStartOpticalMs = null
            RecognitionSceneStage.PERIMETER_ARRIVAL -> perimeterStartOpticalMs = stageStartReferenceMs
            else -> if (perimeterStartOpticalMs == null) {
                // The host may mount after arrival. Use the frozen 125ms stage minimum
                // as a conservative reconstruction from this same SceneClock sample.
                perimeterStartOpticalMs = (stageStartReferenceMs - RecognitionSceneStage.PERIMETER_ARRIVAL.minimumVisibleMs)
                    .coerceAtLeast(0L)
            }
        }
    }
    val progress = if (effectiveReduceMotion) 1f else recognitionPerimeterProgress(
        waterLightFrame,
        opticalProfile,
        perimeterStartOpticalMs,
        material.perimeterSpreadDurationMs,
    )
    val perimeterOpacity = recognitionPerimeterOpacity(
        processingClockStarted = processingClockStarted,
        reduceMotion = effectiveReduceMotion,
        frame = waterLightFrame,
    )
    val nodes = RecognitionPerimeterShaderMath.sparkles(
        path = path,
        timeSec = timeSec,
        activeCoverage = progress,
        pathScale = mapping.pathPxPerCssUnit,
        photoOffsetX = 0f,
        photoOffsetY = 0f,
    )
    val positions = FloatArray(NODE_POSITION_FLOATS).also { values ->
        nodes.forEachIndexed { index, node ->
            values[index * 2] = node.x
            values[index * 2 + 1] = node.y
        }
    }
    // Stage progress and model gates own its lifetime. P7 can drive opacity to zero,
    // but an absolute elapsed-time cutoff must not terminate a slow real recognition.
    val active = recognitionPerimeterActive(processingClockStarted, phase)
    val frame = PerimeterFrame(
        bitmap = bitmap,
        photoBounds = DisplayRect(0f, 0f, photoRect.width, photoRect.height),
        imageBounds = DisplayRect(photoTransform.translationX - photoRect.left,
            photoTransform.translationY - photoRect.top,
            photoTransform.translationX + photoTransform.drawnWidth - photoRect.left,
            photoTransform.translationY + photoTransform.drawnHeight - photoRect.top),
        geometryVersion = waterLightFrame.geometryVersion,
        recognitionSessionId = recognitionSessionId,
        recognitionAttemptId = recognitionAttemptId,
        imageId = imageId,
        photoSha256 = photoSha256,
        elapsedMs = elapsedMs.coerceAtLeast(0L),
        opticalReferenceElapsedMs = sceneFrame.opticalReferenceElapsedMs,
        timeSec = timeSec,
        progress = progress,
        perimeterOpacity = perimeterOpacity * material.opacity,
        shaderCoordinateScale = mapping.shaderPxPerSourceUnit,
        pathCoordinateScale = mapping.pathPxPerCssUnit,
        nodePositions = positions,
        coordinateMappingMeasured = mapping.isMeasured,
        material = material,
        opticalProfile = opticalProfile,
        energyLevel = energyEvent?.takeIf {
            it.matches(waterLightFrame) && it.imageId == imageId && it.photoSha256 == photoSha256 &&
                it.feedback in setOf(EnergyFeedback.RELEASE, EnergyFeedback.ARRIVE)
        }?.level?.coerceIn(0f, 1f) ?: 0f,
        active = active,
    )

    val density = LocalDensity.current
    AndroidView(
        factory = { view },
        modifier = Modifier.offset { IntOffset(photoRect.left.roundToInt(), photoRect.top.roundToInt()) }
            .size(with(density) { photoRect.width.toDp() }, with(density) { photoRect.height.toDp() })
            .then(modifier),
        update = { it.submit(frame) },
    )

    DisposableEffect(view, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.setHostResumed(true)
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> view.setHostResumed(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        view.setHostResumed(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.release()
        }
    }
}

private data class PerimeterFrame(
    val bitmap: Bitmap,
    val photoBounds: DisplayRect,
    val imageBounds: DisplayRect,
    val geometryVersion: Long,
    val recognitionSessionId: String,
    val recognitionAttemptId: String,
    val imageId: String,
    val photoSha256: String,
    val elapsedMs: Long,
    val opticalReferenceElapsedMs: Long,
    val timeSec: Float,
    val progress: Float,
    val perimeterOpacity: Float,
    val shaderCoordinateScale: Float,
    val pathCoordinateScale: Float,
    val nodePositions: FloatArray,
    val coordinateMappingMeasured: Boolean,
    val material: WaterLightMaterialProfile,
    val opticalProfile: RecognitionPerimeterOpticalProfileV1,
    val energyLevel: Float,
    val active: Boolean,
)

private class RecognitionPerimeterTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private val worker = HandlerThread("RecognitionPhotoGles").also { it.start() }
    private val handler = Handler(worker.looper)
    private val renderer = RecognitionPerimeterGlRenderer()
    private val released = AtomicBoolean(false)
    private var texture: SurfaceTexture? = null
    @Volatile private var hostResumed = false
    @Volatile private var latest: PerimeterFrame? = null
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var contextGl: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surfaceGl: EGLSurface = EGL14.EGL_NO_SURFACE
    private var surface: Surface? = null
    private var initialized = false
    private var shownIdentity: String? = null
    @Volatile private var surfaceEpoch = 0L

    init {
        alpha = 0f
        isOpaque = true
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 16f * resources.displayMetrics.density)
            }
        }
        surfaceTextureListener = this
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
        isClickable = false
        Log.i(PERIMETER_LOG_TAG, "PHOTO_COMPOSITOR=TEXTURE_VIEW_OPAQUE_PHOTO_PLUS_LIGHT")
    }

    fun submit(frame: PerimeterFrame) {
        if (released.get()) return
        val key = frameKey(frame)
        if (shownIdentity != key) {
            alpha = 0f
            shownIdentity = null
        }
        latest = frame
        render(frame)
    }

    fun setHostResumed(resumed: Boolean) {
        hostResumed = resumed
        if (resumed) latest?.let(::render) else {
            alpha = 0f
            shownIdentity = null
            Log.i(PERIMETER_LOG_TAG, "PHOTO_COMPOSE_FALLBACK=LIFECYCLE_PAUSED")
        }
    }

    private fun render(frame: PerimeterFrame) {
        if (!hostResumed || released.get() || !isAvailable) return
        val currentTexture = texture ?: return
        val epoch = surfaceEpoch
        val frameIdentity = frameKey(frame)
        handler.post {
            if (released.get() || !hostResumed || currentTexture !== texture || epoch != surfaceEpoch ||
                latest?.let(::frameKey) != frameIdentity) return@post
            if (!initialized …6418 tokens truncated…Positions[1].xy;
                if (index == 3) return uNodePositions[1].zw;
                if (index == 4) return uNodePositions[2].xy;
                if (index == 5) return uNodePositions[2].zw;
                if (index == 6) return uNodePositions[3].xy;
                if (index == 7) return uNodePositions[3].zw;
                if (index == 8) return uNodePositions[4].xy;
                if (index == 9) return uNodePositions[4].zw;
                if (index == 10) return uNodePositions[5].xy;
                if (index == 11) return uNodePositions[5].zw;
                if (index == 12) return uNodePositions[6].xy;
                if (index == 13) return uNodePositions[6].zw;
                if (index == 14) return uNodePositions[7].xy;
                return uNodePositions[7].zw;
            }

            float seededPhase(float index) {
                vec2 p = fract(vec2(index, index + 19.19) * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y) * (2.0 * PI);
            }

            float circleCoverage(float distanceToCenter, float radius, float aaWidth) {
                return 1.0 - smoothstep(max(0.0, radius - aaWidth), radius + aaWidth, distanceToCenter);
            }

            float segmentDistance(vec2 p, vec2 a, vec2 b) {
                vec2 ab = b - a;
                float h = clamp(dot(p - a, ab) / max(dot(ab, ab), 0.0001), 0.0, 1.0);
                return length(p - a - h * ab);
            }

            void screenOver(inout vec3 dstPremul, inout float dstAlpha, vec3 sourceColor, float sourceAlpha) {
                float a = clamp(sourceAlpha, 0.0, 1.0);
                dstPremul = sourceColor * a + dstPremul * (1.0 - a);
                dstAlpha = a + dstAlpha * (1.0 - a);
            }

            void main() {
                vec2 pTop = vec2(gl_FragCoord.x, uViewport.y - gl_FragCoord.y);
                bool insidePhoto = pTop.x >= uPhotoBounds.x && pTop.y >= uPhotoBounds.y &&
                    pTop.x <= uPhotoBounds.x + uPhotoBounds.z &&
                    pTop.y <= uPhotoBounds.y + uPhotoBounds.w;
                if (!insidePhoto) {
                    gl_FragColor = vec4(0.0);
                    return;
                }
                vec2 photoUv = (pTop - uImageRect.xy) / max(uImageRect.zw, vec2(1.0));
                vec3 base = texture2D(uPhoto, photoUv).rgb;

                // Source shader coordinates are bottom-left-origin WebGL backing pixels.
                float sourceScale = max(uMotion.z, 0.0001);
                vec2 frag = vec2(pTop.x - uPhotoBounds.x, uPhotoBounds.y + uPhotoBounds.w - pTop.y) / sourceScale;
                vec2 res = uPhotoBounds.zw / sourceScale;
                vec2 uv = (frag * 2.0 - res) / max(res.y, 1.0);
                float normY = frag.y / max(res.y, 1.0);
                float t = uMotion.x * 0.42;

                float revealedMask = uMotion.y >= 1.0
                    ? 1.0
                    : smoothstep(normY - 0.14, normY + 0.02, uMotion.y * 1.08 - 0.04);
                revealedMask = clamp(revealedMask, 0.0, 1.0);

                vec2 halfRes = res * 0.5;
                vec2 centeredPos = frag - halfRes;
                float shortSide = min(res.x, res.y);
                float margin = shortSide * uProfile0.z;
                vec2 boxHalf = halfRes - vec2(margin);
                float cornerR = shortSide * uProfile0.w;
                float perimAngle = atan(centeredPos.y, centeredPos.x);
                float perimWave = sin(perimAngle * uProfile1.x + t * uProfile1.z) * uProfile1.y +
                    cos(perimAngle * uProfile1.w - t * uProfile2.y) * uProfile2.x;
                float dBox = abs(sdRoundedBox(centeredPos, boxHalf, vec4(cornerR)) + perimWave);

                float effectiveGlow = max(8.0, uProfile2.z);
                float wideHalo = exp(-dBox * (uProfile2.w / (effectiveGlow * uProfile3.x)));
                float mediumBloom = exp(-dBox * (uProfile3.z / (effectiveGlow * uProfile3.w)));
                float sharpCore = 1.0 - smoothstep(uProfile4.y, uProfile4.z, dBox);
                float perimeterAura = (wideHalo * uProfile3.y + mediumBloom * uProfile4.x + sharpCore * uProfile4.w) * revealedMask;

                float filamentAngleT = (perimAngle + PI) / (2.0 * PI);
                float filamentGoldBias = smoothstep(0.18, 0.82, sin(filamentAngleT * PI + 0.7));
                vec3 perimeterColor = mix(uCyanColor, uGoldColor, filamentGoldBias);
                perimeterColor = mix(perimeterColor, uGreenColor, sin(filamentAngleT * 6.28 + t * 0.5) * 0.2 + 0.2);

                // Demo's three-octave domain-warped caustics, bottom-up mask and sunlight spots.
                float bottomGrad = pow(clamp(1.0 - normY * uProfile5.w, 0.0, 1.0), uProfile6.x);
                float causticsMask = mix(uProfile5.z, 1.0, bottomGrad * uProfile5.x) * revealedMask;
                float cVal = causticField(uv * uProfile6.y, t);
                float veins = smoothstep(0.16, 1.10, cVal);
                float haze = smoothstep(0.03, 0.78, cVal) * 0.35;
                float causticGlow = veins * 0.92 + haze;
                float spots = lightSpotsField(uv * 1.25, t * 0.85);
                float waterLight = causticGlow * 0.88 + spots * 0.95;
                float colorFlow = sin(uv.x * 2.2 + uv.y * 1.5 + t * 0.75) * 0.5 + 0.5;
                vec3 waterSpectrum = mix(uCyanColor, uGreenColor, clamp(colorFlow * 0.85 + 0.1, 0.0, 1.0));
                float sunCatch = smoothstep(0.28, 0.88, waterLight * 0.55 + colorFlow * 0.45);
                vec3 bottomCausticColor = mix(waterSpectrum, uGoldColor, clamp(sunCatch * uProfile5.y, 0.0, 1.0));

                float bottomContrib = waterLight * causticsMask * uProfile6.z;
                float perimContrib = perimeterAura * uProfile6.w;
                float totalLight = (bottomContrib + perimContrib) * uProfile0.x;
                float finalAlpha = clamp(totalLight, 0.0, 0.96);
                float totalWeight = max(0.001, bottomContrib + perimContrib);
                vec3 finalRgb = (bottomCausticColor * bottomContrib + perimeterColor * perimContrib) / totalWeight;
                float coreSpark = smoothstep(uProfile7.x, uProfile7.y, totalLight);
                finalRgb = mix(finalRgb, vec3(0.99, 0.99, 0.96), coreSpark * uProfile7.z);
                finalRgb = mix(finalRgb, vec3(0.99, 0.99, 0.96), clamp(uEnergyLevel * 0.08, 0.0, 0.08));
                finalRgb *= (1.0 + coreSpark * uProfile7.w);

                // Demo WebGL canvas is SRC_ALPHA composited, then CSS Overlay blended over the photo.
                float shaderAlpha = clamp(finalAlpha * finalAlpha, 0.0, 1.0) * uPerimeterOpacity;
                vec3 perimeterComposite = mix(base, overlayBlend(base, clamp(finalRgb, 0.0, 1.0)), shaderAlpha);

                // The existing 16 nodes keep the Demo's two expanding bottom branches and screen-style cores.
                vec3 accentPremul = vec3(0.0);
                float accentAlpha = 0.0;
                float edgeAa = max(0.0001, sourceScale * 0.5);
                if (uMotion.y > 0.05) {
                    for (int i = 0; i < 16; i++) {
                        vec2 node = nodePosition(i);
                        if (node.x > -1000.0 && node.y > -1000.0) {
                            float fi = float(i);
                            float nodeSpeed = 0.012 + mod(fi, 3.0) * 0.007;
                            float pPos = mod(fi / 16.0 + uMotion.x * nodeSpeed, 1.0);
                            float pulse = 0.65 + 0.35 * sin(uMotion.x * 3.4 + seededPhase(fi));
                            bool isGold = pPos > 0.45;
                            vec3 glowColor = isGold ? uGoldColor : uCyanColor;
                            vec3 coreColor = isGold ? uSparkleGoldCoreColor : uSparkleCyanCoreColor;
                            float nodeSize = (uProfile8.x + mod(fi, 4.0) * uProfile8.y) * uMotion.w;
                            float haloCoverage = circleCoverage(length(pTop - node), nodeSize * uProfile8.z * pulse, edgeAa);
                            screenOver(accentPremul, accentAlpha, glowColor,
                                uProfile9.x * pulse * uProfile0.x * haloCoverage * uPerimeterOpacity);
                            float coreAlpha = min(1.0, uProfile9.y * pulse * uProfile0.x);
                            float coreCoverage = circleCoverage(length(pTop - node), nodeSize * uProfile8.w * pulse, edgeAa);
                            screenOver(accentPremul, accentAlpha, coreColor, coreAlpha * coreCoverage * uPerimeterOpacity);
                        }
                    }
                }

                // Demo bottom-center white core, symmetric gold water lines and pearl tips.
                float flourishReveal = smoothstep(0.15, 0.30, uMotion.y) * uPerimeterOpacity;
                if (flourishReveal > 0.001) {
                    float cssScale = max(uMotion.w, 0.0001);
                    vec2 localCss = (pTop - uPhotoBounds.xy) / cssScale;
                    float cssShortSide = min(uPhotoBounds.z, uPhotoBounds.w) / cssScale;
                    vec2 center = vec2(uPhotoBounds.z / cssScale * 0.5,
                        uPhotoBounds.w / cssScale - cssShortSide * uProfile0.z);
                    float bPulse = 0.75 + 0.25 * sin(uMotion.x * 2.4);
                    float centerDist = length(localCss - center);
                    float centerAa = edgeAa / cssScale;
                    float centerHalo = circleCoverage(centerDist, 8.0, centerAa);
                    screenOver(accentPremul, accentAlpha, uGoldColor, centerHalo * 0.22 * flourishReveal);
                    float centerCore = circleCoverage(centerDist, 3.0 * bPulse, centerAa);
                    screenOver(accentPremul, accentAlpha, vec3(1.0), centerCore * min(1.0, 0.95 * uProfile0.x) * flourishReveal);

                    float leftDistance = 100000.0;
                    float rightDistance = 100000.0;
                    vec2 leftPrev = center + vec2(-uProfile9.z, 0.0);
                    vec2 rightPrev = center + vec2(6.0, 0.0);
                    for (int j = 1; j <= 12; j++) {
                        float q = float(j) / 12.0;
                        vec2 leftNext = mix(mix(center + vec2(-uProfile9.z, 0.0), center + vec2(-uProfile9.z * 0.5, -2.0 * uProfile9.w), q),
                            mix(center + vec2(-uProfile9.z * 0.5, -2.0 * uProfile9.w), center + vec2(-6.0, 0.0), q), q);
                        vec2 rightNext = mix(mix(center + vec2(6.0, 0.0), center + vec2(uProfile9.z * 0.5, 2.0 * uProfile9.w), q),
                            mix(center + vec2(uProfile9.z * 0.5, 2.0 * uProfile9.w), center + vec2(uProfile9.z, 0.0), q), q);
                        leftDistance = min(leftDistance, segmentDistance(localCss, leftPrev, leftNext));
                        rightDistance = min(rightDistance, segmentDistance(localCss, rightPrev, rightNext));
                        leftPrev = leftNext;
                        rightPrev = rightNext;
                    }
                    float lineAA = max(0.06, centerAa);
                    float wingCoverage = max(1.0 - smoothstep(0.65, 0.65 + lineAA, leftDistance),
                        1.0 - smoothstep(0.65, 0.65 + lineAA, rightDistance));
                    screenOver(accentPremul, accentAlpha, uGoldColor,
                        wingCoverage * min(1.0, 0.65 * uProfile0.x) * flourishReveal);
                    float pearlRadius = 1.4;
                    float leftPearl = circleCoverage(length(localCss - (center + vec2(-uProfile9.z, 0.0))), pearlRadius, centerAa);
                    float rightPearl = circleCoverage(length(localCss - (center + vec2(uProfile9.z, 0.0))), pearlRadius, centerAa);
                    float pearlGlow = max(circleCoverage(length(localCss - (center + vec2(-uProfile9.z, 0.0))), 4.0, centerAa),
                        circleCoverage(length(localCss - (center + vec2(uProfile9.z, 0.0))), 4.0, centerAa));
                    screenOver(accentPremul, accentAlpha, uGoldColor, pearlGlow * 0.18 * flourishReveal);
                    screenOver(accentPremul, accentAlpha, vec3(1.0), max(leftPearl, rightPearl) * flourishReveal);
                }

                vec3 accentColor = accentPremul / max(accentAlpha, 0.0001);
                vec3 outputColor = mix(perimeterComposite, overlayBlend(perimeterComposite, accentColor), accentAlpha);
                // Preserve the APP photo texture contract and prevent CSS Overlay from darkening it.
                outputColor = max(outputColor, base);
                gl_FragColor = vec4(clamp(outputColor, 0.0, 1.0), 1.0);
            }
        """

        private fun createProgram(vertexSource: String, fragmentSource: String, label: String): Int {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource, "$label-vertex")
            if (vertex == 0) return 0
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource, "$label-fragment")
            if (fragment == 0) {
                GLES20.glDeleteShader(vertex)
                return 0
            }
            val result = GLES20.glCreateProgram()
            if (result == 0) {
                GLES20.glDeleteShader(vertex)
                GLES20.glDeleteShader(fragment)
                Log.e(TAG, "$label program allocation failed")
                return 0
            }
            GLES20.glAttachShader(result, vertex)
            GLES20.glAttachShader(result, fragment)
            GLES20.glLinkProgram(result)
            val status = IntArray(1)
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
            val info = GLES20.glGetProgramInfoLog(result).orEmpty()
            if (info.isNotBlank()) Log.i(TAG, "$label link log: $info")
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            if (status[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "$label link failed: $info")
                GLES20.glDeleteProgram(result)
                return 0
            }
            return result
        }

        private fun compileShader(type: Int, source: String, label: String): Int {
            val shader = GLES20.glCreateShader(type)
            if (shader == 0) {
                Log.e(TAG, "$label allocation failed")
                return 0
            }
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            val info = GLES20.glGetShaderInfoLog(shader).orEmpty()
            if (info.isNotBlank()) Log.i(TAG, "$label compile log: $info")
            if (status[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "$label compile failed: $info")
                GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        }

    }
}
