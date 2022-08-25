package com.github.k1rakishou.kurobaexlite.helpers.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.StringSetting
import com.squareup.moshi.Moshi

class RemoteImageSearchSettings(
  private val fileName: String,
  private val appContext: Context,
  private val moshi: Moshi
) {
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = fileName)
  private val dataStore by lazy { appContext.dataStore }

  val yandexImageSearchCookies by lazy { StringSetting("", "yandex_image_search_cookies", dataStore) }

}