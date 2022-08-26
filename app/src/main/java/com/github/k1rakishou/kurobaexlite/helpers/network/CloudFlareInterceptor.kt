package com.github.k1rakishou.kurobaexlite.helpers.network

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.util.containsPattern
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.FirewallBypassManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.FirewallDetectedException
import com.github.k1rakishou.kurobaexlite.model.FirewallType
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

class CloudFlareInterceptor(
  private val siteManager: SiteManager,
  private val firewallBypassManager: FirewallBypassManager,
  private val okHttpType: String
) : Interceptor {
  @GuardedBy("this")
  private val sitesThatRequireCloudFlareCache = mutableSetOf<String>()

  @GuardedBy("this")
  private val requestsWithAddedCookie = mutableMapOf<String, Request>()

  override fun intercept(chain: Interceptor.Chain): Response {
    var request = chain.request()
    var addedCookie = false
    val host = request.url.host

    if (requireCloudFlareCookie(request)) {
      val updatedRequest = addCloudFlareCookie(chain.request())
      if (updatedRequest != null) {
        request = updatedRequest
        addedCookie = true

        synchronized(this) {
          if (!requestsWithAddedCookie.containsKey(host)) {
            requestsWithAddedCookie[host] = request
          }
        }
      }
    }

    val response = chain.proceed(request)

    if (response.code == 503 || response.code == 403) {
      processCloudflareRejectedRequest(
        response = response,
        host = host,
        addedCookie = addedCookie,
        chain = chain,
        request = request
      )
    } else {
      synchronized(this) { requestsWithAddedCookie.remove(host) }
    }

    return response
  }

  private fun processCloudflareRejectedRequest(
    response: Response,
    host: String,
    addedCookie: Boolean,
    chain: Interceptor.Chain,
    request: Request
  ) {
    val body = response.body
    if (body == null || !tryDetectCloudFlareNeedle(body)) {
      return
    }

    logcat(LogPriority.VERBOSE, TAG) {
      "[$okHttpType] Found CloudFlare needle in the page's body"
    }

    // To avoid race conditions which could result in us ending up in a situation where a request
    // with an old cookie or no cookie at all causing us to remove the old cookie from the site
    // settings.
    val isExpectedRequestWithCookie = synchronized(this) { requestsWithAddedCookie[host] === request }
    if (addedCookie && isExpectedRequestWithCookie) {
      // For some reason CloudFlare still rejected our request even though we added the cookie.
      // This may happen because of many reasons like the cookie expired or it was somehow
      // damaged so we need to delete it and re-request again.
      logcat(LogPriority.VERBOSE, TAG) {
        "[$okHttpType] Cookie was already added and we still failed, removing the old cookie"
      }

      removeSiteClearanceCookie(chain.request())
      synchronized(this) { requestsWithAddedCookie.remove(host) }
    }

    synchronized(this) { sitesThatRequireCloudFlareCache.add(host) }

    if (request.method.equals("GET", ignoreCase = true)) {
      val site = siteManager.byUrl(request.url)
      if (site != null) {
        val siteKey = site.siteKey

        val requestUrl = site
          .firewallChallengeEndpoint
          ?: request.url

        firewallBypassManager.onFirewallDetected(
          firewallType = FirewallType.Cloudflare,
          siteKey = siteKey,
          urlToOpen = requestUrl
        )
      }
    }

    // We only want to throw this exception when loading a site's thread endpoint. In any other
    // case (like when opening media files on that site) we only want to add the CloudFlare
    // CfClearance cookie to the headers.
    throw FirewallDetectedException(
      firewallType = FirewallType.Cloudflare,
      requestUrl = request.url
    )
  }

  private fun requireCloudFlareCookie(request: Request): Boolean {
    val host = request.url.host

    val alreadyCheckedSite = synchronized(this) { host in sitesThatRequireCloudFlareCache }
    if (alreadyCheckedSite) {
      return true
    }

    val site = siteManager.byUrl(request.url)
    if (site == null) {
      return false
    }

    val cloudFlareClearanceCookieSetting = site.siteSettings.cloudFlareClearanceCookie
    val cloudFlareClearanceCookie = runBlocking { cloudFlareClearanceCookieSetting.read() }
    return cloudFlareClearanceCookie.isNotEmpty()
  }

  private fun removeSiteClearanceCookie(request: Request) {
    val site = siteManager.byUrl(request.url)
    if (site == null) {
      return
    }

    val cloudFlareClearanceCookieSetting = site.siteSettings.cloudFlareClearanceCookie

    val prevValue = runBlocking { cloudFlareClearanceCookieSetting.read() }
    if (prevValue.isEmpty()) {
      logcatError(TAG) { "[$okHttpType] removeSiteClearanceCookie() cookieValue is empty" }
      return
    }

    runBlocking { cloudFlareClearanceCookieSetting.write("") }
  }

  private fun addCloudFlareCookie(prevRequest: Request): Request? {
    val site = siteManager.byUrl(prevRequest.url)
    if (site == null) {
      return null
    }

    val cloudFlareClearanceCookieSetting = site.siteSettings.cloudFlareClearanceCookie
    val cloudFlareClearanceCookie = runBlocking { cloudFlareClearanceCookieSetting.read() }
    if (cloudFlareClearanceCookie.isEmpty()) {
      logcatError(TAG) { "[$okHttpType] addCloudFlareCookie() cookieValue is empty" }
      return null
    }

    return prevRequest.newBuilder()
      .addHeader("Cookie", "$CF_CLEARANCE=$cloudFlareClearanceCookie")
      .build()
  }

  private fun tryDetectCloudFlareNeedle(responseBody: ResponseBody): Boolean {
    return responseBody.use { body ->
      return@use body.byteStream().use { inputStream ->
        val bytes = ByteArray(READ_BYTES_COUNT) { 0x00 }
        val read = inputStream.read(bytes)
        if (read <= 0) {
          return@use false
        }

        return@use cloudflareNeedles.any { needle -> bytes.containsPattern(0, needle) }
      }
    }
  }

  companion object {
    private const val TAG = "CloudFlareHandlerInterceptor"
    private const val READ_BYTES_COUNT = 24 * 1024 // 24KB

    const val CF_CLEARANCE = "cf_clearance"

    private val CLOUD_FLARE_NEEDLE1 = "<title>Please Wait... | Cloudflare</title>".toByteArray(StandardCharsets.UTF_8)
    private val CLOUD_FLARE_NEEDLE2 = "Checking your browser before accessing".toByteArray(StandardCharsets.UTF_8)
    private val CLOUD_FLARE_NEEDLE3 = "<title>Just a moment...</title>".toByteArray(StandardCharsets.UTF_8)
    private val CLOUD_FLARE_NEEDLE4 = "Browser Integrity Check".toByteArray(StandardCharsets.UTF_8)

    private val cloudflareNeedles = arrayOf(
      CLOUD_FLARE_NEEDLE1,
      CLOUD_FLARE_NEEDLE2,
      CLOUD_FLARE_NEEDLE3,
      CLOUD_FLARE_NEEDLE4
    )
  }
}