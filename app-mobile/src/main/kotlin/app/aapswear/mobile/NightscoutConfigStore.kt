package app.aapswear.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class NightscoutConfig(
    val baseUrl: String,
    val accessToken: String?,
)

internal object NightscoutConfigStore {
    private const val PREFS = "nightscout_config"
    private const val KEY_URL = "base_url"
    private const val KEY_TOKEN = "access_token_encrypted"
    private const val KEY_PROMPT_SHOWN = "setup_prompt_shown"
    private const val KEY_ALIAS = "sugarlicious_nightscout_token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun load(context: Context): NightscoutConfig? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baseUrl = prefs.getString(KEY_URL, null)?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString(KEY_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
            ?.let(::decrypt)
            ?.takeIf { it.isNotBlank() }
        return NightscoutConfig(baseUrl, token)
    }

    fun save(context: Context, baseUrl: String, accessToken: String?) {
        val normalized = normalizeBaseUrl(baseUrl)
        require(normalized.startsWith("https://")) { "Nightscout muss per HTTPS erreichbar sein." }
        val token = accessToken
            ?.trim()
            ?.removePrefix("token=")
            ?.takeIf { it.isNotBlank() }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, normalized)
            .apply {
                if (token == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, encrypt(token))
            }
            .putBoolean(KEY_PROMPT_SHOWN, true)
            .apply()
    }

    fun isConfigured(context: Context): Boolean = load(context) != null

    fun shouldOfferSetup(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !isConfigured(context) && !prefs.getBoolean(KEY_PROMPT_SHOWN, false)
    }

    fun markSetupPromptShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROMPT_SHOWN, true)
            .apply()
    }

    private fun normalizeBaseUrl(value: String): String =
        value.trim().trimEnd('/')

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        if (payload.size <= IV_LENGTH_BYTES) return@runCatching null
        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private const val IV_LENGTH_BYTES = 12
}
