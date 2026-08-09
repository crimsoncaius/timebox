package com.timebox.android.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.timebox.android.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** Adds the shared secret the backend checks when API_KEY is configured. */
class ApiKeyInterceptor(private val keyProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val key = keyProvider()
        if (key.isBlank()) return chain.proceed(chain.request())
        val request = chain.request().newBuilder()
            .header("X-API-Key", key)
            .build()
        return chain.proceed(request)
    }
}

object ApiFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        // PATCH bodies rely on absent-means-unchanged, so null fields must not be sent.
        explicitNulls = false
    }

    fun create(baseUrl: String, apiKeyProvider: () -> String): TimeboxApi {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(apiKeyProvider))
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TimeboxApi::class.java)
    }

    /** Retrofit requires a trailing slash; users will type `http://host:8000`. */
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
