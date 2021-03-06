package com.github.k1rakishou.kurobaexlite.sites.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.squareup.moshi.Moshi

abstract class SiteSettings(
  protected val appContext: Context,
  private val moshi: Moshi,
  val key: String
) {
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "${key}_site_settings")
  protected val dataStore by lazy { appContext.dataStore }

  abstract fun uiSettingItems(
    showDialogScreen: suspend (DialogScreen.Params) -> Unit
  ): List<SettingItem>
}