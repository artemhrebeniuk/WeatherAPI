package com.weatherapi.forecast.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that automatically injects the WeatherAPI key
 * as a query parameter into every outgoing HTTP request.
 */
class ApiKeyInterceptor(
    private val apiKey: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val authenticatedUrl = originalUrl.newBuilder()
            .setQueryParameter("key", apiKey)
            .build()

        val authenticatedRequest = originalRequest.newBuilder()
            .url(authenticatedUrl)
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
