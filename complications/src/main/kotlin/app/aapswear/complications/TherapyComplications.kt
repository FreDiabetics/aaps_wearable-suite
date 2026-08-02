package app.aapswear.complications

import android.app.PendingIntent
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.aapswear.model.*
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.flow.first

enum class ProviderKind { GLUCOSE, GLUCOSE_TREND, GLUCOSE_DELTA, GLUCOSE_TREND_DELTA, GLUCOSE_AGE, GLUCOSE_IMAGE, GLUCOSE_RANGE, GLUCOSE_RANGED, GRAPH, GRAPH_LARGE, IOB, BOLUS_IOB, BASAL_IOB, COB, IOB_COB, BASAL, TEMP_BASAL, TEMP_TARGET, LOOP, LOOP_LAST, PROFILE, RESERVOIR, PUMP_BATTERY, PHONE_BATTERY, SOURCE, AAPS_STATUS, LONG_STATUS }

abstract class TherapyComplicationService(private val kind:ProviderKind):SuspendingComplicationDataSourceService(){
 override fun getPreviewData(type:ComplicationType)=build(type,preview())
 override suspend fun onComplicationRequest(request:ComplicationRequest):ComplicationData = build(request.complicationType,TherapyStateStore(this).state.first())
 private fun build(type:ComplicationType,state:TherapyDisplayState?):ComplicationData {
  val now=System.currentTimeMillis(); val g=state?.glucose; val fresh=FreshnessPolicy.classify(g?.measuredAtEpochMs?:state?.receivedAtEpochMs,now); val stale=fresh==Freshness.STALE||fresh==Freshness.NO_DATA;val s=state.takeUnless{stale}
  val display=if(stale) "—" else glucose(g!!); val trend=if(stale) "" else arrow(g!!.trend); val delta=if(stale) "" else signed(g!!.deltaMgDl,g.displayUnit); val age=g?.let{"${((now-it.measuredAtEpochMs).coerceAtLeast(0)/60_000)}m"}?:"—"
  val pair:Pair<String,String> = when(kind){
   ProviderKind.GLUCOSE->display to "Glucose"
   ProviderKind.GLUCOSE_TREND->"$display$trend" to "Glucose trend"
   ProviderKind.GLUCOSE_DELTA->display to (delta.ifBlank{"—"})
   ProviderKind.GLUCOSE_TREND_DELTA->"$display$trend" to (delta.ifBlank{"—"})
   ProviderKind.GLUCOSE_AGE->display to age
   ProviderKind.GLUCOSE_RANGE->display to range(g,s?.target,stale)
   ProviderKind.IOB->units(s?.insulin?.totalIob,"U",2) to "IOB"
   ProviderKind.BOLUS_IOB->units(s?.insulin?.bolusIob,"U",2) to "Bolus IOB"
   ProviderKind.BASAL_IOB->units(s?.insulin?.basalIob,"U",2) to "Basal IOB"
   ProviderKind.COB->units(s?.carbs?.cobGrams,"g",0) to "COB"
   ProviderKind.IOB_COB->"${units(s?.insulin?.totalIob,"U",1)} ${units(s?.carbs?.cobGrams,"g",0)}" to "IOB COB"
   ProviderKind.BASAL->units(s?.basal?.currentUnitsPerHour,"U/h",2) to "Basal"
   ProviderKind.TEMP_BASAL->(s?.basal?.displayText?:s?.basal?.tempPercent?.let{"$it%"}?:units(s?.basal?.tempAbsoluteUnitsPerHour,"U/h",2)) to "Temp basal"
   ProviderKind.TEMP_TARGET->target(s?.target,g?.displayUnit?:GlucoseUnit.MG_DL) to "Target"
   ProviderKind.LOOP->(s?.loop?.status?:"—") to "Loop"
   ProviderKind.LOOP_LAST->timeAgo(s?.loop?.lastRunAtEpochMs,now) to "Last loop"
   ProviderKind.PROFILE->(s?.profile?.name?:"—") to "Profile"
   ProviderKind.RESERVOIR->units(s?.pump?.reservoirUnits,"U",0) to "Reservoir"
   ProviderKind.PUMP_BATTERY->percent(s?.pump?.batteryPercent) to "Pump battery"
   ProviderKind.PHONE_BATTERY->percent(s?.device?.phoneBatteryPercent) to "Phone battery"
   ProviderKind.SOURCE->(state?.sourceVersion?:"No data") to fresh.name.lowercase()
   ProviderKind.AAPS_STATUS->"$display$trend ${units(s?.insulin?.totalIob,"U",1)}" to "${units(s?.carbs?.cobGrams,"g",0)} ${s?.loop?.status?:"—"}"
   ProviderKind.LONG_STATUS->"Glucose $display$trend, delta ${delta.ifBlank{"—"}}, age $age, IOB ${units(s?.insulin?.totalIob,"U",2)}, COB ${units(s?.carbs?.cobGrams,"g",0)}, basal ${units(s?.basal?.currentUnitsPerHour,"U/h",2)}, loop ${s?.loop?.status?:"—"}, pump ${s?.pump?.status?:"—"}" to "AAPS status"
   ProviderKind.GLUCOSE_IMAGE,ProviderKind.GRAPH,ProviderKind.GRAPH_LARGE,ProviderKind.GLUCOSE_RANGED->display to "Glucose"
  }
  val description=PlainComplicationText.Builder(pair.second).build(); val tap=PendingIntent.getActivity(this,kind.ordinal,packageManager.getLaunchIntentForPackage(packageName)?:Intent(),PendingIntent.FLAG_IMMUTABLE)
  if(kind==ProviderKind.GLUCOSE_IMAGE||kind==ProviderKind.GRAPH||kind==ProviderKind.GRAPH_LARGE){ val icon=Icon.createWithBitmap(render(s,kind==ProviderKind.GLUCOSE_IMAGE)); return if(type==ComplicationType.PHOTO_IMAGE) PhotoImageComplicationData.Builder(icon,description).setTapAction(tap).build() else SmallImageComplicationData.Builder(SmallImage.Builder(icon,SmallImageType.PHOTO).build(),description).setTapAction(tap).build() }
  if(type==ComplicationType.RANGED_VALUE){
   val ranged=when(kind){
    ProviderKind.GLUCOSE_RANGED->{val low=state?.target?.lowMgDl?.toFloat()?:70f;val high=(state?.target?.highMgDl?.toFloat()?:180f).coerceAtLeast(low+1f);Triple(if(stale)low else g!!.valueMgDl.toFloat(),low,high)}
    ProviderKind.RESERVOIR->Triple(s?.pump?.reservoirUnits?.toFloat()?.coerceIn(0f,300f)?:0f,0f,300f)
    ProviderKind.PUMP_BATTERY->Triple(s?.pump?.batteryPercent?.toFloat()?.coerceIn(0f,100f)?:0f,0f,100f)
    ProviderKind.PHONE_BATTERY->Triple(s?.device?.phoneBatteryPercent?.toFloat()?.coerceIn(0f,100f)?:0f,0f,100f)
    else->null
   }
   if(ranged!=null)return RangedValueComplicationData.Builder(ranged.first,ranged.second,ranged.third,description).setText(PlainComplicationText.Builder(pair.first).build()).setTapAction(tap).build()
  }
  if(kind==ProviderKind.LONG_STATUS||type==ComplicationType.LONG_TEXT)return LongTextComplicationData.Builder(PlainComplicationText.Builder(pair.first).build(),description).setTitle(PlainComplicationText.Builder(pair.second).build()).setTapAction(tap).build()
  return ShortTextComplicationData.Builder(PlainComplicationText.Builder(pair.first.take(24)).build(),description).setTitle(PlainComplicationText.Builder(pair.second.take(16)).build()).setTapAction(tap).build()
 }
 private fun render(state:TherapyDisplayState?,valueOnly:Boolean):Bitmap{val w=400;val h=if(valueOnly)200 else 240;val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val c=Canvas(b);val p=Paint(Paint.ANTI_ALIAS_FLAG);c.drawColor(Color.TRANSPARENT);val g=state?.glucose;val low=state?.target?.lowMgDl;val high=state?.target?.highMgDl;p.color=when{g==null->Color.GRAY;low!=null&&g.valueMgDl<low->Color.RED;high!=null&&g.valueMgDl>high->Color.rgb(255,165,0);else->Color.rgb(103,223,232)};p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=if(valueOnly)100f else 50f;c.drawText(if(g==null)"—" else glucose(g)+arrow(g.trend),w/2f,if(valueOnly)130f else 58f,p);if(!valueOnly){val list=state?.glucoseHistory.orEmpty();if(list.size>1){val minT=list.first().measuredAtEpochMs;val maxT=list.last().measuredAtEpochMs.coerceAtLeast(minT+1);val minV=(list.minOf{it.valueMgDl}-20).coerceAtLeast(20.0);val maxV=list.maxOf{it.valueMgDl}+20;p.style=Paint.Style.STROKE;p.strokeWidth=5f;val path=Path();list.forEachIndexed{i,s->val x=((s.measuredAtEpochMs-minT).toDouble()/(maxT-minT)*(w-20)+10).toFloat();val y=(h-15-((s.valueMgDl-minV)/(maxV-minV)*(h-90))).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};c.drawPath(path,p)}};return b}
 private fun glucose(g:GlucoseState)=TherapyDisplayFormatter.glucose(g)
 private fun signed(v:Double?,u:GlucoseUnit)=TherapyDisplayFormatter.signedDelta(v,u)
 private fun arrow(t:Trend)=TherapyDisplayFormatter.trendArrow(t)
 private fun units(v:Double?,suffix:String,digits:Int)=TherapyDisplayFormatter.units(v,suffix,digits)
 private fun percent(v:Int?)=TherapyDisplayFormatter.percent(v)
 private fun timeAgo(t:Long?,now:Long)=TherapyDisplayFormatter.ageMinutes(t,now)
 private fun range(g:GlucoseState?,t:TargetState?,stale:Boolean):String{val low=t?.lowMgDl;val high=t?.highMgDl;return when{stale->"stale";g==null->"no data";low!=null&&g.valueMgDl<low->"low";high!=null&&g.valueMgDl>high->"high";else->"in range"}}
 private fun target(t:TargetState?,u:GlucoseUnit)=TherapyDisplayFormatter.target(t,u)
 private fun preview()=TherapyDisplayState(receivedAtEpochMs=System.currentTimeMillis(),sourceVersion="AAPS dev",glucose=GlucoseState(123.0,GlucoseUnit.MG_DL,Trend.FLAT,System.currentTimeMillis(),5.0,3.0),insulin=InsulinState(1.2,0.8,0.4),carbs=CarbState(15.0,0.0),basal=BasalState(0.8,tempPercent=120,displayText="120%"),target=TargetState(70.0,180.0),loop=LoopState("enacted",System.currentTimeMillis()),pump=PumpState("OK",120.0,80),device=DeviceState(85,90),profile=ProfileState("Default"),capabilities=DataCapability.entries.toSet())
}

class GlucoseComplication:TherapyComplicationService(ProviderKind.GLUCOSE)
class GlucoseTrendComplication:TherapyComplicationService(ProviderKind.GLUCOSE_TREND)
class GlucoseDeltaComplication:TherapyComplicationService(ProviderKind.GLUCOSE_DELTA)
class GlucoseTrendDeltaComplication:TherapyComplicationService(ProviderKind.GLUCOSE_TREND_DELTA)
class GlucoseAgeComplication:TherapyComplicationService(ProviderKind.GLUCOSE_AGE)
class GlucoseImageComplication:TherapyComplicationService(ProviderKind.GLUCOSE_IMAGE)
class GlucoseRangeComplication:TherapyComplicationService(ProviderKind.GLUCOSE_RANGE)
class GlucoseRangedComplication:TherapyComplicationService(ProviderKind.GLUCOSE_RANGED)
class GlucoseGraphComplication:TherapyComplicationService(ProviderKind.GRAPH)
class GlucoseGraphLargeComplication:TherapyComplicationService(ProviderKind.GRAPH_LARGE)
class IobComplication:TherapyComplicationService(ProviderKind.IOB)
class BolusIobComplication:TherapyComplicationService(ProviderKind.BOLUS_IOB)
class BasalIobComplication:TherapyComplicationService(ProviderKind.BASAL_IOB)
class CobComplication:TherapyComplicationService(ProviderKind.COB)
class IobCobComplication:TherapyComplicationService(ProviderKind.IOB_COB)
class BasalComplication:TherapyComplicationService(ProviderKind.BASAL)
class TempBasalComplication:TherapyComplicationService(ProviderKind.TEMP_BASAL)
class TempTargetComplication:TherapyComplicationService(ProviderKind.TEMP_TARGET)
class LoopComplication:TherapyComplicationService(ProviderKind.LOOP)
class LastLoopComplication:TherapyComplicationService(ProviderKind.LOOP_LAST)
class ProfileComplication:TherapyComplicationService(ProviderKind.PROFILE)
class ReservoirComplication:TherapyComplicationService(ProviderKind.RESERVOIR)
class PumpBatteryComplication:TherapyComplicationService(ProviderKind.PUMP_BATTERY)
class PhoneBatteryComplication:TherapyComplicationService(ProviderKind.PHONE_BATTERY)
class SourceComplication:TherapyComplicationService(ProviderKind.SOURCE)
class AapsStatusComplication:TherapyComplicationService(ProviderKind.AAPS_STATUS)
class LongStatusComplication:TherapyComplicationService(ProviderKind.LONG_STATUS)

object AllProviders { val classes=listOf(GlucoseComplication::class.java,GlucoseTrendComplication::class.java,GlucoseDeltaComplication::class.java,GlucoseTrendDeltaComplication::class.java,GlucoseAgeComplication::class.java,GlucoseImageComplication::class.java,GlucoseRangeComplication::class.java,GlucoseRangedComplication::class.java,GlucoseGraphComplication::class.java,GlucoseGraphLargeComplication::class.java,IobComplication::class.java,BolusIobComplication::class.java,BasalIobComplication::class.java,CobComplication::class.java,IobCobComplication::class.java,BasalComplication::class.java,TempBasalComplication::class.java,TempTargetComplication::class.java,LoopComplication::class.java,LastLoopComplication::class.java,ProfileComplication::class.java,ReservoirComplication::class.java,PumpBatteryComplication::class.java,PhoneBatteryComplication::class.java,SourceComplication::class.java,AapsStatusComplication::class.java,LongStatusComplication::class.java) }
