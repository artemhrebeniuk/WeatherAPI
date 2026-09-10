# WeatherAPI Forecast CLI

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![JDK](https://img.shields.io/badge/JDK-21-ED8B00.svg?logo=openjdk&logoColor=white)](https://openjdk.org)
[![Gradle](https://img.shields.io/badge/Gradle-8.12-02303A.svg?logo=gradle&logoColor=white)](https://gradle.org)
[![Retrofit](https://img.shields.io/badge/Retrofit-2.11.0-4285F4.svg)](https://square.github.io/retrofit/)
[![CI](https://github.com/artemhrebeniuk/WeatherAPI/actions/workflows/ci.yml/badge.svg)](https://github.com/artemhrebeniuk/WeatherAPI/actions/workflows/ci.yml)

A production-grade command-line application that aggregates and outputs next-day meteorological forecasts for multiple cities (**Chisinau**, **Madrid**, **Kyiv**, and **Amsterdam**) using the [WeatherAPI.com](https://www.weatherapi.com/) service.

Results are printed to `STDOUT` as an auto-aligned, two-tier ASCII table with dates as columns and cities as rows, displaying minimum/maximum temperatures, average humidity, wind speed, and vector-averaged prevailing wind direction across timezone boundaries.

---

## Quick Start

### Prerequisites
- JDK 21+ installed and available on `PATH`.
- A valid API key from [WeatherAPI.com](https://www.weatherapi.com/).

### Running the Application

```bash
# Option 1: Pass API key directly via CLI flag
./gradlew run --args="--api-key=YOUR_API_KEY"

# Option 2: Export as an environment variable
export WEATHER_API_KEY="YOUR_API_KEY"
./gradlew run

# Option 3: Supply via JVM system property
./gradlew run -Dweather.api.key="YOUR_API_KEY"

# Option 4: Build and run the standalone executable Fat JAR
./gradlew jar
java -jar build/libs/weather-forecast.jar --api-key="YOUR_API_KEY"
```

### Sample Output

```text
+-----------+----------------------------------------------------------------------------------+
|   City    |                                    2026-09-11                                    |
+-----------+---------------+---------------+--------------+------------------+----------------+
|           | Min Temp (°C) | Max Temp (°C) | Humidity (%) | Wind Speed (kph) | Wind Direction |
+-----------+---------------+---------------+--------------+------------------+----------------+
| Chisinau  |     14.8      |     26.9      |     57%      |       21.6       |       N        |
| Madrid    |     17.1      |     27.6      |     16%      |       11.5       |      ENE       |
| Kyiv      |     12.5      |     21.4      |     51%      |       16.9       |      NNW       |
| Amsterdam |     12.4      |     21.1      |     78%      |       14.8       |      WSW       |
+-----------+---------------+---------------+--------------+------------------+----------------+
```

---

## CLI Reference & Configuration

```text
Usage: weather-forecast-cli [OPTIONS]

Options:
  -k, --api-key <KEY>     WeatherAPI.com API key (or set via WEATHER_API_KEY env var)
  -c, --cities <LIST>     Comma-separated list of target cities (default: Chisinau,Madrid,Kyiv,Amsterdam)
  -d, --days <DAYS>       Forecast query depth between 2 and 14 days (default: 3)
      --date <YYYY-MM-DD> Explicit target forecast date to display
  -v, --verbose           Enable verbose HTTP logging with sanitized secrets
  -V, --version           Show application version and exit
  -h, --help              Show usage help and exit
```

### Configuration Precedence
The application resolves the API key in the following cascade order:
1. Explicit CLI argument (`--api-key` or `-k`)
2. Environment variable (`WEATHER_API_KEY`)
3. JVM System property (`-Dweather.api.key`)

### Practical Examples

```bash
# Query a custom list of metropolitan areas
./gradlew run --args="--api-key=YOUR_API_KEY --cities='Berlin,Paris,Rome,Vienna'"

# Request a specific calendar date across all target cities
./gradlew run --args="--api-key=YOUR_API_KEY --date=2026-09-12"

# Run with verbose HTTP network logging (API key is automatically sanitized)
./gradlew run --args="--api-key=YOUR_API_KEY -v"

# Combine custom cities with explicit date
./gradlew run --args="--api-key=YOUR_API_KEY --cities='Tokyo,Sydney,New York' --days=4"
```

---

## Architecture & Design Decisions

The codebase follows **Clean Architecture** principles, enforcing strict unidirectional dependency flow and zero framework leakage into the domain core.

```
                  ┌────────────────────────┐
                  │   Presentation Layer   │
                  │ (CliParser, Table UI)  │
                  └───────────┬────────────┘
                              │
                              ▼
                  ┌────────────────────────┐
                  │      Domain Layer      │
                  │ (Models, Use Cases,    │
                  │  Repository Contract,  │
                  │  Wind Vector Service)  │
                  └───────────▲────────────┘
                              │
                  ┌───────────┴────────────┐
                  │       Data Layer       │
                  │ (Retrofit, OkHttp,     │
                  │  DTOs, RepositoryImpl, │
                  │  ApiKeyInterceptor)    │
                  └────────────────────────┘
```

### Key Engineering Decisions

- **Circular Vector Averaging for Wind Direction:**  
  WeatherAPI provides daily aggregate metrics for temperature and humidity, but omits aggregate wind direction from the daily `day` payload (it is only available in 24 hourly readings). Rather than picking an arbitrary timestamp, `WindDirectionCalculator` decomposes the 24 hourly azimuth angles into Cartesian vectors:
  ```
  x = Σ(cos(θᵢ) · wᵢ),  y = Σ(sin(θᵢ) · wᵢ)  where wᵢ = max(windKphᵢ, 0.1)
  ```
  The resultant angle `atan2(y, x)` correctly resolves circular continuity across North (e.g., 350° and 10° yield 0° North, rather than 180° South). Singularity conditions (`hypot(x, y) < 10⁻⁵`) deterministically fall back to statistical compass mode.

- **Cross-Midnight Timezone Handling:**  
  Target cities span UTC+1 to UTC+3. When invoked near midnight, calendar dates differ across cities. The application queries 3 days (`days=3`), resolves each location's local time from `location.localtime`, and computes its respective tomorrow. If cities resolve to distinct calendar dates, the table dynamically renders multiple date column blocks with sparse cell indicators (`-`).

- **Structured Concurrency & Fault Tolerance:**  
  City queries are dispatched concurrently using Kotlin Coroutines (`async` inside `supervisorScope`) throttled by a `Semaphore(8)` to safeguard network pools and rate limits. If one city encounters an outage or 404, it does not cancel sibling requests: the failure is emitted to `STDERR`, while valid forecasts are cleanly formatted in `STDOUT`.

- **Resource Lifecycle Management:**  
  OkHttp's connection pool and daemon thread executors are explicitly cleared in a `finally` block upon completion, preventing lingering worker threads from blocking process termination.

---

## Project Structure

```
WeatherAPI/
├── .github/workflows/ci.yml       # GitHub Actions automated CI workflow
├── build.gradle.kts               # Dependencies, JVM toolchain, task configurations
├── settings.gradle.kts            # Project settings
├── gradlew, gradlew.bat           # Standalone Gradle Wrapper
├── README.md                      # Documentation
└── src/
    ├── main/kotlin/com/weatherapi/forecast/
    │   ├── Application.kt         # Entry point, dependency wiring, exit handling
    │   ├── common/
    │   │   ├── config/            # AppConfig and key resolution cascade
    │   │   └── error/             # Strongly typed WeatherError hierarchy
    │   ├── domain/
    │   │   ├── model/             # Value objects with validation contracts (Temperature, Wind, etc.)
    │   │   ├── repository/        # WeatherRepository interface contract
    │   │   ├── service/           # WindDirectionCalculator circular statistics service
    │   │   └── usecase/           # GetForecastUseCase concurrent orchestrator
    │   ├── data/
    │   │   ├── mapper/            # DTO-to-Domain mappers with sanity clamping
    │   │   ├── remote/
    │   │   │   ├── api/           # Retrofit WeatherApiService declaration
    │   │   │   ├── dto/           # Kotlinx Serialization DTO models
    │   │   │   └── interceptor/   # ApiKeyInterceptor query injector
    │   │   └── repository/        # WeatherRepositoryImpl with structured error mapping
    │   └── presentation/
    │       ├── cli/               # CliParser POSIX-style argument handler
    │       ├── formatter/         # FormatForecastTableUseCase presenter
    │       └── table/             # AsciiTableFormatter two-tier rendering engine
    └── test/kotlin/com/weatherapi/forecast/
        ├── common/                # AppConfig resolution and JVM isolation tests
        ├── domain/                # Unit tests for domain services, models, and use cases
        ├── data/                  # MockWebServer integration suite & Live E2E tests
        └── presentation/          # Table layout, alignment, and CLI parser tests
```

---

## Testing & Quality Assurance

The test suite covers domain logic, mathematical edge cases, error resilience, integration contracts via MockWebServer, and live execution.

### Test Categories

- **Domain & Mathematical Verification:**  
  Parameterized tests for all 16 compass sectors, circular North wrapping, opposing wind singularities, IEEE 754 precision thresholds, and out-of-range value rejections.
- **Integration Tests (MockWebServer):**  
  Hermetic HTTP server tests validating real network interactions, URL parameter injection, HTTP 401/403 (invalid or disabled key), HTTP 400 (location not found), HTTP 429 (monthly quota exceeded), malformed JSON payloads, socket timeouts, and coroutine cancellation preservation.
- **Table Formatting & Edge Cases:**  
  Monospace alignment validation, sub-zero negative temperatures, sparse cell handling, and empty data guards.
- **Live E2E Verification:**  
  Real network validation against `api.weatherapi.com` (automatically executed when credentials are provided, safely skipped in offline CI environments via JUnit 5 assumptions).

### Running Tests

```bash
# Run the complete test suite
./gradlew test

# Run tests with HTML report generation
./gradlew check

# Run the live E2E integration test against the real API
./gradlew test -Dweather.api.key="YOUR_API_KEY"
```

Test reports are generated at `build/reports/tests/test/index.html`.

---

## Continuous Integration

Every push and pull request is automatically verified via GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)):
- Matrix build on Ubuntu with OpenJDK 21
- Gradle check & automated test execution
- Verification of packaging and CLI execution
