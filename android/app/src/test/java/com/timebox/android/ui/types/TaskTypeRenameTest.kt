package com.timebox.android.ui.types

import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.TaskType
import com.timebox.android.data.remote.TaskTypeCreateDto
import com.timebox.android.data.remote.TaskTypeDto
import com.timebox.android.data.remote.TimeboxApi
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskTypeRenameTest {
    @Test
    fun `rename keeps draft on failure and reloads descendants on success`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            var fail = true
            var rows = listOf(TaskTypeDto(1, "coding"), TaskTypeDto(2, "coding/ai"))
            val api = Proxy.newProxyInstance(TimeboxApi::class.java.classLoader, arrayOf(TimeboxApi::class.java)) { _, method, args ->
                when (method.name) {
                    "listTaskTypes" -> rows
                    "renameTaskType" -> {
                        assertEquals(1, args!![0])
                        assertEquals("learning", (args[1] as TaskTypeCreateDto).name)
                        if (fail) throw IllegalStateException("collision")
                        rows = listOf(TaskTypeDto(1, "learning"), TaskTypeDto(2, "learning/ai"))
                        rows[0]
                    }
                    else -> error(method.name)
                }
            } as TimeboxApi
            val vm = TypesViewModel(TimeboxRepository(api))
            vm.load()
            vm.state.first { !it.loading }
            vm.beginRename(TaskType(1, "coding", 0))
            vm.changeRename(" Learning ")
            vm.saveRename()
            vm.state.first { it.renameError != null }
            assertEquals(" Learning ", vm.state.value.renameInput)
            assertEquals(1, vm.state.value.renaming?.id)
            fail = false
            vm.saveRename()
            vm.state.first { it.renaming == null && !it.loading }
            assertEquals(listOf("learning", "learning/ai"), vm.state.value.groups.flatMap { it.items }.map { it.name })
            vm.beginRename(TaskType(3, "unspecified", 0))
            assertNull(vm.state.value.renaming)
            vm.deleteType(TaskType(3, "unspecified", 0))
            assertFalse(vm.state.value.saving)
        } finally { Dispatchers.resetMain() }
    }
}
