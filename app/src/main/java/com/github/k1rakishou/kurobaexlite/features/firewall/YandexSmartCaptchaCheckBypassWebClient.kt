package com.github.k1rakishou.kurobaexlite.features.firewall

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.kurobaexlite.helpers.util.domain
import com.github.k1rakishou.kurobaexlite.model.BypassException
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class YandexSmartCaptchaCheckBypassWebClient(
  private val originalRequestUrlHost: String,
  private val cookieManager: CookieManager,
  bypassResultCompletableDeferred: CompletableDeferred<BypassResult>
) : BypassWebClient(bypassResultCompletableDeferred) {
  private var pageLoadsCounter = 0

  private var captchaPageLoaded = false

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)

    if (url.isNullOrEmpty()) {
      return
    }

    val domainOrHost = url.toHttpUrlOrNull()
      ?.let { httpUrl -> httpUrl.domain() ?: httpUrl.host }
      ?: return

    val cookie = cookieManager.getCookie(originalRequestUrlHost)

    if (url.contains("https://yandex.com/showcaptcha")) {
      captchaPageLoaded = true
    }

    if (captchaPageLoaded && url.contains("https://yandex.com/images/")) {
      success(domainOrHost, cookie)
      return
    }

    ++pageLoadsCounter

    if (pageLoadsCounter > MAX_PAGE_LOADS_COUNT) {
      fail(BypassException("Exceeded max page load limit"))
    }
  }

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