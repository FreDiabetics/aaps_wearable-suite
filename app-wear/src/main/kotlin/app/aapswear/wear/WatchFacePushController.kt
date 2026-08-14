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

    private const val PREFS = "sugarlicious_watchface_push"
    private const val LAST_APPLIED_FACE = "last_applied_face"
    private const val LAST_APPLIED_AT = "last_applied_at"
    private const val SETTLING_WINDOW_MS = 15_000L

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

        val detected =
            runCatching {
                val manager =
                    WatchFacePushManagerFactory
                        .createWatchFacePushManager(context)
                val installed =
                    manager.listWatchFaces()
                        .installedWatchFaceDetails

                faces.indexOfFirst { face ->
                    installed.any { details ->
                        details.packageName == face.packageName &&
                            runCatching {
                                manager.isWatchFaceActive(details.packageName)
                            }.getOrDefault(false)
                    }
                }.takeIf { it >= 0 }
            }.getOrNull()

        if (detected != null) return detected

        // Watch Face Push swaps settle asynchronously. During that short interval the platform can
        // report neither the old nor the new package as active. Preserve the face we just applied
        // only for that settling window; afterwards platform state is authoritative again.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val appliedAt = prefs.getLong(LAST_APPLIED_AT, 0L)
        if (System.currentTimeMillis() - appliedAt !in 0..SETTLING_WINDOW_MS) return null
        return prefs
            .getInt(LAST_APPLIED_FACE, -1)
            .takeIf { it in faces.indices }
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

            val managed =
                installed.filter { details ->
                    faces.any { face -> face.packageName == details.packageName }
                }

            val matching =
                managed.firstOrNull { details ->
                    details.packageName == spec.packageName
                }

            val activeManaged =
                managed.firstOrNull { details ->
                    runCatching {
                        manager.isWatchFaceActive(details.packageName)
                    }.getOrDefault(false)
                }

            // Reuse an existing Sugarlicious slot whenever possible. The Push API has a finite
            // slot limit; creating one slot per Sugarlicious variant eventually causes
            // AddWatchFaceException. If the requested package already has a slot, update it. If it
            // does not, replace the currently active Sugarlicious slot (or another managed slot)
            // instead of allocating a fifth/new slot.
            val target = matching ?: activeManaged ?: managed.firstOrNull()
            val targetWasActive =
                target != null &&
                    activeManaged?.slotId == target.slotId

            val details =
                ParcelFileDescriptor.open(
                    apk,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                ).use { pfd ->
                    if (target == null) {
                        manager.addWatchFace(
                            pfd,
                            token,
                        )
                    } else {
                        manager.updateWatchFace(
                            target.slotId,
                            pfd,
                            token,
                        )
                    }
                }

            when {
                // An update of the active slot stays active after the asynchronous package swap.
                // Do not call isWatchFaceActive()/setWatchFaceAsActive immediately afterwards;
                // Android's Watch Face Push contract explicitly allows the swap to settle later.
                targetWasActive -> {
                    rememberApplied(context, index)
                    "Watchface aktiv"
                }

                !hasActivationPermission(context) ->
                    "Watchface geladen - Direktwechsel auf der Uhr freigeben"

                else -> {
                    manager.setWatchFaceAsActive(details.slotId)
                    rememberApplied(context, index)
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

    private fun rememberApplied(
        context: Context,
        index: Int,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(LAST_APPLIED_FACE, index)
            .putLong(LAST_APPLIED_AT, System.currentTimeMillis())
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
