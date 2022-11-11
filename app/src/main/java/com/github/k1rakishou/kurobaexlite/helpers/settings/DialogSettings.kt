package com.github.k1rakishou.kurobaexlite.helpers.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.BooleanSetting
import com.squareup.moshi.Moshi

class DialogSettings(
  private val fileName: String,
  private val appContext: Context,
  private val moshi: Moshi
) {
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = fileName)
  private val dataStore by lazy { appContext.dataStore }

  val doNotShowNewBookmarkDialogOptions by lazy { BooleanSetting(false, "show_new_bookmark_dialog_options", dataStore) }

}