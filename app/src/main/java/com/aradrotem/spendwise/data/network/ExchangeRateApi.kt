package com.aradrotem.spendwise.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Frankfurter (https://frankfurter.dev) - a free, reputable exchange-rate API built on the
// European Central Bank's published reference rates. Chosen specifically because it requires no
// API key/account and has no private secret to keep out of the APK, which fits this academic
// project's constraint of never embedding a paid credential in the client.
data class FrankfurterRateResponse(
    val amount: Double,
    val base: String,
    val date: String,
    val rates: Map<String, Double>
)

interface ExchangeRateApi {
    @GET("latest")
    suspend fun getLatestRate(@Query("from") from: String, @Query("to") to: String): FrankfurterRateResponse
}

object ExchangeRateApiFactory {
    private const val BASE_URL = "https://api.frankfurter.app/"

    fun create(): ExchangeRateApi {
        val client = OkHttpClient.Builder().build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)
    }
}
