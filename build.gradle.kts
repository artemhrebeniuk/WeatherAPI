plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "com.weatherapi.forecast"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin standard library & Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Retrofit 2 & OkHttp 3/4
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.weatherapi.forecast.ApplicationKt")
}

tasks.withType<JavaExec> {
    System.getProperty("weather.api.key")?.let { key ->
        systemProperty("weather.api.key", key)
    }
}

tasks.test {
    useJUnitPlatform()
    System.getProperty("weather.api.key")?.let { key ->
        systemProperty("weather.api.key", key)
    }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles an executable fat JAR archive containing all dependencies."
    archiveBaseName.set("weather-forecast")
    archiveClassifier.set("")
    archiveVersion.set("")

    manifest {
        attributes["Main-Class"] = "com.weatherapi.forecast.ApplicationKt"
        attributes["Implementation-Title"] = "WeatherAPI Forecast CLI"
        attributes["Implementation-Version"] = project.version
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("weather-forecast")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.weatherapi.forecast.ApplicationKt"
        attributes["Implementation-Title"] = "WeatherAPI Forecast CLI"
        attributes["Implementation-Version"] = project.version
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

