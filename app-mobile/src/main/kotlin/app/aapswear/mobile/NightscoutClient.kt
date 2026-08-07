package app.aapswear.mobile

import app.aapswear.model.GlucoseSample
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.Instant

internal data class NightscoutEntry(
    val sample: GlucoseSample,
    val direction: String?,
)

internal class NightscoutClient {
    fun fetchLast24Hours(
        config: NightscoutConfig,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<NightscoutEntry> {
        val tokenQuery = config.accessToken?.let {
            "&token=${URLEncoder.encode(it, Charsets.UTF_8.name())}"
        }.orEmpty()
        val endpoint = "${config.baseUrl}/api/v1/entries/sgv.json?count=$REQUEST_COUNT$tokenQuery"
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Sugarlicious/0.5.1")
            connection.instanceFollowRedirects = true

            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Nightscout HTTP $status")
            }
            parseEntries(connection.inputStream.bufferedReader().use { it.readText() }, nowEpochMs)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseEntries(json: String, nowEpochMs: Long): List<NightscoutEntry> {
        val earliest = nowEpochMs - DisplayHistoryAccumulator.WINDOW_MS
        val latest = nowEpochMs + 5 * 60_000L
        val array = JSONArray(json)
        val entries = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val value = item.optDouble("sgv", Double.NaN)
                if (!value.isFinite() || value !in 20.0..1000.0) continue
                val timestamp = when {
                    item.has("date") -> item.optLong("date", 0L)
                    else -> runCatching { Instant.parse(item.optString("dateString")).toEpochMilli() }.getOrDefault(0L)
                }
                if (timestamp !in earliest..latest) continue
                add(
                    NightscoutEntry(
                        sample = GlucoseSample(value, timestamp),
                        direction = item.optString("direction").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }

        return entries
            .sortedBy { it.sample.measuredAtEpochMs }
            .associateBy { it.sample.measuredAtEpochMs }
            .values
            .toList()
            .takeLast(DisplayHistoryAccumulator.MAX_POINTS)
    }

    private companion object {
        const val REQUEST_COUNT = 300
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 12_000
    }
}
