package app.aapswear.mobile
import app.aapswear.protocol.WearProtocol
import app.aapswear.storage.TherapyStateStore
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
class MobileDataLayerService:WearableListenerService(){private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);override fun onMessageReceived(event:MessageEvent){if(event.path==WearProtocol.REQUEST_PATH)scope.launch{TherapyStateStore(this@MobileDataLayerService).state.first()?.let{publishState(this@MobileDataLayerService,it)}}}override fun onDestroy(){scope.cancel();super.onDestroy()}}
