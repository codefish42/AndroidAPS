package app.aaps.plugins.aps.openAPSSMB

import app.aaps.core.interfaces.aps.VariableSensitivityResult
import app.aaps.core.interfaces.iob.GlucoseStatus
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.aps.APSResultObject
import dagger.android.HasAndroidInjector
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class DetermineBasalResultSMB private constructor(
    injector: HasAndroidInjector
) : APSResultObject(injector), VariableSensitivityResult {

    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider

    private var eventualBG = 0.0
    private var snoozeBG = 0.0
    override var variableSens: Double? = null

    internal constructor(injector: HasAndroidInjector, result: JSONObject) : this(injector) {
        date = dateUtil.now()
        json = result
        try {
            if (result.has("error")) {
                reason = result.getString("error")
                return
            }
            reason = result.getString("reason")
            if (result.has("eventualBG")) eventualBG = result.getDouble("eventualBG")
            if (result.has("snoozeBG")) snoozeBG = result.getDouble("snoozeBG")
            //if (result.has("insulinReq")) insulinReq = result.getDouble("insulinReq");
            if (result.has("carbsReq")) carbsReq = result.getInt("carbsReq")
            if (result.has("carbsReqWithin")) carbsReqWithin = result.getInt("carbsReqWithin")
            if (result.has("rate") && result.has("duration")) {
                isTempBasalRequested = true
                rate = result.getDouble("rate")
                if (rate < 0.0) rate = 0.0

                // Ketoacidosis Protection
                // Get active BaseBasalRate
                val baseBasalRate: Double = activePlugin.activePump.baseBasalRate

                val bgStatus: GlucoseStatus? = glucoseStatusProvider.getGlucoseStatusData()
                val isLow = (bgStatus != null && bgStatus.glucose < 120 && bgStatus.delta < 0) || (bgStatus != null && bgStatus.glucose < 200 && bgStatus.delta < -10)

                // Activate a small TBR
                if (!isLow) {
                    var cutoff: Double = baseBasalRate * 0.2 // min: 20%
                    val min = 0.1 // absolute minimum for Dana RS
                    if (cutoff < min) cutoff = min
                    if (rate < cutoff) rate = cutoff
                }
                // End Ketoacidosis Protection

                duration = result.getInt("duration")
            } else {
                rate = (-1).toDouble()
                duration = -1
            }
            if (result.has("units")) {
                smb = result.getDouble("units")
            } else {
                smb = 0.0
            }
            if (result.has("targetBG")) {
                targetBG = result.getDouble("targetBG")
            }
            if (result.has("deliverAt")) {
                val date = result.getString("deliverAt")
                try {
                    deliverAt = dateUtil.fromISODateString(date)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.APS, "Error parsing 'deliverAt' date: $date", e)
                }
            }
            if (result.has("variable_sens")) variableSens = result.getDouble("variable_sens")
        } catch (e: JSONException) {
            aapsLogger.error(LTag.APS, "Error parsing determine-basal result JSON", e)
        }
    }

    override fun newAndClone(injector: HasAndroidInjector): DetermineBasalResultSMB {
        val newResult = DetermineBasalResultSMB(injector)
        doClone(newResult)
        newResult.eventualBG = eventualBG
        newResult.snoozeBG = snoozeBG
        return newResult
    }

    override fun json(): JSONObject? {
        try {
            return JSONObject(json.toString())
        } catch (e: JSONException) {
            aapsLogger.error(LTag.APS, "Error converting determine-basal result to JSON", e)
        }
        return null
    }

    init {
        hasPredictions = true
    }
}