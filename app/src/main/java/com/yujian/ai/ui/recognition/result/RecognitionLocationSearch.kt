package com.yujian.ai.ui.recognition.result

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class RecognitionPlace(val name: String, val secondaryAddress: String = "")

fun updateRecognitionPlaceRecents(
    current: List<RecognitionPlace>,
    committed: RecognitionPlace,
    limit: Int = 3,
): List<RecognitionPlace> {
    if (limit <= 0 || committed.name.isBlank()) return current.take(limit.coerceAtLeast(0))
    val cleanCommit = committed.copy(name = committed.name.trim())
    val key = normalizePlaceName(cleanCommit.name)
    return (listOf(cleanCommit) + current.filter { normalizePlaceName(it.name) != key })
        .take(limit)
}

private fun normalizePlaceName(value: String): String = Normalizer.normalize(
    value.trim().lowercase(Locale.ROOT),
    Normalizer.Form.NFD,
).replace(Regex("\\p{M}+"), "").filter(Char::isLetterOrDigit)

class RecognitionPlaceRecentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): List<RecognitionPlace> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECENT, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            item.optString("name").takeIf(String::isNotBlank)?.let {
                RecognitionPlace(it, item.optString("address"))
            }
        }.distinctBy { normalizePlaceName(it.name) }.take(MAX_RECENTS)
    }.getOrDefault(emptyList())

    fun commit(place: RecognitionPlace): List<RecognitionPlace> {
        val updated = updateRecognitionPlaceRecents(read(), place, MAX_RECENTS)
        val array = JSONArray().apply {
            updated.forEach { put(JSONObject().put("name", it.name).put("address", it.secondaryAddress)) }
        }
        preferences.edit().putString(KEY_RECENT, array.toString()).apply()
        return updated
    }

    fun clear() {
        preferences.edit().remove(KEY_RECENT).apply()
    }

    companion object {
        private const val PREFERENCES = "recognition_result_location_v1"
        private const val KEY_RECENT = "recent_places"
        private const val MAX_RECENTS = 3
    }
}

/** Returns null when the platform geocoder is unavailable or reports an error. */
suspend fun searchRecognitionPlaces(context: Context, query: String): List<RecognitionPlace>? = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    if (!Geocoder.isPresent()) return@withContext null
    runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context.applicationContext, Locale.getDefault()).getFromLocationName(query, 8)
            ?.mapNotNull { it.toRecognitionPlace() }
            ?.distinctBy { normalizePlaceName(it.name) }
            .orEmpty()
    }.getOrNull()
}

fun Address.toRecognitionPlace(): RecognitionPlace? {
    val name = featureName?.takeIf(String::isNotBlank)
        ?: subLocality?.takeIf(String::isNotBlank)
        ?: locality?.takeIf(String::isNotBlank)
        ?: subAdminArea?.takeIf(String::isNotBlank)
        ?: adminArea?.takeIf(String::isNotBlank)
        ?: return null
    val secondary = listOfNotNull(subLocality, locality, subAdminArea, adminArea, countryName)
        .map(String::trim).filter(String::isNotBlank).distinct()
        .filterNot { normalizePlaceName(it) == normalizePlaceName(name) }
        .joinToString(" · ")
    return RecognitionPlace(name, secondary)
}

/** Uses a fresh provider fix after explicit user action, falling back to a recent cached fix. */
suspend fun resolveCurrentRecognitionPlace(context: Context): RecognitionPlace? = withContext(Dispatchers.IO) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
    try {
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
        val cached = providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        var selectedLocation = cached?.takeIf { System.currentTimeMillis() - it.time <= 120_000L }
        if (selectedLocation == null) {
            for (provider in providers) {
                selectedLocation = requestCurrentLocation(manager, provider, context)
                if (selectedLocation != null) break
            }
        }
        val fix = selectedLocation ?: return@withContext null
        if (!Geocoder.isPresent()) return@withContext null
        @Suppress("DEPRECATION")
        Geocoder(context.applicationContext, Locale.getDefault())
            .getFromLocation(fix.latitude, fix.longitude, 1)
            ?.firstOrNull()?.toRecognitionPlace()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

private suspend fun requestCurrentLocation(manager: LocationManager, provider: String, context: Context): Location? =
    withTimeoutOrNull(8_000L) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            }
        } else {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                        runCatching { manager.removeUpdates(this) }
                    }
                    @Suppress("DEPRECATION")
                    override fun onStatusChanged(providerName: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(providerName: String) = Unit
                    override fun onProviderDisabled(providerName: String) = Unit
                }
                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
    }
