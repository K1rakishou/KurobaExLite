package com.github.k1rakishou.kurobaexlite.helpers.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.k1rakishou.kurobaexlite.R

class AppSettings(
  private val appContext: Context
) {
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
  private val isTablet by lazy { appContext.resources.getBoolean(R.bool.isTablet) }

  val layoutType by lazy {
    EnumSetting<LayoutType>(LayoutType.Auto, "layout_type", LayoutType::class.java, appContext.dataStore)
  }
  val bookmarksScreenOnLeftSide by lazy {
    BooleanSetting(true, "bookmarks_screen_on_left_side", appContext.dataStore)
  }

  val textTitleSizeSp by lazy {
    val defaultValue = if (isTablet) 16 else 14
    NumberSetting(defaultValue, "text_title_size_sp", appContext.dataStore)
  }
  val textSubTitleSizeSp by lazy {
    val defaultValue = if (isTablet) 14 else 12
    NumberSetting(defaultValue, "text_sub_title_size_sp", appContext.dataStore)
  }
  val postCellCommentTextSizeSp by lazy {
    val defaultValue = if (isTablet) 16 else 14
    NumberSetting(defaultValue, "post_cell_comment_text_size_sp", appContext.dataStore)
  }
  val postCellSubjectTextSizeSp by lazy {
    val defaultValue = if (isTablet) 16 else 14
    NumberSetting(defaultValue, "post_subject_comment_text_size_sp", appContext.dataStore)
  }

}

enum class LayoutType {
  Auto,
  Phone,
  Split
}