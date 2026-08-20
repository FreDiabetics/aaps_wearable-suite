package app.aapswear.datasource.aaps

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Extracts the effective APS target from the public suggested/enacted payload. */
object AapsTargetParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String?): Double? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            json.parseToJsonElement(payload)
                .jsonObject["targetBG"]
                ?.jsonPrimitive
                ?.doubleOrNull
                ?.takeIf { it.isFinite() && it in 20.0..1_000.0 }
        }.getOrNull()
    }
}
