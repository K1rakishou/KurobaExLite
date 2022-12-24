package com.github.k1rakishou.kurobaexlite.helpers.network.http_client

import okhttp3.OkHttpClient

interface IKurobaOkHttpClient {
  fun okHttpClient(): OkHttpClient
}