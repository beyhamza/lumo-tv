package tv.lumo.android.core.network.di

import javax.inject.Qualifier

/**
 * The client and Retrofit instance that carry no `Authorization` header and no
 * 401 handling.
 *
 * Refresh has to go out on this one. If the refresh call travelled through the
 * authenticated client, a 401 on refresh would trigger a refresh, which would
 * 401, forever — and the request would carry the very access token whose
 * expiry we are trying to fix.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Unauthenticated
