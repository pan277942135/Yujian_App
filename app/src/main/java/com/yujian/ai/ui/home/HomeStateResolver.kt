package com.yujian.ai.ui.home

import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.presentation.PresentationSanitizer

enum class HomeState { EMPTY, NORMAL }

/** Records with no stable identity or fish identity are not valid Home catches. */
fun isValidHomeRecord(record: RemoteCatch): Boolean =
    record.id.isNotBlank() && (record.speciesId.isNotBlank() || record.speciesName.isNotBlank())

fun validHomeRecords(records: List<RemoteCatch>): List<RemoteCatch> = records.filter(::isValidHomeRecord)

fun orderedHomeRecords(records: List<RemoteCatch>): List<RemoteCatch> =
    validHomeRecords(records).sortedByDescending {
        PresentationSanitizer.resolveTimestamp(it.capturedAt, it.createdAt).millis ?: Long.MIN_VALUE
    }

internal fun normalHomeCatchMotionActive(running: Boolean, reduceMotion: Boolean): Boolean =
    running && !reduceMotion

/** A null result means the archive has not resolved yet; it must never be treated as Empty. */
fun resolveHomeState(fishRecords: List<RemoteCatch>, resolved: Boolean = true): HomeState? {
    if (!resolved) return null
    return if (validHomeRecords(fishRecords).isEmpty()) HomeState.EMPTY else HomeState.NORMAL
}
