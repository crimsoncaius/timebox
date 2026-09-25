package com.timebox.android.ui.types

import androidx.lifecycle.viewModelScope
import com.timebox.android.data.*
import com.timebox.android.data.remote.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody

@OptIn(ExperimentalCoroutinesApi::class)
class TaskTypeMergeTest {
    @Test fun `collision previews then stale confirmation requires another explicit confirmation`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var vm: TypesViewModel? = null
        try {
            var merged = false
            var mergeCalls = 0
            var previewCalls = 0
            val preview = TaskTypeMergePreview(1, "travel", 2, "transportation", "one", listOf(TaskTypeMergeChange(1, "travel", "transportation", "combine")), 8, 2, 1, 1, 12, 34, 2)
            val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
                when(method.name) {
                    "listTaskTypes" -> if (merged) listOf(TaskTypeDto(2, "transportation")) else listOf(TaskTypeDto(1, "travel"), TaskTypeDto(2, "transportation"))
                    "previewTaskTypeMerge" -> { previewCalls++; preview.copy(previewToken = if (previewCalls == 1) "one" else "two") }
                    "mergeTaskType" -> {
                        mergeCalls++
                        if (mergeCalls == 1) throw retrofit2.HttpException(retrofit2.Response.error<Any>(409, "changed".toResponseBody()))
                        assertEquals("two", (args!![1] as TaskTypeMergeRequest).previewToken)
                        merged = true
                        preview
                    }
                    "renameTaskType" -> error("A collision must not perform a rename")
                    else -> error(method.name)
                }
            } as TimeboxApi
            val model = TypesViewModel(TimeboxRepository(api))
            vm = model
            model.load(); model.state.first { !it.loading }
            model.beginRename(TaskType(1, "travel", 0)); model.changeRename("Transportation"); model.saveRename()
            model.state.first { it.mergePreview != null && !it.saving }
            assertEquals(0, mergeCalls)
            model.confirmMerge()
            model.state.first { it.mergePreview?.previewToken == "two" && !it.saving }
            assertEquals(1, mergeCalls)
            assertNotNull(model.state.value.renameError)
            assertNotNull(model.state.value.renaming)
            model.confirmMerge()
            model.state.first { it.groups.flatMap { group -> group.items }.size == 1 }
            assertNull(model.state.value.renaming)
            assertEquals(2, mergeCalls)
        } finally {
            vm?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }
}
