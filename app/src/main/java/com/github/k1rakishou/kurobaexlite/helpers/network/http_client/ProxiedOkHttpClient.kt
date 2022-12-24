package com.github.k1rakishou.kurobaexlite.helpers.network.http_client

import com.github.k1rakishou.kurobaexlite.helpers.network.CloudFlareInterceptor
import com.github.k1rakishou.kurobaexlite.managers.FirewallBypassManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ProxiedOkHttpClient(
  private val siteManager: SiteManager,
  private val firewallBypassManager: FirewallBypassManager
) : IKurobaOkHttpClient {

  private val okHttpClient by lazy {
    OkHttpClient.Builder()
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .connectTimeout(20, TimeUnit.SECONDS)
      .addNetworkInterceptor(CloudFlareInterceptor(siteManager, firewallBypassManager, "ProxiedOkHttpClient"))
      .addNetworkInterceptor(GzipInterceptor())
      .build()
  }

  override fun okHttpClient(): OkHttpClient {
    return okHttpClient
  }

}