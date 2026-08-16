package app.aapswear.g7watch

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import app.aapswear.storage.DiagnosticEventStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class G7ReadingProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        if (uri.lastPathSegment == "diagnostics") {
            val columns = arrayOf("id", "occurred_at", "origin", "module", "code", "severity", "message", "metadata")
            val cursor = MatrixCursor(columns)
            val events = runBlocking(Dispatchers.IO) { DiagnosticEventStore(requireNotNull(context)).snapshot() }
            events.forEach { event ->
                cursor.addRow(
                    arrayOf<Any?>(
                        event.id,
                        event.occurredAtEpochMs,
                        event.origin,
                        event.module,
                        event.code,
                        event.severity.name,
                        event.message,
                        event.metadata.entries.joinToString("; ") { "${it.key}=${it.value}" },
                    ),
                )
            }
            return cursor
        }
        val columns = arrayOf("id", "glucose", "measured_at", "received_at", "delta", "trend", "trend_rate", "status")
        val cursor = MatrixCursor(columns)
        val limit = if (uri.lastPathSegment == "latest") 1 else 300
        G7ReadingDatabase(requireNotNull(context)).query(limit = limit).forEach { reading ->
            cursor.addRow(arrayOf<Any?>(reading.id, reading.glucoseMgDl, reading.timestampEpochMs, reading.receivedAtEpochMs, reading.deltaMgDl, reading.trend.name, reading.trendRateMgDlPerMinute, reading.status.name))
        }
        cursor.setNotificationUri(requireNotNull(context).contentResolver, CONTENT_URI)
        return cursor
    }
    override fun getType(uri: Uri): String = when (uri.lastPathSegment) {
        "latest" -> "vnd.android.cursor.item/vnd.sugarlicious.g7"
        "diagnostics" -> "vnd.android.cursor.dir/vnd.sugarlicious.diagnostics"
        else -> "vnd.android.cursor.dir/vnd.sugarlicious.g7"
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only provider")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only provider")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only provider")

    companion object {
        val CONTENT_URI: Uri = Uri.parse("content://app.aapswear.g7watch.readings/readings")
        val DIAGNOSTICS_URI: Uri = Uri.parse("content://app.aapswear.g7watch.readings/diagnostics")
    }
}
