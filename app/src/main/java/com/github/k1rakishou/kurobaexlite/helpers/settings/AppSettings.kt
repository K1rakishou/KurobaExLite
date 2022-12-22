package com.github.k1rakishou.kurobaexlite.helpers.settings

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.EnumSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.JsonSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.NumberSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.RangeSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.StringSetting
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import logcat.asLog

class AppSettings(
  private val fileName: String,
  private val appContext: Context,
  private val moshi: Moshi
) {
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = fileName)
  private val dataStore by lazy { appContext.dataStore }
  private val isTablet by lazy { appContext.resources.getBoolean(R.bool.isTablet) }
  private val composeDensity by lazy { Density(appContext) }

  val processorsCount by lazy { Runtime.getRuntime().availableProcessors() }

  val specialUserAgentFor4chanPosting by lazy {
    buildString {
      val applicationLabel = appContext.packageManager.getApplicationLabel(appContext.applicationInfo)

      append(applicationLabel)
      append(" ")
      append(BuildConfig.VERSION_NAME)
    }
  }

  val layoutType by lazy { EnumSetting<LayoutType>(LayoutType.Auto, "layout_type", LayoutType::class.java, dataStore) }
  val historyEnabled by lazy { BooleanSetting(true, "history_enabled", dataStore) }
  val drawerContentType by lazy { EnumSetting(DrawerContentType.Bookmarks, "drawer_content_type", DrawerContentType::class.java, dataStore) }

  val globalFontSizeMultiplier by lazy {
    val defaultValue = if (isTablet) 125 else 100
    return@lazy RangeSetting(
      minValue = 100,
      maxValue = 150,
      defaultValue = defaultValue,
      settingKey = "global_font_size_multiplier",
      dataStore = dataStore
    )
  }

  val catalogSort by lazy { JsonSetting(moshi.adapter(CatalogSortSetting::class.java), CatalogSortSetting(), "catalog_sort_setting", dataStore) }
  val catalogPostViewMode by lazy { EnumSetting<PostViewModeSetting>(PostViewModeSetting.List, "catalog_post_view_mode", PostViewModeSetting::class.java, dataStore) }
  val navigationHistoryMaxSize by lazy { NumberSetting(256, "navigation_history_max_size", dataStore) }
  val lastRememberedFilePicker by lazy { StringSetting("", "last_remembered_file_picker", dataStore) }
  val mediaViewerUiVisible by lazy { BooleanSetting(true, "media_viewer_ui_visible", dataStore) }
  val watcherIntervalForegroundSeconds by lazy { EnumSetting(WatcherFg.SEC_30, "watcher_interval_foreground_seconds", WatcherFg::class.java, dataStore) }
  val watcherIntervalBackgroundSeconds by lazy { EnumSetting(WatcherBg.MIN_30, "watcher_interval_background_seconds", WatcherBg::class.java, dataStore) }
  val replyNotifications by lazy { BooleanSetting(true, "reply_notifications_enabled", dataStore) }
  val lastUpdateCheckTime by lazy { NumberSetting<Long>(0L, "last_update_check_time", dataStore) }
  val lastCheckedVersionCode by lazy { NumberSetting<Long>(0L, "last_checked_version_code", dataStore) }
  val notifyAboutBetaUpdates by lazy { BooleanSetting(true, "notify_about_beta_updates", dataStore) }
  val catalogGridModeColumnCount by lazy { NumberSetting<Int>(0, "catalog_grid_mode_column_count", dataStore) }
  val albumColumnCount by lazy { NumberSetting<Int>(0, "album_column_count", dataStore) }
  val albumShowImageInfo by lazy { BooleanSetting(true, "album_show_image_info", dataStore) }
  val automaticallyStartWatchingBookmarks by lazy { BooleanSetting(true, "automatically_start_watching_bookmarks", dataStore) }
  val catalogSelectionScreenLastUsedSite by lazy { StringSetting("", "catalog_selection_screen_last_used_site", dataStore) }
  val currentLightThemeName by lazy { StringSetting("", "current_light_theme_name", dataStore) }
  val currentDarkThemeName by lazy { StringSetting("", "current_dark_theme_name", dataStore) }
  val isDarkThemeUsed by lazy { BooleanSetting(true, "is_dark_theme_used", dataStore) }

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

  suspend fun calculateFontSizeInPixels(fontSize: Int): Int {
    return (fontSize * (globalFontSizeMultiplier.read() / 100f)).toInt()
  }

  suspend fun calculateFontSizeInSp(fontSize: TextUnit): TextUnit {
    return (fontSize * (globalFontSizeMultiplier.read() / 100f))
  }

  companion object {
    private const val TAG = "AppSettings"

    const val CATALOG_MIN_COLUMN_COUNT = 0
    const val CATALOG_MAX_COLUMN_COUNT = 4

    const val ALBUM_MIN_COLUMN_COUNT = 0
    const val ALBUM_MAX_COLUMN_COUNT = 5

    private const val USER_AGENT_FORMAT =
      "Mozilla/5.0 (Linux; Android %s; %s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.127 Mobile Safari/537.36"
  }

}

enum class WatcherFg(val seconds: Int, val text: String) {
  SEC_30(30, "30 seconds"),
  SEC_60(60, "60 seconds"),
  SEC_90(90, "90 seconds"),
  SEC_120(120, "2 minutes"),
  SEC_240(240, "4 minutes"),
  SEC_300(300, "5 minutes"),
}

enum class WatcherBg(val seconds: Int, val text: String) {
  MIN_1(60, "1 minute (only for development!)"),
  MIN_15(15 * 60, "15 minutes"),
  MIN_30(30 * 60, "30 minutes"),
  MIN_45(45 * 60, "45 minutes"),
  MIN_60(60 * 60, "1 hour"),
  MIN_120(120 * 60, "2 hours"),
}

enum class LayoutType {
  Auto,
  Phone,
  Split
}

@JsonClass(generateAdapter = true)
data class IntPositionJson(
  val x: Int = 0,
  val y: Int = 0
)

enum class PostViewModeSetting(val mode: Int) {
  List(0),
  Grid(1);

  fun toPostViewMode(): PostViewMode {
    return when (this) {
      List -> PostViewMode.List
      Grid -> PostViewMode.Grid
    }
  }
}

@Immutable
enum class PostViewMode {
  List,
  Grid;

  fun toPostViewModeSetting(): PostViewModeSetting {
    return when (this) {
      PostViewMode.List -> PostViewModeSetting.List
      PostViewMode.Grid -> PostViewModeSetting.Grid
    }
  }
}

@JsonClass(generateAdapter = true)
data class CatalogSortSetting(
  @Json (name = "sort") val sort: CatalogSort = CatalogSort.BUMP,
  @Json (name = "ascending") val ascending: Boolean = false
)

@Immutable
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

@Immutable
enum class DrawerContentType {
  History,
  Bookmarks
}