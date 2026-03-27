package io.github.hyrodium.realsizeviewerapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalibrationRequest(
    val manufacturer: String,
    val model: String,
    @SerialName("android_version") val androidVersion: String,
    @SerialName("reported_xdpi") val reportedXdpi: Double,
    @SerialName("reported_ydpi") val reportedYdpi: Double,
    @SerialName("calibration_factor_x") val calibrationFactorX: Double,
    @SerialName("calibration_factor_y") val calibrationFactorY: Double,
    @SerialName("app_version") val appVersion: String,
    @SerialName("build_source") val buildSource: String,
)

@Serializable
data class RecommendedCalibrationResponse(
    val manufacturer: String,
    val model: String,
    @SerialName("median_factor_x") val medianFactorX: Double,
    @SerialName("median_factor_y") val medianFactorY: Double,
    @SerialName("recommended_xdpi") val medianReportedXdpi: Double,
    @SerialName("recommended_ydpi") val medianReportedYdpi: Double,
    @SerialName("sample_count") val sampleCount: Int,
    @SerialName("updated_at") val updatedAt: String?,
    @SerialName("low_sample_warning") val lowSampleWarning: Boolean,
)
