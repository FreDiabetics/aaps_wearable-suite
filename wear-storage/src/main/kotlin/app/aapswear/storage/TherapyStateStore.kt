package app.aapswear.storage
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.aapswear.model.TherapyDisplayState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
private val Context.therapyDataStore by preferencesDataStore("therapy_display_state")
class TherapyStateStore(private val context:Context) {
 private val key=stringPreferencesKey("state_v1"); private val json=Json{ignoreUnknownKeys=true;explicitNulls=false}
 val state:Flow<TherapyDisplayState?> = context.therapyDataStore.data.map { it[key]?.let { raw->runCatching{json.decodeFromString<TherapyDisplayState>(raw)}.getOrNull() } }
 suspend fun save(value:TherapyDisplayState){ context.therapyDataStore.edit{it[key]=json.encodeToString(value)} }
}
