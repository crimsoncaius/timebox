package com.timebox.android.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.timebox.android.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        // Ordinary nullable DTOs keep absent-means-unchanged. Fields that must be cleared
        // use request-specific JsonObject builders with an explicit JsonNull instead.
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
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-Timebox-Protocol", "activity-online-v1").build()
                chain.proceed(request)
            }
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
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    fun validatedBaseUrl(raw: String): String {
        require(raw.isNotBlank()) { "Enter the Timebox server address." }
        require(raw.none(Char::isWhitespace)) { "The server address cannot contain spaces." }
        val value = normalizeBaseUrl(raw)
        val url = value.toHttpUrlOrNull()
        require(url != null && url.host.isNotBlank() && (!raw.contains("://") || raw.startsWith("http://", true) || raw.startsWith("https://", true))) {
            "Enter a valid HTTP or HTTPS server address."
        }
        require(url.username.isEmpty() && url.password.isEmpty()) { "Use the API key field for credentials, not the server address." }
        require(url.query == null && url.fragment == null) { "Remove the query or fragment from the server address." }
        return url.toString()
    }
}
