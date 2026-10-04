package com.timebox.android.ui.assistant

import android.os.StrictMode
import androidx.test.platform.app.InstrumentationRegistry
import com.timebox.android.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AssistantTaskTransportTest {
    @Test fun responseBodiesArrivingAfterHeadersAreConsumedOffMainThread(): Unit = runBlocking {
        val fixtures = Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("assistant-tasks.json").bufferedReader().use { it.readText() }).jsonObject
        val full = fixtures.getValue("full").jsonObject
        val result = fixtures.getValue("result").jsonObject
        val responses = listOf(
            buildJsonObject { put("conversation_id", full.text("conversation_id")); put("capabilities", JsonArray(emptyList())) },
            full, buildJsonObject { put("operations", JsonArray(listOf(result))) },
        )
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val served = executor.submit {
            responses.forEach { response -> server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                var count = 0
                while (true) {
                    val line = input.readLine()
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", true)) count = line.substringAfter(':').trim().toInt()
                }
                repeat(count) { input.read() }
                val bytes = response.toString().toByteArray(Charsets.UTF_8)
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); output.flush()
                Thread.sleep(250) // Headers resume OkHttp's callback before the body reaches the device.
                output.write(bytes); output.flush()
            } }
        }
        try {
            val api = HttpAssistantTransport(AppSettings("http://127.0.0.1:${server.localPort}/", "", null))
            withContext(Dispatchers.Main) {
                val previous = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build())
                try {
                    assertEquals(full.text("conversation_id"), api.create())
                    assertEquals(full.text("proposal_id"), api.taskReview(full.text("proposal_id")).id)
                    assertEquals(result.text("operation_id"), api.taskStatus(result.text("operation_id"))!!.operationId)
                } finally { StrictMode.setThreadPolicy(previous) }
            }
            served.get(10, TimeUnit.SECONDS)
        } finally { server.close(); executor.shutdownNow() }
    }
}
