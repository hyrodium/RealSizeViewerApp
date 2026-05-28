package io.github.hyrodium.realsizeviewerapp.data

import io.github.hyrodium.realsizeviewerapp.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val BASE_URL = "https://realsizeviewer-server.fly.dev"

@Singleton
class CalibrationApiService @Inject constructor() {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000L
            connectTimeoutMillis = 10_000L
            socketTimeoutMillis = 10_000L
        }
    }

    suspend fun postCalibration(request: CalibrationRequest) {
        val response = client.post("$BASE_URL/calibrations") {
            header("X-API-Key", BuildConfig.API_KEY)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            val body = try { response.bodyAsText() } catch (_: Exception) { "" }
            val detail = if (body.isNotEmpty()) ": $body" else ""
            throw Exception("HTTP ${response.status.value}$detail")
        }
    }

    /** デバイスデータが未登録の場合は null を返す。 */
    suspend fun getRecommended(
        manufacturer: String,
        model: String,
    ): RecommendedCalibrationResponse? {
        val response = client.get("$BASE_URL/calibrations/recommended") {
            header("X-API-Key", BuildConfig.API_KEY)
            parameter("manufacturer", manufacturer)
            parameter("model", model)
        }
        return if (response.status.value == 404) null else response.body()
    }
}
