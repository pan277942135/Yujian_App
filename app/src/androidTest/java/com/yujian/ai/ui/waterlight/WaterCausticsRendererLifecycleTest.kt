package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.renderer.WaterCausticsField
import com.yujian.ai.ui.waterlight.renderer.WaterCausticsRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WaterCausticsRendererLifecycleTest {
    private val color = WaterCausticsField.Rgb(.3, .7, .8)
    private fun frame(photo: String, version: Long) = WaterLightFrame(
        sessionId = "session", photoSha256 = photo, elapsedMs = 1000,
        opticalReferenceElapsedMs = 1000, phase = WaterLightPhase.PROCESSING,
        completion = .5f, reducedMotion = false, geometryVersion = version,
        attemptId = "attempt", imageId = photo,
    )

    @Test fun resizeAndPhotoSwitchCannotPublishOldBuffer() {
        val renderer = WaterCausticsRenderer(CoroutineScope(SupervisorJob() + Dispatchers.Default), 4096)
        val ready = CountDownLatch(1)
        val staleCallbacks = AtomicInteger()
        try {
            renderer.request(frame("old", 1), 200, 80, 1.0, 1.0, color, color, .2) { staleCallbacks.incrementAndGet() }
            renderer.request(frame("old", 2), 80, 200, 1.0, 1.0, color, color, .2) { staleCallbacks.incrementAndGet() }
            renderer.request(frame("new", 3), 120, 120, 1.0, 1.0, color, color, .2) { ready.countDown() }
            assertTrue("latest caustics frame did not arrive", ready.await(10, TimeUnit.SECONDS))
            renderer.withFrontBitmap { bitmap -> assertEquals(bitmap.width, bitmap.height) }
            assertEquals(0, staleCallbacks.get())
        } finally { renderer.close() }
    }

    @Test fun closeRejectsFurtherRequestsAndClearsFront() {
        val renderer = WaterCausticsRenderer(CoroutineScope(SupervisorJob() + Dispatchers.Default), 4096)
        renderer.close()
        renderer.request(frame("new", 1), 120, 120, 1.0, 1.0, color, color, .2) {
            throw AssertionError("closed renderer published")
        }
        var published = false
        renderer.withFrontBitmap { published = true }
        assertEquals(false, published)
    }
}
