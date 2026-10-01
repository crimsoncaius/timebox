package com.timebox.android.ui.day

import com.timebox.android.BuildConfig
import com.timebox.android.data.remote.ActivityPlanDto
import com.timebox.android.data.remote.ApiFactory
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Isolated review server only. This is not the production plan-and-track protocol. */
internal fun createReviewPlan(minutes: Int, typeId: Int, name: String, currentId: Int): ActivityPlanDto {
    check(BuildConfig.DEBUG && BuildConfig.PLAN_NOW_PROTOTYPE)
    check(BuildConfig.DEFAULT_BASE_URL == "http://10.0.2.2:12075/") { "Use the isolated issue 297 review build." }
    val connection = URL(BuildConfig.DEFAULT_BASE_URL + "prototype/plan-now").openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000; connection.readTimeout = 10000
        connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(JSONObject().put("minutes", minutes).put("task_type_id", typeId).put("name", name).put("current_id", currentId).toString().toByteArray()) }
        check(connection.responseCode == 200) { "Could not save the review plan. Refresh Day and try again." }
        return ApiFactory.json.decodeFromString<ActivityPlanDto>(connection.inputStream.bufferedReader().use { it.readText() })
    } finally { connection.disconnect() }
}
