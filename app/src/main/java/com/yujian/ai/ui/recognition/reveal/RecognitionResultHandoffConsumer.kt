package com.yujian.ai.ui.recognition.reveal

import android.util.Log
import com.yujian.ai.ui.waterlight.core.RecognitionSceneAnimationController

/** The production Result event path fans out to the shared controller, then commits in SessionStore. */
object RecognitionResultHandoffConsumer {
    fun consume(
        key: RecognitionRevealKey,
        event: RecognitionResultHandoffEvent,
        store: RecognitionRevealSessionStore,
        controller: RecognitionSceneAnimationController,
    ): RecognitionResultHandoffReceipt {
        val storeRejection = store.canAcceptResultHandoffEvent(key, event)
        if (storeRejection != null) {
            store.recordRejectedResultHandoffEvent(key, event, storeRejection)
            Log.w(
                TAG,
                "accepted=false substage=${event.substage.name} reason=$storeRejection " +
                    "sequence=${event.eventSequence} session=${event.identity.sessionId} attempt=${event.identity.attemptId}",
            )
            return RecognitionResultHandoffReceipt(
                accepted = false,
                rejectionReason = storeRejection,
                resultSceneStable = store.snapshot?.resultHandoffState?.resultSceneStable == true,
            )
        }

        val controllerReceipt = controller.acceptResultHandoffEvent(event)
        if (!controllerReceipt.accepted) {
            val reason = controllerReceipt.rejectionReason ?: "CONTROLLER_REJECTED"
            store.recordRejectedResultHandoffEvent(key, event, reason)
            Log.w(TAG, "accepted=false substage=${event.substage.name} reason=$reason sequence=${event.eventSequence}")
            return controllerReceipt
        }

        val receipt = store.recordResultHandoffEvent(key, event)
        if (!receipt.accepted) {
            val reason = receipt.rejectionReason ?: "SESSION_STORE_REJECTED_AFTER_CONTROLLER_ACCEPT"
            store.recordRejectedResultHandoffEvent(key, event, reason)
            Log.e(TAG, "controller accepted event but SessionStore rejected it substage=${event.substage.name} reason=$reason")
            return receipt
        }
        Log.i(
            TAG,
            "accepted=true substage=${event.substage.name} sequence=${event.eventSequence} " +
                "timestamp=${event.monotonicTimestampMs} session=${event.identity.sessionId} attempt=${event.identity.attemptId}",
        )
        return receipt
    }

    private const val TAG = "RecognitionResultHandoff"
}
