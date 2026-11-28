package com.yujian.ai.ui.recognition

import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbMotionFrame
import com.yujian.ai.ui.designsystem.components.aiorb.aiOrbMotionFrame

/** Compatibility API; the shared AI Orb Core owns the single motion-frame calculation. */
typealias ProcessingOrbMotionFrame = AIOrbMotionFrame

fun processingOrbMotionFrame(
    entranceMs: Long,
    fromCamera: Boolean,
    reduceMotion: Boolean,
    completion: Float,
): ProcessingOrbMotionFrame = aiOrbMotionFrame(entranceMs, fromCamera, reduceMotion, completion)
