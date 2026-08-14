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

    private data class FaceSpec(
        val packageName: String,
        val apkAsset: String,
        val tokenAsset: String,
    )

    private val faces =
        listOf(
            FaceSpec(
                "app.aapswear.watchfacepush.analog",
                "watchfaces/sugarlicious_analog.apk",
                "watchfaces/sugarlicious_analog_token.txt",
            ),
            FaceSpec(
                "app.aapswear.watchfacepush.orbit",
                "watchfaces/sugarlicious_orbit.apk",
                "watchfaces/sugarlicious_orbit_token.txt",
            ),
            FaceSpec(
                "app.aapswear.watchfacepush.rings",
                "watchfaces/sugarlicious_rings.apk",
                "watchfaces/sugarlicious_rings_token.txt",
            ),
            FaceSpec(
                "app.aapswear.watchfacepush.graph",
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

    suspend fun activeFaceIndex(context: Context): Int? {
        if (!isSupported()) return null
        return runCatching {
            val manager =
                WatchFacePushManagerFactory
                    .createWatchFacePushManager(context)
            val installed =
                manager.listWatchFaces()
                    .installedWatchFaceDetails
            faces.indexOfFirst { face ->
                installed.any { details ->
                    details.packageName == face.packageName &&
                        manager.isWatchFaceActive(details.packageName)
                }
            }.takeIf { it >= 0 }
        }.getOrNull()
    }

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
            val installed =
                manager.listWatchFaces()
                    .installedWatchFaceDetails
            val matching =
                installed.firstOrNull {
                    it.packageName == spec.packageName
                }

            val details =
                ParcelFileDescriptor.open(
                    apk,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                ).use { pfd ->
                    if (matching == null) {
                        manager.addWatchFace(
                            pfd,
                            token,
                        )
                    } else {
                        manager.updateWatchFace(
                            matching.slotId,
                            pfd,
                            token,
                        )
                    }
                }

            if (
                runCatching {
                    manager.isWatchFaceActive(spec.packageName)
                }.getOrDefault(false)
            ) {
                "Watchface aktiv"
            } else if (!hasActivationPermission(context)) {
                "Watchface geladen - Direktwechsel auf der Uhr freigeben"
            } else {
                manager.setWatchFaceAsActive(details.slotId)
                if (
                    runCatching {
                        manager.isWatchFaceActive(spec.packageName)
                    }.getOrDefault(false)
                ) {
                    "Watchface aktiv"
                } else {
                    "Watchface geladen - Aktivierung wurde nicht bestätigt"
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
