package app.aapswear.wear
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
class WearActivity:Activity(){private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);private lateinit var view:TextView;private var latest:TherapyDisplayState?=null;private var connectedNodes=0;override fun onCreate(s:Bundle?){super.onCreate(s);view=TextView(this).apply{gravity=17;setPadding(24,24,24,24)};setContentView(view);scope.launch{TherapyStateStore(this@WearActivity).state.collectLatest{latest=it;render()}};scope.launch{connectedNodes=withContext(Dispatchers.IO){runCatching{requestLatestState(applicationContext)}.getOrDefault(0)};render()}}private fun render(){val state=latest;val g=state?.glucose;val freshness=FreshnessPolicy.classify(g?.measuredAtEpochMs?:state?.receivedAtEpochMs,System.currentTimeMillis());val value=if(g!=null&&(freshness==Freshness.CURRENT||freshness==Freshness.DELAYED))TherapyDisplayFormatter.glucose(g) else "—";val age=TherapyDisplayFormatter.ageMinutes(g?.measuredAtEpochMs,System.currentTimeMillis());val status=when(freshness){Freshness.CURRENT->"aktuell";Freshness.DELAYED->"verzögert";Freshness.STALE->"veraltet";Freshness.NO_DATA->"keine Daten"};view.text="$value\n$status · $age\n${state?.sourceVersion?:"AndroidAPS nicht erkannt"}\nTelefonverbindungen: $connectedNodes\nNur Anzeige · read-only"}override fun onDestroy(){scope.cancel();super.onDestroy()}}
