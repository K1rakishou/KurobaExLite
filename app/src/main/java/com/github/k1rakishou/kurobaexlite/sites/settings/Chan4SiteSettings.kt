package com.github.k1rakishou.kurobaexlite.sites.settings

import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.settings.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.StringSetting
import com.squareup.moshi.Moshi

class Chan4SiteSettings(
  appContext: Context,
  moshi: Moshi
) : SiteSettings(appContext, moshi, "chan4") {

  val rememberCaptchaCookies by lazy { BooleanSetting(true, "remember_captcha_cookies", dataStore) }
  val chan4CaptchaCookie by lazy { StringSetting("", "chan4_captcha_cookie", dataStore) }
  val channel4CaptchaCookie by lazy { StringSetting("", "channel4_captcha_cookie", dataStore) }

}