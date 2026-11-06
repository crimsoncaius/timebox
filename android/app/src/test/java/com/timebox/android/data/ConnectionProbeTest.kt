package com.timebox.android.data

import com.timebox.android.data.remote.TimeboxApi
import java.lang.reflect.Proxy
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConnectionProbeTest {
    @Test fun probeChecksEnteredAddressAndKeyWithoutMutatingConfiguredConnection() = runBlocking {
        val server = ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"))
        var receivedKey: String? = null
        val responder = thread(isDaemon = true) {
            repeat(2) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val headers = generateSequence { reader.readLine()?.takeIf(String::isNotEmpty) }.toList()
                    receivedKey = headers.firstOrNull { it.startsWith("X-API-Key:", true) }?.substringAfter(':')?.trim()
                    val status = if (receivedKey == "test-key") 200 else 403
                    val body = if (status == 200) "[]" else "{\"detail\":\"Invalid key\"}"
                    socket.getOutputStream().write(("HTTP/1.1 $status Result\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
                }
            }
        }
        val untouchedApi = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, _, _ -> error("Configured API must not be touched") } as TimeboxApi
        val repository = TimeboxRepository(untouchedApi)
        val address = "http://127.0.0.1:${server.localPort}/"
        try {
            assertTrue(repository.testConnection(address, "test-key").isSuccess)
            assertEquals("test-key", receivedKey)
            val failure = repository.testConnection(address, "wrong-key").exceptionOrNull()!!
            assertEquals(403, failure.apiError.statusCode)
            assertTrue(failure.apiError.isAuth)
        } finally { server.close(); responder.join(1000) }
        assertTrue(repository.testConnection(address, "test-key").exceptionOrNull()!!.apiError.isNetwork)
    }
}
