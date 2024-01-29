package com.github.k1rakishou.kurobaexlite.helpers.network.http_client

import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.network.CloudFlareInterceptor
import com.github.k1rakishou.kurobaexlite.managers.FirewallBypassManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ProxiedOkHttpClient(
  private val context: Context,
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
          val loggingInterceptor = KurobaHttpLoggingInterceptor(context)
          loggingInterceptor.setLevel(KurobaHttpLoggingInterceptor.Level.BODY)
          builder.addNetworkInterceptor(loggingInterceptor)
        }
      }
      .build()
  }

  override fun okHttpClient(): OkHttpClient {
    return okHttpClient
  }

  companion object {
    // TODO: don't forget to change me back to false!!!
    private const val loggingInterceptorEnabled = false
  }

}