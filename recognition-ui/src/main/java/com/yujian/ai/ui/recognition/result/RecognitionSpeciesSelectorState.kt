package com.yujian.ai.ui.recognition.result

import android.content.Context
import com.yujian.ai.knowledge.FishGuideItem
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

enum class SpeciesSelectorEntryContext { EDIT_CONFIRMED, MEDIUM_OTHER, LOW_MANUAL }

/** Search support from existing Fish Knowledge fields plus the local catalog's registered readings. */
object RecognitionSpeciesSearch {
    private val localPinyin = mapOf(
        "grass_carp" to "cao yu",
        "crucian_carp" to "ji yu",
        "common_carp" to "li yu",
        "bighead_carp" to "yong yu",
        "silver_carp" to "bai lian",
        "largemouth_bass" to "jia zhou lu",
        "snakehead" to "hei yu",
        "yellow_catfish" to "huang gu yu",
        "black_carp" to "qing yu",
    )

    fun pinyin(item: FishGuideItem): String = item.pinyin?.takeIf(String::isNotBlank)
        ?: localPinyin[item.id].orEmpty()

    fun initials(item: FishGuideItem): String = item.pinyinInitials?.takeIf(String::isNotBlank)
        ?: pinyin(item).split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.toString() }
            .joinToString("")

    fun initial(item: FishGuideItem): String = pinyin(item).trim().firstOrNull()
        ?.uppercaseChar()?.toString() ?: "#"

    fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .filter(Char::isLetterOrDigit)

    fun search(species: List<FishGuideItem>, query: String): List<FishGuideItem> {
        val needle = normalize(query)
        if (needle.isBlank()) return emptyList()
        return species.filter { item ->
            sequenceOf(item.nameCn, item.pinyin.orEmpty().ifBlank { pinyin(item) }, initials(item))
                .plus(item.aliases.asSequence())
                .map(::normalize)
                .any { it.contains(needle) }
        }
    }
}

fun updateSpeciesRecents(current: List<String>, committedId: String, limit: Int = 3): List<String> {
    if (committedId.isBlank() || limit <= 0) return current.distinct().take(limit.coerceAtLeast(0))
    return (listOf(committedId) + current).distinct().take(limit)
}

class SpeciesSelectorRecentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): List<String> = runCatching {
        val stored = JSONArray(preferences.getString(KEY_RECENT, "[]"))
        (0 until stored.length()).mapNotNull { stored.optString(it).takeIf(String::isNotBlank) }.distinct().take(3)
    }.getOrDefault(emptyList())

    fun commit(speciesId: String): List<String> {
        val updated = updateSpeciesRecents(read(), speciesId)
        val json = JSONArray().apply { updated.forEach(::put) }.toString()
        preferences.edit().putString(KEY_RECENT, json).apply()
        return updated
    }

    companion object {
        private const val PREFERENCES = "recognition_result_species_selector_v1"
        private const val KEY_RECENT = "recent_species_ids"
    }
}
