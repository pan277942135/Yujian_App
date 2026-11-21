package com.yujian.ai.ui.recognition.reveal

import android.util.Log
import android.os.Looper
import com.yujian.ai.ui.waterlight.core.RecognitionSceneAnimationController

/** The production Result event path fans out to the shared controller, then commits in SessionStore. */
object RecognitionResultHandoffConsumer {
    fun consume(
        key: RecognitionRevealKey,
        event: RecognitionResultHandoffEvent,
        store: RecognitionRevealSessionStore,
        controller: RecognitionSceneAnimationController,
    ): RecognitionResultHandoffReceipt {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return reject(store, key, event, "HANDOFF_TRANSACTION_NOT_ON_MAIN_THREAD")
        }
        val storeRejection = store.canAcceptResultHandoffEvent(key, event)
        if (storeRejection != null) {
            return reject(store, key, event, storeRejection)
        }
        val controllerRejection = controller.canAcceptResultHandoffEvent(event)
        if (controllerRejection != null) return reject(store, key, event, controllerRejection)

        // All mutations below are synchronous on Main. Both owners have accepted the
        // same immutable event sequence and identity before either side commits it.
        val controllerReceipt = controller.acceptResultHandoffEvent(event)
        if (!controllerReceipt.accepted) {
            val reason = controllerReceipt.rejectionReason ?: "CONTROLLER_REJECTED"
            return reject(store, key, event, reason)
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

    private fun reject(
        store: RecognitionRevealSessionStore,
        key: RecognitionRevealKey,
        event: RecognitionResultHandoffEvent,
        reason: String,
    ): RecognitionResultHandoffReceipt {
        store.recordRejectedResultHandoffEvent(key, event, reason)
        Log.w(
            TAG,
            "accepted=false substage=${event.substage.name} reason=$reason sequence=${event.eventSequence} " +
                "session=${event.identity.sessionId} attempt=${event.identity.attemptId}",
        )
        return RecognitionResultHandoffReceipt(
            accepted = false,
            rejectionReason = reason,
            resultSceneStable = store.snapshot?.resultHandoffState?.resultSceneStable == true,
        )
    }

    private const val TAG = "RecognitionResultHandoff"
}
