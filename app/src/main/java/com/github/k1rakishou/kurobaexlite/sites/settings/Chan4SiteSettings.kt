package com.github.k1rakishou.kurobaexlite.sites.settings

import android.content.Context
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.settings.items.BooleanSettingItem
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingItem
import com.github.k1rakishou.kurobaexlite.features.settings.items.StringSettingItem
import com.github.k1rakishou.kurobaexlite.helpers.asFormattedToken
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.StringSetting
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.squareup.moshi.Moshi

class Chan4SiteSettings(
  appContext: Context,
  moshi: Moshi
) : SiteSettings(appContext, moshi, "chan4") {

  val rememberCaptchaCookies by lazy { BooleanSetting(true, "remember_captcha_cookies", dataStore) }
  val chan4CaptchaCookie by lazy { StringSetting("", "chan4_captcha_cookie", dataStore) }
  val channel4CaptchaCookie by lazy { StringSetting("", "channel4_captcha_cookie", dataStore) }

  override fun uiSettingItems(
    showDialogScreen: suspend (DialogScreen.Params) -> Unit
  ): List<SettingItem> {
    return listOf(
      BooleanSettingItem(
        title = appContext.resources.getString(R.string.chan4_setting_remember_captcha_cookies),
        delegate = rememberCaptchaCookies
      ),
      StringSettingItem(
        title = appContext.resources.getString(R.string.chan4_setting_4chan_cookie),
        delegate = chan4CaptchaCookie,
        showDialogScreen = showDialogScreen,
        settingValueMapper = { token -> token.asFormattedToken() }
      ),
      StringSettingItem(
        title = appContext.resources.getString(R.string.chan4_setting_4channel_cookie),
        delegate = channel4CaptchaCookie,
        showDialogScreen = showDialogScreen,
        settingValueMapper = { token -> token.asFormattedToken() }
      )
    )
  }

}