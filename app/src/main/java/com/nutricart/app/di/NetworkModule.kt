package com.nutricart.app.di

import com.nutricart.app.cloud.CloudConfig
import com.nutricart.app.cloud.SupabaseAuthApi
import com.nutricart.app.cloud.SupabaseRestApi
import com.nutricart.app.data.remote.ClaudeApi
import com.nutricart.app.data.remote.OpenFoodFactsApi
import com.nutricart.app.data.remote.TelegramApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
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

    /**
     * The optional AI assistant talks to a different host with different
     * rules, so it builds its OWN client and Json INSIDE this provider and
     * exposes only the interface. Providing a second OkHttpClient, Retrofit or
     * Json to Hilt would be a duplicate-binding compile error, and introducing
     * the project's first @Qualifier for one service is not worth it.
     *
     * Two things the shared ones would get wrong:
     *  - OkHttp's ~10 s read timeout is far below a normal model response;
     *  - the shared Json leaves encodeDefaults off, so any request field left
     *    at its Kotlin default would silently vanish from the body and the
     *    server would answer 400.
     */
    @Provides
    @Singleton
    fun provideClaudeApi(): ClaudeApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        return Retrofit.Builder()
            .baseUrl(ClaudeApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ClaudeApi::class.java)
    }

    /**
     * Telegram Bot API for the partner feature. Its own client for the same
     * reason as the AI assistant above: a different host with its own rules,
     * and no wish to introduce qualifiers for one more service. The bot token
     * is a per-call path parameter (see TelegramApi), so nothing here caches
     * a secret.
     */
    @Provides
    @Singleton
    fun provideTelegramApi(): TelegramApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
        val json = Json {
            ignoreUnknownKeys = true // Telegram objects carry dozens of fields we don't map
            isLenient = true
            coerceInputValues = true
        }
        return Retrofit.Builder()
            .baseUrl(TelegramApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TelegramApi::class.java)
    }

    /**
     * Supabase: one Retrofit for both Auth and PostgREST, since they share a
     * host and the `apikey` header. Not a @Provides of its own: the module
     * already provides a Retrofit for Open Food Facts, and a second binding of
     * the same type would be a duplicate-binding compile error. Built even
     * when the app has no keys (the base URL is then a placeholder) so Hilt's
     * graph is the same in every build; CloudConfig.isConfigured gates every
     * call site.
     */
    private val supabaseRetrofit: Retrofit by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("apikey", CloudConfig.anonKey)
                    .build()
                chain.proceed(request)
            }
            .build()
        val json = Json {
            ignoreUnknownKeys = true // PostgREST rows and GoTrue sessions carry more than we map
            isLenient = true
            coerceInputValues = true
        }
        Retrofit.Builder()
            .baseUrl(CloudConfig.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideSupabaseAuthApi(): SupabaseAuthApi =
        supabaseRetrofit.create(SupabaseAuthApi::class.java)

    @Provides
    @Singleton
    fun provideSupabaseRestApi(): SupabaseRestApi =
        supabaseRetrofit.create(SupabaseRestApi::class.java)
}
