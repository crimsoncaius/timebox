package com.timebox.android.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A PATCH value has three states. Kotlin null alone cannot represent both "omit" and
 * "clear", and the API intentionally gives those states different meanings.
 */
sealed interface PatchField<out T> {
    data object Absent : PatchField<Nothing>
    data object Null : PatchField<Nothing>
    data class Value<T>(val value: T) : PatchField<T>

    companion object {
        fun <T> of(value: T): PatchField<T> = Value(value)
        fun <T> clear(): PatchField<T> = Null
    }
}

internal class PatchBodyBuilder {
    private val values = linkedMapOf<String, JsonElement>()

    fun string(name: String, field: PatchField<String>) = put(name, field, ::JsonPrimitive)
    fun int(name: String, field: PatchField<Int>) = put(name, field, ::JsonPrimitive)
    fun boolean(name: String, field: PatchField<Boolean>) = put(name, field, ::JsonPrimitive)
    fun strings(name: String, field: PatchField<List<String>>) =
        put(name, field) { list -> JsonArray(list.map(::JsonPrimitive)) }
    fun ints(name: String, field: PatchField<List<Int>>) =
        put(name, field) { list -> JsonArray(list.map(::JsonPrimitive)) }

    private fun <T> put(name: String, field: PatchField<T>, encode: (T) -> JsonElement) {
        when (field) {
            PatchField.Absent -> Unit
            PatchField.Null -> values[name] = JsonNull
            is PatchField.Value -> values[name] = encode(field.value)
        }
    }

    fun build(): JsonObject = JsonObject(values)
}

internal fun patchBody(build: PatchBodyBuilder.() -> Unit): JsonObject =
    PatchBodyBuilder().apply(build).build()
