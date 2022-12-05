package com.github.k1rakishou.kurobaexlite.sites

sealed class SiteCaptcha {
  object Chan4Captcha : SiteCaptcha()

  class DvachCaptcha : SiteCaptcha() {

    fun formatCaptchaRequestUrl(domain: String): String {
      return "https://${domain}/api/captcha/2chcaptcha/id"
    }

  }
}