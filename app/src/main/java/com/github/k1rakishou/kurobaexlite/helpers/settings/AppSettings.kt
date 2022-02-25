package com.github.k1rakishou.kurobaexlite.helpers.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

class AppSettings(
  private val appContext: Context
) {
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

  val layoutType by lazy { EnumSetting<LayoutType>(LayoutType.Auto, "layout_type", LayoutType::class.java, appContext.dataStore) }
  val bookmarksScreenOnLeftSide by lazy { BooleanSetting(true, "bookmarks_screen_on_left_side", appContext.dataStore) }

}

enum class LayoutType {
  Auto,
  Phone,
  Split
}