package com.github.k1rakishou.kurobaexlite.features.firewall

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.kurobaexlite.helpers.network.CloudFlareInterceptor
import com.github.k1rakishou.kurobaexlite.helpers.util.domain
import com.github.k1rakishou.kurobaexlite.model.BypassException
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class CloudFlareCheckBypassWebClient(
  private val originalRequestUrlHost: String,
  private val cookieManager: CookieManager,
  bypassResultCompletableDeferred: CompletableDeferred<BypassResult>
) : BypassWebClient(bypassResultCompletableDeferred) {
  private var pageLoadsCounter = 0

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)

    if (url.isNullOrEmpty()) {
      return
    }

    val domainOrHost = url.toHttpUrlOrNull()
      ?.let { httpUrl -> httpUrl.domain() ?: httpUrl.host }
      ?: return

    val cookie = cookieManager.getCookie(originalRequestUrlHost)
    if (cookie.isNullOrEmpty() || !cookie.contains(CloudFlareInterceptor.CF_CLEARANCE)) {
      ++pageLoadsCounter

      if (pageLoadsCounter > BypassWebClient.MAX_PAGE_LOADS_COUNT) {
        fail(BypassException("Exceeded max page load limit"))
      }

      return
    }

    val actualCookie = cookie
      .split(";")
      .map { cookiePart -> cookiePart.trim() }
      .firstOrNull { cookiePart -> cookiePart.startsWith(CloudFlareInterceptor.CF_CLEARANCE) }
      ?.removePrefix("${CloudFlareInterceptor.CF_CLEARANCE}=")

    if (actualCookie == null) {
      fail(BypassException("No cf_clearance cookie found in result"))
      return
    }

    success(domainOrHost, actualCookie)
  }

  @Deprecated("Deprecated in Java")
  override fun onReceivedError(
    view: WebView?,
    errorCode: Int,
    description: String?,
    failingUrl: String?
  ) {
    super.onReceivedError(view, errorCode, description, failingUrl)

    val error = description ?: "Unknown error while trying to load CloudFlare page"
    fail(BypassException(error))
  }

}