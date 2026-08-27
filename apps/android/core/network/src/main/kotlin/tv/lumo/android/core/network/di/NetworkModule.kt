package tv.lumo.android.core.network.di

import android.util.Log
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import tv.lumo.android.core.auth.TokenRefresher
import tv.lumo.android.core.network.BuildConfig
import tv.lumo.android.core.network.auth.AuthInterceptor
import tv.lumo.android.core.network.auth.RetrofitTokenRefresher
import tv.lumo.android.core.network.auth.TokenAuthenticator
import tv.lumo.android.network.generated.api.AccountApi
import tv.lumo.android.network.generated.api.AuthApi
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.api.SourcesApi
import tv.lumo.android.network.generated.api.UserdataApi
import tv.lumo.android.network.generated.infrastructure.Serializer

/**
 * The HTTP stack, in two layers.
 *
 * The [Unauthenticated] client is the base: timeouts, logging, connection pool.
 * The authenticated client is derived from it with `newBuilder()`, which means
 * both share one connection pool and one dispatcher — two independent clients
 * would double the socket count for no benefit.
 *
 * Refresh goes out on the unauthenticated one. That is not a detail: it is what
 * makes a 401-on-refresh terminate instead of recursing.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun moshi(): Moshi =
        // The generated Serializer already registers the adapters the contract
        // needs (UUID, OffsetDateTime, URI, byte[]). Building our own would
        // silently drop them and fail at runtime on the first date field.
        Serializer.moshiBuilder.build()

    @Provides
    @Singleton
    @Unauthenticated
    fun baseClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(BuildConfig.API_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .readTimeout(BuildConfig.API_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .writeTimeout(BuildConfig.API_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(debugLogging())
        }
        .build()

    @Provides
    @Singleton
    fun authenticatedClient(
        @Unauthenticated base: OkHttpClient,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    @Unauthenticated
    fun unauthenticatedRetrofit(
        @Unauthenticated client: OkHttpClient,
        moshi: Moshi,
    ): Retrofit = retrofit(client, moshi)

    @Provides
    @Singleton
    fun authenticatedRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
        retrofit(client, moshi)

    @Provides
    @Singleton
    @Unauthenticated
    fun refreshAuthApi(@Unauthenticated retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun sourcesApi(retrofit: Retrofit): SourcesApi = retrofit.create(SourcesApi::class.java)

    @Provides
    @Singleton
    fun catalogApi(retrofit: Retrofit): CatalogApi = retrofit.create(CatalogApi::class.java)

    @Provides
    @Singleton
    fun accountApi(retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)

    @Provides
    @Singleton
    fun userdataApi(retrofit: Retrofit): UserdataApi = retrofit.create(UserdataApi::class.java)

    private fun retrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        // Retrofit requires a trailing slash, and drops the last path segment
        // without one — `…/v1` would silently become `…/`, so every call would
        // 404 against a correct-looking base URL.
        .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    /**
     * Debug builds only, and never at BODY level.
     *
     * AGENTS.md §5 forbids a token, an Xtream password or a stream URL in the
     * logs at any level. BODY logging would print the sign-in request, the
     * token pair and `PlaybackInfo.stream_url` in full — and logcat is readable
     * over `adb` by anything with USB access.
     *
     * Note this is also why the generated `ApiClient` is not used to build the
     * Retrofit instance: it installs a BODY-level logger of its own.
     */
    private fun debugLogging(): HttpLoggingInterceptor =
        HttpLoggingInterceptor { message -> Log.d("LumoHttp", message) }
            .apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
                redactHeader("Cookie")
                // BASIC logs the request line, which means the full URL, query
                // string included — and the contract puts single-use secrets
                // there: `GET /auth/verify-email?token=…`, and the activation
                // code on the links the television shows. Redacting the headers
                // and leaving the query readable would have been a half-measure
                // (AGENTS.md §5).
                redactQueryParams("token", "code", "user_code")
            }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {

    @Binds
    @Singleton
    abstract fun tokenRefresher(impl: RetrofitTokenRefresher): TokenRefresher
}
