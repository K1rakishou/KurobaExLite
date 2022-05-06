package com.github.k1rakishou.kurobaexlite.helpers.settings

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import logcat.asLog

class AppSettings(
  private val appContext: Context,
  private val moshi: Moshi
) {
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
  private val dataStore by lazy { appContext.dataStore }
  private val isTablet by lazy { appContext.resources.getBoolean(R.bool.isTablet) }

  val specialUserAgentFor4chanPosting by lazy {
    buildString {
      val applicationLabel = appContext.packageManager.getApplicationLabel(appContext.applicationInfo)

      append(applicationLabel)
      append(" ")
      append(BuildConfig.VERSION_NAME)
    }
  }

  val layoutType by lazy { EnumSetting<LayoutType>(LayoutType.Auto, "layout_type", LayoutType::class.java, dataStore) }
  val bookmarksScreenOnLeftSide by lazy { BooleanSetting(true, "bookmarks_screen_on_left_side", dataStore) }

  val textTitleSizeSp by lazy {
    val defaultValue = if (isTablet) 16 else 14
    NumberSetting(defaultValue, "text_title_size_sp", dataStore)
  }
  val textSubTitleSizeSp by lazy {
    val defaultValue = if (isTablet) 14 else 12
    NumberSetting(defaultValue, "text_sub_title_size_sp", dataStore)
  }
  val postCellCommentTextSizeSp by lazy {
    val defaultValue = if (isTablet) 16 else 14
    NumberSetting(defaultValue, "post_cell_comment_text_size_sp", dataStore)
  }
  val postCellSubjectTextSizeSp by lazy {
    val defaultValue = if (isTablet) 16 else 14
    NumberSetting(defaultValue, "post_subject_comment_text_size_sp", dataStore)
  }
  val catalogSort by lazy {
    JsonSetting(moshi.adapter(CatalogSortSetting::class.java), CatalogSortSetting(), "catalog_sort_setting", dataStore)
  }
  val navigationHistoryMaxSize by lazy { NumberSetting(256, "navigation_history_max_size", dataStore) }
  val lastRememberedFilePicker by lazy { StringSetting("", "last_remembered_file_picker", dataStore) }
  val mediaViewerUiVisible by lazy { BooleanSetting(true, "media_viewer_ui_visible", dataStore) }

  val drawerDragGestureTutorialShown by lazy { BooleanSetting(false, "drawer_drag_gesture_tutorial_shown", dataStore) }

  val userAgent by lazy {
    val userAgent = try {
      WebSettings.getDefaultUserAgent(appContext)
    } catch (error: Throwable) {
      // Who knows what may happen if the user deletes webview from the system so just in case
      // switch to a default user agent in case of a crash
      logcatError(TAG) { "WebSettings.getDefaultUserAgent() error: ${error.asLog()}" }
      String.format(USER_AGENT_FORMAT, Build.VERSION.RELEASE, Build.MODEL)
    }

    StringSetting(userAgent, "user_agent", dataStore)
  }

  companion object {
    private const val TAG = "AppSettings"

    private const val USER_AGENT_FORMAT =
      "Mozilla/5.0 (Linux; Android %s; %s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.127 Mobile Safari/537.36"
  }

}

enum class LayoutType {
  Auto,
  Phone,
  Split
}

@JsonClass(generateAdapter = true)
data class CatalogSortSetting(
  @Json (name = "sort") val sort: CatalogSort = CatalogSort.BUMP,
  @Json (name = "ascending") val ascending: Boolean = false
)

enum class CatalogSort(val orderName: String) {
  BUMP("bump"),
  REPLY("reply"),
  IMAGE("image"),
  CREATION_TIME("creation_time"),
  MODIFIED("modified"),
  ACTIVITY("activity");

  companion object {
    fun find(name: String): CatalogSort {
      return values().firstOrNull { it.orderName == name }
        ?: BUMP
    }

    @JvmStatic
    fun isNotBumpOrder(orderString: String): Boolean {
      val o = find(orderString)
      return BUMP != o
    }
  }
}