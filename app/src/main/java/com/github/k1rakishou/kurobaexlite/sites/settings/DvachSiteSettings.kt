package com.github.k1rakishou.kurobaexlite.sites.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.text.buildAnnotatedString
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.login.chan4.Chan4LoginScreen
import com.github.k1rakishou.kurobaexlite.features.login.dvach.DvachLoginScreen
import com.github.k1rakishou.kurobaexlite.features.settings.items.LinkSettingItem
import com.github.k1rakishou.kurobaexlite.features.settings.items.MapSettingItem
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingItem
import com.github.k1rakishou.kurobaexlite.features.settings.items.StringSettingItem
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.KeyValue
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.MapSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.MapSettingEntry
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.StringSetting
import com.github.k1rakishou.kurobaexlite.helpers.util.asFormattedToken
import com.github.k1rakishou.kurobaexlite.helpers.util.domain
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class DvachSiteSettings(
  appContext: Context,
  moshi: Moshi,
  defaultDomain: String
) : SiteSettings(appContext, moshi, "dvach", defaultDomain) {
  val passcodeCookie by lazy { StringSetting("", "passcode_cookie", dataStore) }
  val userCodeCookie by lazy { StringSetting("", "user_code_cookie", dataStore) }

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
      StringSettingItem(
        title = appContext.resources.getString(R.string.dvach_setting_site_domain),
        delegate = currentSiteDomain,
        showDialogScreen = showDialogScreen,
        valueValidator = { input ->
          val fullInput = "https://${input}"
          val host = fullInput.toHttpUrlOrNull()?.domain()
          if (host == null) {
            val error = SettingFormatException("Failed to extract domain from \'${fullInput}\'")
            return@StringSettingItem Result.failure(error)
          }

          return@StringSettingItem Result.success(host)
        },
        settingDisplayFormatter = { value -> "https://${value}" }
      ),
      StringSettingItem(
        title = appContext.resources.getString(R.string.dvach_setting_passcode_cookie),
        delegate = passcodeCookie,
        showDialogScreen = showDialogScreen,
        settingDisplayFormatter = { value -> value.asFormattedToken() },
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
      StringSettingItem(
        title = appContext.resources.getString(R.string.dvach_setting_user_code_cookie),
        delegate = userCodeCookie,
        showDialogScreen = showDialogScreen,
        settingDisplayFormatter = { value -> value.asFormattedToken() },
      ),
      LinkSettingItem(
        key = "dvach_passcode_options",
        title = appContext.resources.getString(R.string.dvach_setting_passcode),
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

            val dvachLoginScreen = ComposeScreen.createScreen<DvachLoginScreen>(
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              args = { putString(Chan4LoginScreen.CURRENT_PASSCODE_COOKIE, currentPasscodeCookie) }
            )

            navigationRouter.presentScreen(dvachLoginScreen)
          }
        }
      )
    )
  }
}