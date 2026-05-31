package com.example.network

import com.example.data.DiscordWebhookRequest
import com.example.data.StreamElementsTipRequest
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface StreamElementsApi {
    @POST("v2/tips/{channelId}")
    suspend fun sendTip(
        @Path("channelId") channelId: String,
        @Header("Authorization") bearerToken: String,
        @Body request: StreamElementsTipRequest
    ): Response<ResponseBody>
}

interface DiscordApi {
    @POST
    suspend fun sendWebhook(
        @Url url: String,
        @Body request: DiscordWebhookRequest
    ): Response<ResponseBody>
}

object NetworkClient {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofitBuilder = Retrofit.Builder()
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())

    val streamElementsApi: StreamElementsApi by lazy {
        retrofitBuilder
            .baseUrl("https://api.streamelements.com/")
            .build()
            .create(StreamElementsApi::class.java)
    }

    val discordApi: DiscordApi by lazy {
        retrofitBuilder
            .baseUrl("https://discord.com/api/") // Fallback base URL for url parameter annotation
            .build()
            .create(DiscordApi::class.java)
    }
}
