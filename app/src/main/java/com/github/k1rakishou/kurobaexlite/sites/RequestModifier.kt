package com.github.k1rakishou.kurobaexlite.sites

import androidx.annotation.CallSuper
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.helpers.network.CloudFlareInterceptor
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.appendCookieHeader
import com.github.k1rakishou.kurobaexlite.helpers.util.domain
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.sites.chan4.Chan4
import okhttp3.HttpUrl
import okhttp3.Request

open class RequestModifier<T : Site>(
  protected val site: T,
  protected val appSettings: AppSettings
) {

  @CallSuper
  open suspend fun modifyReplyRequest(requestBuilder: Request.Builder) {
    addCloudflareClearanceCookie(requestBuilder)

    if (site is Chan4) {
      requestBuilder.addHeader(userAgentHeaderKey, appSettings.specialUserAgentFor4chanPosting)
    } else {
      requestBuilder.addHeader(userAgentHeaderKey, appSettings.userAgent.read())
    }

    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open suspend fun modifyCaptchaGetRequest(requestBuilder: Request.Builder) {
    addCloudflareClearanceCookie(requestBuilder)
    requestBuilder.addHeader(userAgentHeaderKey, appSettings.userAgent.read())
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open suspend fun modifyCatalogOrThreadGetRequest(
    chanDescriptor: ChanDescriptor,
    requestBuilder: Request.Builder
  ) {
    addCloudflareClearanceCookie(requestBuilder)
    requestBuilder.addHeader(userAgentHeaderKey, appSettings.userAgent.read())
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open suspend fun modifyGetMediaRequest(requestBuilder: Request.Builder) {
    addCloudflareClearanceCookie(requestBuilder)
    requestBuilder.addHeader(userAgentHeaderKey, appSettings.userAgent.read())
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open suspend fun modifyCoilImageRequest(requestUrl: HttpUrl, imageRequestBuilder: ImageRequest.Builder) {
    addCloudflareClearanceCookie(requestUrl, imageRequestBuilder)
    imageRequestBuilder.addHeader(userAgentHeaderKey, appSettings.userAgent.read())
    imageRequestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  private suspend fun addCloudflareClearanceCookie(
    requestBuilder: Request.Builder
  ) {
    val requestUrl = requestBuilder.build().url

    val domainOrHost = requestUrl.domain() ?: requestUrl.host
    val cloudflareCookie = site.siteSettings.cloudFlareClearanceCookie.get(domainOrHost)

    if (cloudflareCookie.isNotNullNorEmpty()) {
      requestBuilder.appendCookieHeader("${CloudFlareInterceptor.CF_CLEARANCE}=$cloudflareCookie")
    }
  }

  private suspend fun addCloudflareClearanceCookie(
    requestUrl: HttpUrl,
    imageRequestBuilder: ImageRequest.Builder
  ) {
    val domainOrHost = requestUrl.domain() ?: requestUrl.host
    val cloudflareCookie = site.siteSettings.cloudFlareClearanceCookie.get(domainOrHost)

    if (cloudflareCookie.isNotNullNorEmpty()) {
      imageRequestBuilder.addHeader("Cookie", "${CloudFlareInterceptor.CF_CLEARANCE}=$cloudflareCookie")
    }
  }

  companion object {
    val userAgentHeaderKey = "User-Agent"
    val acceptEncodingHeaderKey = "Accept-Encoding"
    val gzipHeaderValue = "gzip"
  }

}