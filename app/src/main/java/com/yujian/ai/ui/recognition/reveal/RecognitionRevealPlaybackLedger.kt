package com.yujian.ai.ui.recognition.reveal

/** Bounded process-lifetime guard against replay on recomposition or screen recreation. */
internal class RecognitionRevealPlaybackLedger(private val maxKeys: Int = 64) {
    private val played = LinkedHashSet<RecognitionRevealKey>()

    @Synchronized
    fun hasPlayed(key: RecognitionRevealKey): Boolean = key in played

    @Synchronized
    fun claim(key: RecognitionRevealKey): Boolean {
        if (!played.add(key)) return false
        while (played.size > maxKeys) played.remove(played.first())
        return true
    }
}
