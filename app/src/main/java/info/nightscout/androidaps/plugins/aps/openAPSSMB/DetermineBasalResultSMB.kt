package info.nightscout.androidaps.plugins.aps.openAPSSMB

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.shared.logging.LTag
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class DetermineBasalResultSMB private constructor(injector: HasAndroidInjector) : APSResult(injector) {

    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider

    private var eventualBG = 0.0
    private var snoozeBG = 0.0

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
                tempBasalRequested = true
                rate = result.getDouble("rate")
                if (rate < 0.0) rate = 0.0

                // Ketoacidosis Protection
                // Calculate IOB
                // treatmentsPlugin.updateTotalIOBTreatments()
                // treatmentsPlugin.updateTotalIOBTempBasals()
                // val bolusIob: IobTotal = treatmentsPlugin.getLastCalculationTreatments()
                // val basalIob: IobTotal = treatmentsPlugin.getLastCalculationTempBasals()
                // Get active BaseBasalRate
                val baseBasalRate = activePlugin.activePump.baseBasalRate

                var isLow = false
                val lastBG: GlucoseValue? = iobCobCalculator.ads.lastBg()
                // Log.d(TAG, logPrefix + "LastBg=" + lastBG);
                if (lastBG != null) {
                    val bgStatus: GlucoseStatus? = glucoseStatusProvider?.getGlucoseStatusData()
                    isLow = (bgStatus != null) &&
                        ((bgStatus.glucose < 120 && bgStatus.delta < 0) || (bgStatus.glucose < 200 && bgStatus.delta < -10))
                }

                // Activate a small TBR
                if (sp.getBoolean(R.string.key_keto_protect, false)) {
                    if (!sp.getBoolean(R.string.key_variable_keto_protect_strategy, true) ||
                        !isLow //((bolusIob.iob + basalIob.basaliob) < (0 - baseBasalRate) && -(bolusIob.activity + basalIob.activity) > 0)
                    ) {
                        var cutoff: Double = baseBasalRate * (sp.getDouble(R.string.keto_protect_basal, 20.0) * 0.01)
                        val min: Double = sp.getDouble(R.string.keto_protect_min, 0.1)
                        if (cutoff < min) cutoff = min
                        if (rate < cutoff) rate = cutoff
                    }
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