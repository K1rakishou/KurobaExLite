package com.github.k1rakishou.kurobaexlite.sites.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.text.buildAnnotatedString
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.login.chan4.Chan4LoginScreen
import com.github.k1rakishou.kurobaexlite.features.settings.items.BooleanSettingItem
import com.github.k1rakishou.kurobaexlite.features.settings.items.LinkSettingItem
import com.github.k1rakishou.kurobaexlite.features.settings.items.MapSettingItem
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingItem
import com.github.k1rakishou.kurobaexlite.features.settings.items.StringSettingItem
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.KeyValue
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.MapSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.MapSettingEntry
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.StringSetting
import com.github.k1rakishou.kurobaexlite.helpers.util.asFormattedToken
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Chan4SiteSettings(
  appContext: Context,
  moshi: Moshi
) : SiteSettings(appContext, moshi, "chan4") {

  val rememberCaptchaCookies by lazy { BooleanSetting(true, "remember_captcha_cookies", dataStore) }
  val chan4CaptchaCookie by lazy { StringSetting("", "chan4_captcha_cookie", dataStore) }
  val channel4CaptchaCookie by lazy { StringSetting("", "channel4_captcha_cookie", dataStore) }
  val passcodeCookie by lazy { StringSetting("", "passcode_cookie", dataStore) }

  override val cloudFlareClearanceCookie: MapSetting<String, String> by lazy {
    MapSetting<String, String>(
      moshi = moshi,
      mapperFrom = { mapSettingEntry -> KeyValue(mapSettingEntry.key, mapSettingEntry.value) },
      mapperTo = { keyValue -> MapSettingEntry(keyValue.key, keyValue.value) },
      defaultValue = emptyMap(),
      settingKey = "cloudflare_clearance_cookie",
      dataStore = dataStore
    )
  }

  override suspend fun isLoggedIn(): Boolean {
    return passcodeCookie.read().isNotEmpty()
  }

  override suspend fun uiSettingItems(
    coroutineScope: CoroutineScope,
    componentActivity: ComponentActivity,
    navigationRouter: NavigationRouter,
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
        settingDisplayFormatter = { token -> token.asFormattedToken() }
      ),
      StringSettingItem(
        title = appContext.resources.getString(R.string.chan4_setting_4channel_cookie),
        delegate = channel4CaptchaCookie,
        showDialogScreen = showDialogScreen,
        settingDisplayFormatter = { token -> token.asFormattedToken() }
      ),
      MapSettingItem<String, String>(
        title = appContext.resources.getString(R.string.site_setting_cloudflare_clearance_cookie),
        delegate = cloudFlareClearanceCookie,
        showDialogScreen = showDialogScreen,
        keyMapperFrom = { key -> key },
        keyMapperTo = { key -> key },
        valueMapperFrom = { value -> value },
        valueMapperTo = { value -> value },
        valueFormatter = { value -> value.asFormattedToken() }
      ),
      LinkSettingItem(
        key = "4chan_passcode_options",
        title = appContext.resources.getString(R.string.chan4_setting_passcode),
        subtitle = buildAnnotatedString {
          val currentPasscodeCookie = passcodeCookie.read().asFormattedToken()

          if (currentPasscodeCookie.isNotEmpty()) {
            append(currentPasscodeCookie)
          } else {
            append(appContext.resources.getString(R.string.chan4_setting_passcode_not_logged_in))
          }
        },
        onClicked = {
          coroutineScope.launch {
            val currentPasscodeCookie = passcodeCookie.read()

            val chan4LoginScreen = ComposeScreen.createScreen<Chan4LoginScreen>(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              args = { putString(Chan4LoginScreen.CURRENT_PASSCODE_COOKIE, currentPasscodeCookie) }
            )

            navigationRouter.presentScreen(chan4LoginScreen)
          }
        }
      )
    )
  }

}