package com.fadghost.notesapp.data.di

import com.fadghost.notesapp.data.ai.KeystoreCrypto
import com.fadghost.notesapp.data.ai.net.OpenRouterClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * AI infrastructure wiring (PLAN.md §5). Provides the shared Ktor/OkHttp client,
 * a lenient Json, the Keystore crypto helper and the [OpenRouterClient].
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        engine {
            config {
                connectTimeout(20, TimeUnit.SECONDS)
                // Long read window so a slow streamed completion is not cut mid-flight.
                readTimeout(90, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
        }
        install(ContentNegotiation) { json(json) }
    }

    @Provides
    @Singleton
    fun provideKeystoreCrypto(): KeystoreCrypto = KeystoreCrypto()

    @Provides
    @Singleton
    fun provideOpenRouterClient(http: HttpClient, json: Json): OpenRouterClient =
        OpenRouterClient(http, json)
}
