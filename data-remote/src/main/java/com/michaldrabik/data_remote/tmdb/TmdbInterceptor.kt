package com.michaldrabik.data_remote.tmdb

import com.michaldrabik.data_remote.credentials.RuntimeCredentialsStore
import okhttp3.Interceptor
import okhttp3.Response

class TmdbInterceptor(
  private val runtimeCredentials: RuntimeCredentialsStore,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain
      .request()
      .newBuilder()
      .header("Content-Type", "application/json")
      .header("Authorization", "Bearer ${runtimeCredentials.tmdbReadAccessToken}")
      .build()

    return chain.proceed(request)
  }
}
