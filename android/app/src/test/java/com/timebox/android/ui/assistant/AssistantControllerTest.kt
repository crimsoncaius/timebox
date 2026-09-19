package com.timebox.android.ui.assistant

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantControllerTest {
    private class Fake : AssistantTransport {
        val events = MutableSharedFlow<AssistantEvent>()
        var run = ""
        var sequence = 0
        var creates = 0
        var stops = 0
        var deletes = 0
        var acks = 0
        var ackFailures = 0
        var streams = 0
        override suspend fun create() = "conversation-${++creates}"
        override suspend fun delete(conversation: String) { deletes++ }
        override suspend fun stop(conversation: String, run: String) { stops++ }
        override suspend fun acknowledge(conversation: String, run: String) {
            acks++
            if (ackFailures > 0) { ackFailures--; throw java.io.IOException("Connection lost") }
        }
        override fun stream(conversation: String, run: String, message: String): Flow<AssistantEvent> {
            this.run = run; sequence = 0
            streams++
            return events.takeWhile { it.kind != "eof" }
        }
        suspend fun emit(kind: String, text: String = "") {
            events.emit(AssistantEvent(kind, buildJsonObject {
                put("run_id", run); put("sequence", ++sequence); put("text", text); put("message", text)
            }))
        }
    }

    @Test fun `stop retains partial text and retry is explicit`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Today?"); runCurrent()
        api.emit("text_delta", "Partial"); runCurrent()
        controller.stop(); runCurrent()
        assertEquals("Partial", controller.state.value.exchanges.last().answer)
        assertEquals("Stopped", controller.state.value.exchanges.last().status)
        assertEquals(1, api.stops)
        assertEquals(0, api.acks)
        controller.retry(); runCurrent()
        assertEquals(2, controller.state.value.exchanges.size)
        assertTrue(controller.state.value.busy)
    }

    @Test fun `reset cancels old run and next conversation is fresh`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("First"); runCurrent()
        controller.newConversation(); runCurrent()
        api.emit("text_delta", "Late old output"); runCurrent()
        assertTrue(controller.state.value.exchanges.isEmpty())
        assertEquals(1, api.deletes)
        controller.send("Second"); runCurrent()
        assertEquals(2, api.creates)
        assertEquals("Second", controller.state.value.exchanges.single().question)
    }

    @Test fun `EOF is interrupted and cannot acknowledge partial context`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Today?"); runCurrent()
        api.emit("text_delta", "Partial"); runCurrent()
        api.emit("eof"); runCurrent()
        assertEquals("Interrupted", controller.state.value.exchanges.single().status)
        assertFalse(controller.state.value.busy)
        assertEquals(0, api.acks)
        assertEquals(1, controller.state.value.exchanges.size)
    }

    @Test fun `completed response is acknowledged and duplicate send is blocked`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("Today?"); controller.send("Duplicate"); runCurrent()
        api.emit("text_delta", "Answer"); runCurrent()
        api.emit("completed"); runCurrent()
        api.emit("eof"); runCurrent()
        assertEquals("Complete", controller.state.value.exchanges.single().status)
        assertEquals(0, api.acks)
        assertFalse(controller.state.value.busy)
        controller.send("Follow up"); runCurrent()
        assertEquals(1, api.acks)
    }

    @Test fun `lost acknowledgement does not replay generation or discard completed answer`() = runTest {
        val api = Fake()
        val controller = AssistantController(backgroundScope) { api }
        controller.send("First"); runCurrent()
        api.emit("text_delta", "Complete answer"); runCurrent()
        api.emit("completed"); runCurrent()
        api.emit("eof"); runCurrent()
        api.ackFailures = 1
        controller.send("Follow up"); runCurrent()
        assertEquals("Complete", controller.state.value.exchanges.first().status)
        assertEquals(1, api.streams)
        assertEquals("Interrupted", controller.state.value.exchanges.last().status)
        controller.retry(); runCurrent()
        assertEquals(2, api.acks)
        assertEquals(2, api.streams)
    }
}
