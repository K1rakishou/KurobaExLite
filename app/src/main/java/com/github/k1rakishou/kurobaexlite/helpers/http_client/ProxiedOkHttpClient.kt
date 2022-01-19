package com.github.k1rakishou.kurobaexlite.helpers.http_client

import okhttp3.OkHttpClient

class ProxiedOkHttpClient : KurobaOkHttpClient {
  private val okHttpClient by lazy { OkHttpClient.Builder().build() }

  override fun okHttpClient(): OkHttpClient {
    return okHttpClient
  }

}