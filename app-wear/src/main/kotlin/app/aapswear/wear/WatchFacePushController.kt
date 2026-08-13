package app.aapswear.wear

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import java.io.File
import kotlinx.coroutines.CancellationException

internal object SugarliciousWatchFacePush {
    const val ACTIVE_PERMISSION =
        "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"

    private const val PREFS = "watchface_push"
    private const val KEY_ACTIVE_ATTEMPT_USED = "active_attempt_used"

    private data class FaceSpec(
        val apkAsset: String,
        val tokenAsset: String,
    )

    private val faces =
        listOf(
            FaceSpec(
                "watchfaces/sugarlicious_analog.apk",
                "watchfaces/sugarlicious_analog_token.txt",
            ),
            FaceSpec(
                "watchfaces/sugarlicious_orbit.apk",
                "watchfaces/sugarlicious_orbit_token.txt",
            ),
            FaceSpec(
                "watchfaces/sugarlicious_rings.apk",
                "watchfaces/sugarlicious_rings_token.txt",
            ),
            FaceSpec(
                "watchfaces/sugarlicious_graph.apk",
                "watchfaces/sugarlicious_graph_token.txt",
            ),
        )

    fun isSupported(): Boolean =
        runCatching {
            WatchFacePushManagerFactory.isSupported()
        }.getOrDefault(false)

    fun hasActivationPermission(context: Context): Boolean =
        context.checkSelfPermission(ACTIVE_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun apply(
        context: Context,
        index: Int,
    ): String {
        if (!isSupported()) {
            return "Direktwechsel braucht Wear OS 6 oder neuer"
        }

        val spec =
            faces.getOrNull(index)
                ?: return "Unbekanntes Watchface"

        val token =
            runCatching {
                context.assets
                    .open(spec.tokenAsset)
                    .bufferedReader()
                    .use { it.readText().trim() }
            }.getOrElse {
                return "Watchface-Token fehlt"
            }

        if (token.isBlank()) {
            return "Watchface-Token ist leer"
        }

        val apk =
            runCatching {
                copyAssetToCache(
                    context,
                    spec.apkAsset,
                )
            }.getOrElse {
                return "Watchface-APK fehlt"
            }

        return try {
            val manager =
                WatchFacePushManagerFactory
                    .createWatchFacePushManager(context)
            val response = manager.listWatchFaces()
            val installed =
                response.installedWatchFaceDetails
            val hadActive =
                installed.any { details ->
                    runCatching {
                        manager.isWatchFaceActive(
                            details.packageName,
                        )
                    }.getOrDefault(false)
                }

            val details =
                ParcelFileDescriptor.open(
                    apk,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                ).use { pfd ->
                    if (installed.isEmpty()) {
                        manager.addWatchFace(
                            pfd,
                            token,
                        )
                    } else {
                        manager.updateWatchFace(
                            installed.first().slotId,
                            pfd,
                            token,
                        )
                    }
                }

            when {
                hadActive ->
                    "Watchface aktiv"

                !hasActivationPermission(context) ->
                    "Watchface geladen - Direktwechsel auf der Uhr freigeben"

                activationAttemptUsed(context) ->
                    "Watchface geladen - bitte auf der Uhr manuell aktivieren"

                else -> {
                    manager.setWatchFaceAsActive(
                        details.slotId,
                    )
                    markActivationAttemptUsed(context)
                    "Watchface aktiv"
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            "Watchface-Wechsel fehlgeschlagen: ${error.javaClass.simpleName}"
        } finally {
            apk.delete()
        }
    }

    private fun activationAttemptUsed(
        context: Context,
    ): Boolean =
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE,
            )
            .getBoolean(
                KEY_ACTIVE_ATTEMPT_USED,
                false,
            )

    private fun markActivationAttemptUsed(
        context: Context,
    ) {
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE,
            )
            .edit()
            .putBoolean(
                KEY_ACTIVE_ATTEMPT_USED,
                true,
            )
            .apply()
    }

    private fun copyAssetToCache(
        context: Context,
        assetPath: String,
    ): File {
        val target =
            File.createTempFile(
                "sugarlicious-watchface-",
                ".apk",
                context.cacheDir,
            )

        context.assets
            .open(assetPath)
            .use { input ->
                target
                    .outputStream()
                    .use { output ->
                        input.copyTo(output)
                    }
            }

        return target
    }
}
