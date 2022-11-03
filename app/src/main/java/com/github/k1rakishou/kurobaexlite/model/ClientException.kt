package com.github.k1rakishou.kurobaexlite.model

import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import okhttp3.HttpUrl
import java.io.IOException

abstract class ClientException : Exception {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)
}

class BadStatusResponseException(val status: Int) : ClientException("Bad response status: ${status}") {

  fun isAuthError(): Boolean {
    return status == 401
  }

  fun isForbiddenError(): Boolean {
    return status == 403
  }

  fun isNotFoundError(): Boolean {
    return status == 404
  }

}

class EmptyBodyResponseException : ClientException("Response has no body")

class SiteIsNotSupported(siteKey: SiteKey) : ClientException("Site \'${siteKey.key}\' is not supported")

enum class FirewallType {
  Cloudflare,
  YandexSmartCaptcha
}

/**
 * This one has to inherit from IOException and not ClientException like the other exceptions because OkHttp's interceptor
 * will crash the whole app if any exception other than IOException is thrown inside of it.
 * */
class FirewallDetectedException(
  val firewallType: FirewallType,
  val requestUrl: HttpUrl
) : IOException("Url '$requestUrl' is blocked by ${firewallType} firewall!")

class BypassException(message: String) : ClientException("BypassException: ${message}")
