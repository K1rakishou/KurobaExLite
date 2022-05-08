package com.github.k1rakishou.kurobaexlite.sites

import androidx.annotation.CallSuper
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.chan4.Chan4
import okhttp3.Request

open class RequestModifier<T : Site>(
  protected val site: T,
  protected val appSettings: AppSettings
) {

  @CallSuper
  open suspend fun modifyReplyRequest(site: T, requestBuilder: Request.Builder) {
    if (site is Chan4) {
      requestBuilder.addHeader(userAgentHeaderKey, appSettings.specialUserAgentFor4chanPosting)
    } else {
      requestBuilder.addHeader(userAgentHeaderKey, appSettings.userAgent.read())
    }

    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open suspend fun modifyCaptchaGetRequest(site: T, requestBuilder: Request.Builder) {
    requestBuilder.addHeader(userAgentHeaderKey, appSettings.userAgent.read())
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  @CallSuper
  open suspend fun modifyCatalogOrThreadGetRequest(
    site: T,
    chanDescriptor: ThreadDescriptor,
    requestBuilder: Request.Builder
  ) {
    requestBuilder.addHeader(userAgentHeaderKey, appSettings.userAgent.read())
    requestBuilder.addHeader(acceptEncodingHeaderKey, gzipHeaderValue)
  }

  companion object {
    val userAgentHeaderKey = "User-Agent"
    val acceptEncodingHeaderKey = "Accept-Encoding"
    val gzipHeaderValue = "gzip"
  }

}