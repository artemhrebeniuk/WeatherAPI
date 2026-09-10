package com.weatherapi.forecast.presentation.cli

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Parsed command line options.
 */
data class CliArgs(
    val apiKey: String? = null,
    val cities: List<String>? = null,
    val days: Int? = null,
    val targetDate: String? = null,
    val isVerbose: Boolean = false,
    val isHelpRequested: Boolean = false,
    val parsingError: String? = null
)

/**
 * Lightweight, zero-dependency Command Line Interface argument parser.
 */
object CliParser {

    fun parse(args: Array<String>): CliArgs {
        var apiKey: String? = null
        var cities: List<String>? = null
        var days: Int? = null
        var targetDate: String? = null
        var isVerbose = false
        var isHelpRequested = false
        val parsingErrors = mutableListOf<String>()

        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg == "-h" || arg == "--help" -> {
                    isHelpRequested = true
                }
                arg == "-v" || arg == "--verbose" -> {
                    isVerbose = true
                }
                arg == "-k" || arg == "--api-key" -> {
                    if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                        apiKey = args[++i]
                    } else {
                        parsingErrors.add("Missing value for --api-key argument")
                    }
                }
                arg.startsWith("--api-key=") -> {
                    apiKey = arg.substringAfter("--api-key=")
                }
                arg == "-c" || arg == "--cities" -> {
                    if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                        val parsed = args[++i].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        cities = parsed.ifEmpty { null }
                    } else {
                        parsingErrors.add("Missing value for --cities argument")
                    }
                }
                arg.startsWith("--cities=") -> {
                    val parsed = arg.substringAfter("--cities=").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    cities = parsed.ifEmpty { null }
                }
                arg == "-d" || arg == "--days" -> {
                    if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                        val rawDays = args[++i]
                        val parsed = rawDays.toIntOrNull()
                        if (parsed == null || parsed !in 2..14) {
                            parsingErrors.add("Invalid --days value '$rawDays'. Must be an integer between 2 and 14 (minimum 2 days required for next-day forecast).")
                        } else {
                            days = parsed
                        }
                    } else {
                        parsingErrors.add("Missing value for --days argument")
                    }
                }
                arg.startsWith("--days=") -> {
                    val rawDays = arg.substringAfter("--days=")
                    val parsed = rawDays.toIntOrNull()
                    if (parsed == null || parsed !in 2..14) {
                        parsingErrors.add("Invalid --days value '$rawDays'. Must be an integer between 2 and 14 (minimum 2 days required for next-day forecast).")
                    } else {
                        days = parsed
                    }
                }
                arg == "--date" -> {
                    if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                        val rawDate = args[++i]
                        targetDate = validateIsoDate(rawDate, parsingErrors)
                    } else {
                        parsingErrors.add("Missing value for --date argument")
                    }
                }
                arg.startsWith("--date=") -> {
                    val rawDate = arg.substringAfter("--date=")
                    targetDate = validateIsoDate(rawDate, parsingErrors)
                }
                arg.startsWith("-") -> {
                    parsingErrors.add("Unrecognized option '$arg'. Use --help for available options.")
                }
                else -> {
                    parsingErrors.add("Unexpected positional argument '$arg'. Use --help for usage instructions.")
                }
            }
            i++
        }

        return CliArgs(
            apiKey = apiKey,
            cities = cities,
            days = days,
            targetDate = targetDate,
            isVerbose = isVerbose,
            isHelpRequested = isHelpRequested,
            parsingError = parsingErrors.joinToString("; ").takeIf { it.isNotEmpty() }
        )
    }

    private fun validateIsoDate(rawDate: String, errors: MutableList<String>): String? {
        return try {
            LocalDate.parse(rawDate)
            rawDate
        } catch (_: DateTimeParseException) {
            errors.add("Invalid --date format '$rawDate'. Must match ISO-8601 format YYYY-MM-DD (e.g. 2026-09-12).")
            null
        }
    }

    fun printHelp(out: java.io.PrintStream = System.out) {
        out.println(
            """
            WeatherAPI Next-Day Forecast CLI
            ================================
            Retrieves and displays the next-day weather forecast for:
            Chisinau, Madrid, Kyiv, and Amsterdam.

            USAGE:
              ./gradlew run --args="[OPTIONS]"
              java -jar weather-forecast.jar [OPTIONS]

            OPTIONS:
              -k, --api-key <KEY>     WeatherAPI API key (or use WEATHER_API_KEY environment variable)
              -c, --cities <CITIES>   Comma-separated list of cities (default: Chisinau, Madrid, Kyiv, Amsterdam)
              -d, --days <DAYS>       Number of forecast days to fetch (2 to 14, default: 3 for cross-timezone coverage)
                  --date <YYYY-MM-DD> Explicit target forecast date to display
              -v, --verbose           Enable verbose HTTP logging with sanitized secrets
              -h, --help              Show this help message and exit

            ENVIRONMENT VARIABLES:
              WEATHER_API_KEY         WeatherAPI API key
            """.trimIndent()
        )
    }
}
