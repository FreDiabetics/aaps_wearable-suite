package app.aapswear.wear
import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aapswear.complications.AllProviders
import app.aapswear.protocol.WearProtocol
import app.aapswear.storage.TherapyStateStore
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
class StateDataLayerService:WearableListenerService(){ private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);override fun onCreate(){super.onCreate();scope.launch{runCatching{requestLatestState(this@StateDataLayerService)}}} override fun onDataChanged(events:DataEventBuffer){ events.filter{it.type==DataEvent.TYPE_CHANGED&&it.dataItem.uri.path==WearProtocol.STATE_PATH}.forEach{ event->runCatching{WearProtocol.decode(event.dataItem.data?:return@forEach)}.getOrNull()?.let{incoming->scope.launch{val store=TherapyStateStore(this@StateDataLayerService);val old=store.state.first();val sample=incoming.glucose?.let{app.aapswear.model.GlucoseSample(it.valueMgDl,it.measuredAtEpochMs)};val history=(old?.glucoseHistory.orEmpty()+listOfNotNull(sample)).distinctBy{it.measuredAtEpochMs}.filter{System.currentTimeMillis()-it.measuredAtEpochMs<=6*60*60_000L}.takeLast(144);store.save(incoming.copy(glucoseHistory=history));AllProviders.classes.forEach{provider->ComplicationDataSourceUpdateRequester.create(this@StateDataLayerService,ComponentName(this@StateDataLayerService,provider)).requestUpdateAll()}}}} }; override fun onDestroy(){scope.cancel();super.onDestroy()} }

suspend fun requestLatestState(context:Context):Int { val nodes=Wearable.getNodeClient(context).connectedNodes.await();nodes.forEach{node->runCatching{Wearable.getMessageClient(context).sendMessage(node.id,WearProtocol.REQUEST_PATH,byteArrayOf()).await()}};return nodes.size }
