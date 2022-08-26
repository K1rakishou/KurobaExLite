package com.github.k1rakishou.kurobaexlite.helpers.network.http_client

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class ProxiedOkHttpClient : KurobaOkHttpClient {
  private val okHttpClient by lazy {
    OkHttpClient.Builder()
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .connectTimeout(20, TimeUnit.SECONDS)
      .addNetworkInterceptor(GzipInterceptor())
      .build()
  }

  override fun okHttpClient(): OkHttpClient {
    return okHttpClient
  }

}