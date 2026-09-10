package com.weatherapi.forecast.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Top-level response object returned by WeatherAPI /v1/forecast.json endpoint.
 */
@Serializable
data class ForecastResponseDto(
    @SerialName("location") val location: LocationDto? = null,
    @SerialName("current") val current: CurrentDto? = null,
    @SerialName("forecast") val forecast: ForecastContainerDto? = null,
    @SerialName("error") val error: ApiErrorDto? = null
)

/**
 * Location metadata returned by WeatherAPI.
 */
@Serializable
data class LocationDto(
    @SerialName("name") val name: String? = null,
    @SerialName("region") val region: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("tz_id") val tzId: String? = null,
    @SerialName("localtime") val localtime: String? = null
)

/**
 * Real-time weather observation metadata.
 */
@Serializable
data class CurrentDto(
    @SerialName("temp_c") val tempC: Double? = null,
    @SerialName("humidity") val humidity: Int? = null,
    @SerialName("wind_kph") val windKph: Double? = null,
    @SerialName("wind_dir") val windDir: String? = null,
    @SerialName("wind_degree") val windDegree: Int? = null
)

/**
 * Container holding the list of forecast days.
 */
@Serializable
data class ForecastContainerDto(
    @SerialName("forecastday") val forecastday: List<ForecastDayDto> = emptyList()
)

/**
 * Weather forecast for a specific calendar date.
 */
@Serializable
data class ForecastDayDto(
    @SerialName("date") val date: String? = null,
    @SerialName("day") val day: DayDto? = null,
    @SerialName("hour") val hour: List<HourDto> = emptyList()
)

/**
 * Daily weather summary metrics.
 */
@Serializable
data class DayDto(
    @SerialName("maxtemp_c") val maxtempC: Double? = null,
    @SerialName("mintemp_c") val mintempC: Double? = null,
    @SerialName("avgtemp_c") val avgtempC: Double? = null,
    @SerialName("avghumidity") val avghumidity: Double? = null,
    @SerialName("maxwind_kph") val maxwindKph: Double? = null,
    @SerialName("wind_dir") val windDir: String? = null
)

/**
 * Hourly weather metrics (provides hourly wind direction and bearings).
 */
@Serializable
data class HourDto(
    @SerialName("time") val time: String? = null,
    @SerialName("temp_c") val tempC: Double? = null,
    @SerialName("wind_kph") val windKph: Double? = null,
    @SerialName("wind_degree") val windDegree: Int? = null,
    @SerialName("wind_dir") val windDir: String? = null,
    @SerialName("humidity") val humidity: Int? = null
)

/**
 * Structured error payload returned by WeatherAPI on non-200 responses.
 */
@Serializable
data class ApiErrorDto(
    @SerialName("code") val code: Int? = null,
    @SerialName("message") val message: String? = null
)

@Serializable
data class WeatherApiErrorWrapper(
    @SerialName("error") val error: ApiErrorDto? = null
)
