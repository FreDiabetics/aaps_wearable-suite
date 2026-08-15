package app.aapswear.wear

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import java.io.File
import kotlinx.coroutines.CancellationException

internal data class ManagedWatchFaceSlot(
    val slotId: String,
    val packageName: String,
    val isActive: Boolean,
)

internal fun selectManagedWatchFaceSlot(
    slots: List<ManagedWatchFaceSlot>,
    requestedPackageName: String,
): ManagedWatchFaceSlot? =
    slots.firstOrNull { it.isActive }
        ?: slots.firstOrNull { it.packageName == requestedPackageName }
        ?: slots.firstOrNull()

internal object SugarliciousWatchFacePush {
    const val ACTIVE_PERMISSION =
        "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"

    private const val PREFS = "sugarlicious_watchface_push"
    private const val LAST_APPLIED_FACE = "last_applied_face"
    private const val LAST_APPLIED_AT = "last_applied_at"
    private const val DIRECT_ACTIVATION_ATTEMPTED = "direct_activation_attempted"
    private const val SETTLING_WINDOW_MS = 15_000L
    private const val MANUAL_ACTIVATION_MESSAGE =
        "Watchface geladen - auf der Uhr lange drücken und auswählen"
    private const val TAG = "WatchFacePush"

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
            FaceSpec(
                "app.aapswear.watchfacepush.digital",
                "watchfaces/sugarlicious_digital.apk",
                "watchfaces/sugarlicious_digital_token.txt",
            ),
            FaceSpec("app.aapswear.watchfacepush.aapsbigchart", "watchfaces/aaps_big_chart.apk", "watchfaces/aaps_big_chart_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aapscircle", "watchfaces/aaps_circle.apk", "watchfaces/aaps_circle_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aapscockpit", "watchfaces/aaps_cockpit.apk", "watchfaces/aaps_cockpit_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aapscommunity", "watchfaces/aaps_community.apk", "watchfaces/aaps_community_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aapsdigitalstyle", "watchfaces/aaps_digital_style.apk", "watchfaces/aaps_digital_style_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aapslarge", "watchfaces/aaps_large.apk", "watchfaces/aaps_large_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aapsnochart", "watchfaces/aaps_no_chart.apk", "watchfaces/aaps_no_chart_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aapsstandard", "watchfaces/aaps_standard.apk", "watchfaces/aaps_standard_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aapsv2", "watchfaces/aaps_v2.apk", "watchfaces/aaps_v2_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aapsv2ttdark", "watchfaces/aaps_v2_tt_dark.apk", "watchfaces/aaps_v2_tt_dark_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aapsv4", "watchfaces/aaps_v4.apk", "watchfaces/aaps_v4_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.aimico", "watchfaces/aimico.apk", "watchfaces/aimico_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.analoggwatch", "watchfaces/analog_g_watch.apk", "watchfaces/analog_g_watch_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.bluering", "watchfaces/blue_ring.apk", "watchfaces/blue_ring_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.digitalbiggraph", "watchfaces/digital_big_graph.apk", "watchfaces/digital_big_graph_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.digitalgwatch", "watchfaces/digital_g_watch.apk", "watchfaces/digital_g_watch_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.gears", "watchfaces/gears.apk", "watchfaces/gears_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.gota", "watchfaces/gota.apk", "watchfaces/gota_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.luckyloopkoeln", "watchfaces/lucky_loop_koeln.apk", "watchfaces/lucky_loop_koeln_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.pzero", "watchfaces/p_zero.apk", "watchfaces/p_zero_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.robby", "watchfaces/robby.apk", "watchfaces/robby_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.simpledigital", "watchfaces/simple_digital.apk", "watchfaces/simple_digital_token.txt"),
            FaceSpec("app.aapswear.watchfacepush.steampunk", "watchfaces/steam_punk.apk", "watchfaces/steam_punk_token.txt"),
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

                faces.take(5).indexOfFirst { face ->
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

            val managedSlots =
                managed.map { details ->
                    ManagedWatchFaceSlot(
                        slotId = details.slotId,
                        packageName = details.packageName,
                        isActive =
                            runCatching {
                                manager.isWatchFaceActive(details.packageName)
                            }.getOrDefault(false),
                    )
                }

            val selectedSlot =
                selectManagedWatchFaceSlot(
                    slots = managedSlots,
                    requestedPackageName = spec.packageName,
                )
            val target =
                selectedSlot?.let { slot ->
                    managed.firstOrNull { details -> details.slotId == slot.slotId }
                }

            // Wear OS 6 provides one Push slot per marketplace. If our marketplace currently owns
            // the active watch face, that active slot must be updated to switch faces. Calling
            // setWatchFaceAsActive for every variant is both unnecessary and rejected after its
            // one permitted use.
            val targetWasActive = selectedSlot?.isActive == true

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

                directActivationWasAttempted(context) -> MANUAL_ACTIVATION_MESSAGE

                else -> {
                    markDirectActivationAttempted(context)
                    try {
                        manager.setWatchFaceAsActive(details.slotId)
                        rememberApplied(context, index)
                        "Watchface aktiv"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: WatchFacePushManager.SetWatchFaceAsActiveException) {
                        Log.w(
                            TAG,
                            "Direct activation unavailable (code=${error.errorCode}); manual selection required",
                        )
                        MANUAL_ACTIVATION_MESSAGE
                    }
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

    internal fun directActivationWasAttempted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(DIRECT_ACTIVATION_ATTEMPTED, false) ||
            // Upgrade existing installs conservatively: a recorded successful face application
            // means the one-shot activation path has already been used in an earlier version.
            prefs.getLong(LAST_APPLIED_AT, 0L) > 0L
    }

    private fun markDirectActivationAttempted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DIRECT_ACTIVATION_ATTEMPTED, true)
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
