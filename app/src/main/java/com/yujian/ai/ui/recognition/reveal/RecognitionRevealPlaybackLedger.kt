package com.yujian.ai.ui.recognition.reveal

/** Bounded process-lifetime playback lifecycle. Claiming a key never means playback completed. */
internal class RecognitionRevealPlaybackLedger(private val maxKeys: Int = 64) {
    private data class Entry(
        var status: RevealPlaybackStatus = RevealPlaybackStatus.NOT_STARTED,
        var stage: com.yujian.ai.ui.waterlight.core.RecognitionSceneStage? = null,
        var progress: Float = 0f,
    )

    private val entries = LinkedHashMap<RecognitionRevealKey, Entry>()

    @Synchronized
    fun hasPlayed(key: RecognitionRevealKey): Boolean = status(key) == RevealPlaybackStatus.COMPLETED

    @Synchronized
    fun claim(key: RecognitionRevealKey): Boolean {
        val entry = entry(key)
        if (entry.status !in setOf(RevealPlaybackStatus.NOT_STARTED, RevealPlaybackStatus.INTERRUPTED)) return false
        entry.status = RevealPlaybackStatus.CLAIMED
        trim()
        return true
    }

    @Synchronized
    fun status(key: RecognitionRevealKey): RevealPlaybackStatus = entries[key]?.status ?: RevealPlaybackStatus.NOT_STARTED

    @Synchronized
    fun start(key: RecognitionRevealKey): Boolean {
        val entry = entries[key] ?: return false
        if (entry.status == RevealPlaybackStatus.IN_PROGRESS) return true
        if (entry.status !in setOf(RevealPlaybackStatus.CLAIMED, RevealPlaybackStatus.INTERRUPTED)) return false
        entry.status = RevealPlaybackStatus.IN_PROGRESS
        return true
    }

    @Synchronized
    fun updateProgress(
        key: RecognitionRevealKey,
        stage: com.yujian.ai.ui.waterlight.core.RecognitionSceneStage,
        progress: Float,
    ): Boolean {
        val entry = entries[key] ?: return false
        if (entry.status != RevealPlaybackStatus.IN_PROGRESS || !progress.isFinite() || progress !in 0f..1f) return false
        val previousStage = entry.stage
        if (previousStage != null && stage.ordinal < previousStage.ordinal) return false
        if (previousStage == stage && progress < entry.progress) return false
        entry.stage = stage
        entry.progress = progress
        return true
    }

    @Synchronized
    fun complete(key: RecognitionRevealKey, resultSceneStable: Boolean): Boolean {
        if (!resultSceneStable) return false
        val entry = entries[key] ?: return false
        if (entry.status != RevealPlaybackStatus.IN_PROGRESS) return entry.status == RevealPlaybackStatus.COMPLETED
        entry.status = RevealPlaybackStatus.COMPLETED
        entry.progress = 1f
        return true
    }

    @Synchronized
    fun interrupt(key: RecognitionRevealKey): Boolean {
        val entry = entries[key] ?: return false
        if (entry.status !in setOf(RevealPlaybackStatus.CLAIMED, RevealPlaybackStatus.IN_PROGRESS)) return false
        entry.status = RevealPlaybackStatus.INTERRUPTED
        return true
    }

    @Synchronized
    fun resume(key: RecognitionRevealKey): Boolean {
        val entry = entries[key] ?: return false
        if (entry.status != RevealPlaybackStatus.INTERRUPTED) return false
        entry.status = RevealPlaybackStatus.IN_PROGRESS
        return true
    }

    @Synchronized
    fun fail(key: RecognitionRevealKey): Boolean {
        val entry = entries[key] ?: return false
        if (entry.status == RevealPlaybackStatus.COMPLETED) return false
        entry.status = RevealPlaybackStatus.FAILED
        return true
    }

    @Synchronized
    fun progress(key: RecognitionRevealKey): Pair<com.yujian.ai.ui.waterlight.core.RecognitionSceneStage?, Float> {
        val entry = entries[key] ?: return null to 0f
        return entry.stage to entry.progress
    }

    private fun entry(key: RecognitionRevealKey): Entry = entries.getOrPut(key) { Entry() }

    private fun trim() {
        while (entries.size > maxKeys) entries.remove(entries.keys.first())
    }
}
