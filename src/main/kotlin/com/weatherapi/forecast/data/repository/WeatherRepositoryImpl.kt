package com.weatherapi.forecast.data.repository

import com.weatherapi.forecast.common.error.WeatherError
import com.weatherapi.forecast.data.mapper.WeatherMapper
import com.weatherapi.forecast.data.remote.api.WeatherApiService
import com.weatherapi.forecast.data.remote.dto.WeatherApiErrorWrapper
import com.weatherapi.forecast.domain.model.CityForecast
import com.weatherapi.forecast.domain.repository.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Implementation of [WeatherRepository] interfacing with WeatherAPI via Retrofit.
 *
 * Implements clean error handling without using naive 'runCatching',
 * guaranteeing that [CancellationException] is strictly rethrown to preserve
 * structured concurrency across coroutines.
 */
class WeatherRepositoryImpl(
    private val apiService: WeatherApiService,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
) : WeatherRepository {

    override suspend fun getForecast(city: String, days: Int): Result<CityForecast> {
        return try {
            val responseDto = apiService.getForecast(
                query = city,
                days = days
            )

            // Check for API-level error returned in a 200 response body
            responseDto.error?.let { apiError ->
                return Result.failure(
                    mapApiError(
                        city = city,
                        httpCode = 200,
                        apiCode = apiError.code,
                        apiMsg = apiError.message
                    )
                )
            }

            val domainModel = WeatherMapper.toDomain(city, responseDto)
            if (domainModel.forecasts.isEmpty()) {
                Result.failure(
                    WeatherError.GenericApiError(
                        httpCode = 200,
                        apiErrorCode = null,
                        message = "WeatherAPI returned empty forecast list for '$city'."
                    )
                )
            } else {
                Result.success(domainModel)
            }
        } catch (ce: CancellationException) {
            // CRITICAL: rethrow CancellationException so coroutine cancellations are never swallowed
            throw ce
        } catch (se: SerializationException) {
            Result.failure(
                WeatherError.SerializationError(
                    message = "Failed to deserialize WeatherAPI response for '$city': ${se.message}",
                    cause = se
                )
            )
        } catch (httpEx: HttpException) {
            Result.failure(mapHttpException(city, httpEx))
        } catch (timeoutEx: SocketTimeoutException) {
            Result.failure(
                WeatherError.NetworkError(
                    message = "Connection to WeatherAPI timed out while fetching forecast for '$city'.",
                    cause = timeoutEx
                )
            )
        } catch (ioEx: IOException) {
            Result.failure(
                WeatherError.NetworkError(
                    message = "Network error occurred while contacting WeatherAPI for '$city': ${ioEx.localizedMessage}",
                    cause = ioEx
                )
            )
        } catch (err: WeatherError) {
            Result.failure(err)
        } catch (other: Exception) {
            Result.failure(
                WeatherError.GenericApiError(
                    httpCode = null,
                    apiErrorCode = null,
                    message = "Unexpected error while fetching forecast for '$city': ${other.localizedMessage}",
                    cause = other
                )
            )
        }
    }

    private fun mapHttpException(city: String, ex: HttpException): WeatherError {
        val httpCode = ex.code()
        val errorBody = runCatching { ex.response()?.errorBody()?.string() }.getOrNull()

        val parsedError = errorBody?.let { body ->
            runCatching {
                json.decodeFromString<WeatherApiErrorWrapper>(body).error
            }.getOrNull()
        }

        val apiCode = parsedError?.code
        val apiMsg = parsedError?.message ?: ex.message()

        return mapApiError(city, httpCode, apiCode, apiMsg, ex)
    }

    companion object {
        private val INVALID_KEY_HTTP_CODES = setOf(401, 403)
        private val INVALID_KEY_API_CODES = setOf(1002, 2006, 2008, 2009)
    }

    private fun mapApiError(
        city: String,
        httpCode: Int?,
        apiCode: Int?,
        apiMsg: String?,
        cause: Throwable? = null
    ): WeatherError {
        val safeMsg = apiMsg ?: "Unknown error"
        return when {
            httpCode == 429 || apiCode == 2007 -> WeatherError.QuotaExceeded(
                message = "Rate limit or quota exceeded" + (httpCode?.let { " (HTTP $it)" } ?: "") + ": $safeMsg",
                cause = cause
            )
            httpCode in INVALID_KEY_HTTP_CODES || apiCode in INVALID_KEY_API_CODES -> WeatherError.InvalidApiKey(
                message = "Authentication failed" + (httpCode?.let { " (HTTP $it)" } ?: "") + ": $safeMsg",
                cause = cause
            )
            apiCode == 1006 -> WeatherError.CityNotFound(
                city = city,
                message = "Location '$city' not found by WeatherAPI: $safeMsg",
                cause = cause
            )
            else -> WeatherError.GenericApiError(
                httpCode = httpCode,
                apiErrorCode = apiCode,
                message = "WeatherAPI error for '$city' (code $apiCode): $safeMsg",
                cause = cause
            )
        }
    }
}
