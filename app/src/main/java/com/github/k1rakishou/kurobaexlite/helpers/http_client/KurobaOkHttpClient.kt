package com.github.k1rakishou.kurobaexlite.helpers.http_client

import okhttp3.OkHttpClient

interface KurobaOkHttpClient {
  fun okHttpClient(): OkHttpClient
}