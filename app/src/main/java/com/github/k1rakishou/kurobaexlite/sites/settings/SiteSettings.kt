package com.github.k1rakishou.kurobaexlite.sites.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingItem
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.MapSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.StringSetting
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope

abstract class SiteSettings(
  protected val appContext: Context,
  protected val moshi: Moshi,
  val key: String
) {
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "${key}_site_settings")
  protected val dataStore by lazy { appContext.dataStore }

  val lastUsedBoardFlags by lazy { StringSetting("", "last_used_board_flags", dataStore) }

  abstract val cloudFlareClearanceCookie: MapSetting<String, String>

  abstract suspend fun isLoggedIn(): Boolean

  abstract suspend fun uiSettingItems(
    coroutineScope: CoroutineScope,
    componentActivity: ComponentActivity,
    navigationRouter: NavigationRouter,
    showDialogScreen: suspend (DialogScreen.Params) -> Unit
  ): List<SettingItem>
}