package com.weatherapi.forecast

import com.weatherapi.forecast.common.config.AppConfig
import com.weatherapi.forecast.common.config.EnvironmentProvider
import com.weatherapi.forecast.data.remote.api.WeatherApiService
import com.weatherapi.forecast.data.remote.interceptor.ApiKeyInterceptor
import com.weatherapi.forecast.data.remote.interceptor.RetryInterceptor
import com.weatherapi.forecast.data.remote.interceptor.SanitizedHttpLoggingInterceptor
import com.weatherapi.forecast.data.repository.WeatherRepositoryImpl
import com.weatherapi.forecast.domain.repository.WeatherRepository
import com.weatherapi.forecast.domain.usecase.GetForecastUseCase
import com.weatherapi.forecast.presentation.cli.CliParser
import com.weatherapi.forecast.presentation.formatter.FormatForecastTableUseCase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.PrintStream
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val exitCode = runApplication(args, System.out, System.err)
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}

/**
 * Testable application entry point that returns an integer exit code
 * without calling [exitProcess], permitting in-memory integration testing.
 * Protected by a top-level exception trap to guard against uncaught runtime panics.
 */
fun runApplication(
    args: Array<String>,
    stdout: PrintStream = System.out,
    stderr: PrintStream = System.err,
    envProvider: EnvironmentProvider = EnvironmentProvider.SYSTEM,
    repositoryOverride: WeatherRepository? = null
): Int {
    return try {
        executeApplication(args, stdout, stderr, envProvider, repositoryOverride)
    } catch (t: Throwable) {
        stderr.println("[FATAL] An unhandled error occurred: ${t.localizedMessage ?: t.javaClass.simpleName}")
        1
    }
}

private fun executeApplication(
    args: Array<String>,
    stdout: PrintStream,
    stderr: PrintStream,
    envProvider: EnvironmentProvider,
    repositoryOverride: WeatherRepository?
): Int {
    val cliArgs = CliParser.parse(args)

    if (cliArgs.isHelpRequested) {
        CliParser.printHelp(stdout)
        return 0
    }

    if (cliArgs.parsingError != null) {
        stderr.println("[ERROR] ${cliArgs.parsingError}")
        return 1
    }

    val resolvedApiKey = AppConfig.resolveApiKey(cliArgs.apiKey, envProvider)
    if (resolvedApiKey.isNullOrBlank()) {
        stderr.println(
            """
            [ERROR] API key is missing!
            Please provide a valid WeatherAPI.com key using one of the following methods:
              1. CLI argument:        --api-key=YOUR_KEY (or -k YOUR_KEY)
              2. Environment variable: export WEATHER_API_KEY="YOUR_KEY"
              3. JVM system property: -Dweather.api.key="YOUR_KEY"

            Get a free API key at: https://www.weatherapi.com/signup.aspx
            """.trimIndent()
        )
        return 1
    }

    // Sanitize and deduplicate requested cities (case-insensitive) to protect quota and prevent duplicate table rows
    val rawCities = cliArgs.cities ?: AppConfig.DEFAULT_CITIES
    val targetCities = rawCities.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }

    val config = AppConfig(
        apiKey = resolvedApiKey,
        targetCities = targetCities,
        days = cliArgs.days ?: AppConfig.DEFAULT_FORECAST_DAYS
    )

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val okHttpClient = OkHttpClient.Builder()
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        })
        .addInterceptor(RetryInterceptor())
        .addInterceptor(ApiKeyInterceptor(config.apiKey))
        .addInterceptor(SanitizedHttpLoggingInterceptor.create(cliArgs.isVerbose))
        .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    return try {
        val weatherRepository = repositoryOverride ?: run {
            val retrofit = Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val apiService = retrofit.create(WeatherApiService::class.java)
            WeatherRepositoryImpl(apiService, json)
        }
        val getForecastUseCase = GetForecastUseCase(weatherRepository)
        val formatTableUseCase = FormatForecastTableUseCase()

        runBlocking {
            val batchResult = getForecastUseCase.execute(
                cities = config.targetCities,
                days = config.days
            )

            if (batchResult.failures.isNotEmpty()) {
                stderr.println("[WARNING] Some cities could not be retrieved:")
                for ((city, error) in batchResult.failures) {
                    stderr.println(" - $city: ${error.message}")
                }
                stderr.println()
            }

            if (batchResult.successful.isEmpty()) {
                stderr.println("[FATAL] Could not retrieve weather forecast for any requested city.")
                return@runBlocking 1
            }

            val hasAnyForecasts = batchResult.successful.any { cf ->
                if (cliArgs.targetDate != null) {
                    cf.forecasts.any { it.date == cliArgs.targetDate }
                } else {
                    cf.nextDayForecast != null
                }
            }
            if (!hasAnyForecasts) {
                val errorMsg = if (cliArgs.targetDate != null) {
                    "[ERROR] No forecast data available for date '${cliArgs.targetDate}' across requested cities."
                } else {
                    "[ERROR] No next-day forecast data available for any of the requested cities."
                }
                stderr.println(errorMsg)
                return@runBlocking 1
            }

            // Output table strictly to STDOUT as mandated by requirements
            val formattedTable = formatTableUseCase.execute(
                cityForecasts = batchResult.successful,
                targetDate = cliArgs.targetDate
            )
            stdout.println(formattedTable)

            // Return exit code 2 on partial degradation so automation scripts can detect missing cities
            if (batchResult.failures.isNotEmpty()) 2 else 0
        }
    } finally {
        // Guaranteed resource release: shutdown thread pools and clear connection pool
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }
}
