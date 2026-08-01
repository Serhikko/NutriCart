package com.nutricart.app.di

import com.nutricart.app.data.remote.OpenFoodFactsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // OFF responses carry hundreds of fields we don't map
        isLenient = true         // OFF sometimes sends numbers as strings ("12.5")
        coerceInputValues = true // bad values fall back to our defaults instead of crashing
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Open Food Facts policy: identify your app with a descriptive
                // User-Agent, or requests may be throttled.
                // No version number on purpose — it would silently go stale on
                // every release; OFF only asks for an app name + contact.
                val request = chain.request().newBuilder()
                    .header("User-Agent", "NutriCart (https://github.com/Serhikko/NutriCart)")
                    .build()
                chain.proceed(request)
            }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(OpenFoodFactsApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(retrofit: Retrofit): OpenFoodFactsApi =
        retrofit.create(OpenFoodFactsApi::class.java)
}
