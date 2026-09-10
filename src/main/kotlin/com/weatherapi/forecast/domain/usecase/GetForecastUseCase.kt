package com.weatherapi.forecast.domain.usecase

import com.weatherapi.forecast.domain.model.CityForecast
import com.weatherapi.forecast.domain.repository.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Result of a batch forecast retrieval operation.
 */
data class ForecastBatchResult(
    val successful: List<CityForecast>,
    val failures: List<CityFailure>
) {
    val isFullySuccessful: Boolean get() = failures.isEmpty()
    val hasAnySuccess: Boolean get() = successful.isNotEmpty()
}

/**
 * Details of a failure for an individual city.
 */
data class CityFailure(
    val city: String,
    val error: Throwable
)

/**
 * Use case responsible for orchestrating concurrent weather forecast retrieval
 * across multiple locations using Kotlin Coroutines and structured concurrency.
 */
class GetForecastUseCase(
    private val weatherRepository: WeatherRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY
) {

    companion object {
        const val DEFAULT_MAX_CONCURRENCY = 8
    }

    /**
     * Retrieves the weather forecast for all specified cities concurrently.
     *
     * Utilizes [supervisorScope] to guarantee that a failure in retrieving data
     * for one city does not cancel or compromise requests for other cities.
     * Concurrency is throttled via a [Semaphore] to protect against API rate limits.
     *
     * @param cities List of city names to query.
     * @param days Number of days for the forecast (default is 3).
     * @return [ForecastBatchResult] containing successful forecasts and individual failure details.
     */
    suspend fun execute(
        cities: List<String>,
        days: Int = WeatherRepository.DEFAULT_DAYS
    ): ForecastBatchResult = withContext(ioDispatcher) {
        val semaphore = Semaphore(maxConcurrency)
        supervisorScope {
            val deferredList = cities.map { cityName ->
                cityName to async {
                    semaphore.withPermit {
                        weatherRepository.getForecast(cityName, days)
                    }
                }
            }

            val successfulList = mutableListOf<CityForecast>()
            val failureList = mutableListOf<CityFailure>()

            for ((cityName, deferred) in deferredList) {
                try {
                    val result = deferred.await()
                    result.fold(
                        onSuccess = { forecast -> successfulList.add(forecast) },
                        onFailure = { error -> failureList.add(CityFailure(cityName, error)) }
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    failureList.add(CityFailure(cityName, e))
                }
            }

            ForecastBatchResult(
                successful = successfulList,
                failures = failureList
            )
        }
    }
}
