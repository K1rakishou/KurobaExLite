package com.github.k1rakishou.kurobaexlite.helpers.network.http_client

import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.network.CloudFlareInterceptor
import com.github.k1rakishou.kurobaexlite.managers.FirewallBypassManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class ProxiedOkHttpClient(
  private val siteManager: SiteManager,
  private val firewallBypassManager: FirewallBypassManager,
  private val androidHelpers: AndroidHelpers
) : IKurobaOkHttpClient {

  private val okHttpClient by lazy {
    OkHttpClient.Builder()
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .connectTimeout(20, TimeUnit.SECONDS)
      .addNetworkInterceptor(CloudFlareInterceptor(siteManager, firewallBypassManager, "ProxiedOkHttpClient"))
      .addNetworkInterceptor(GzipInterceptor())
      .also { builder ->
        if (androidHelpers.isDevFlavor() && loggingInterceptorEnabled) {
          val loggingInterceptor = HttpLoggingInterceptor()
          loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
          builder.addNetworkInterceptor(loggingInterceptor)
        }
      }
      .build()
  }

  override fun okHttpClient(): OkHttpClient {
    return okHttpClient
  }

  companion object {
    private const val loggingInterceptorEnabled = false
  }

}