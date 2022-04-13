package com.github.k1rakishou.kurobaexlite.helpers.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

class AppSettings(
  private val appContext: Context,
  private val moshi: Moshi
) {
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
  private val dataStore by lazy { appContext.dataStore }
  private val isTablet by lazy { appContext.resources.getBoolean(R.bool.isTablet) }

  val layoutType by lazy {
    EnumSetting<LayoutType>(LayoutType.Auto, "layout_type", LayoutType::class.java, dataStore)
  }
  val bookmarksScreenOnLeftSide by lazy {
    BooleanSetting(true, "bookmarks_screen_on_left_side", dataStore)
  }

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
  val navigationHistoryMaxSize by lazy {
    NumberSetting(256, "navigation_history_max_size", dataStore)
  }

  val lastVisitedCatalog by lazy {
    JsonSetting<LastVisitedCatalog?>(moshi.adapter(LastVisitedCatalog::class.java), null, "last_visited_catalog", dataStore)
  }
  val lastVisitedThread by lazy {
    JsonSetting<LastVisitedThread?>(moshi.adapter(LastVisitedThread::class.java), null, "last_visited_thread", dataStore)
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

@JsonClass(generateAdapter = true)
data class LastVisitedCatalog(
  @Json (name = "site_key") val siteKey: String,
  @Json (name = "board_code") val boardCode: String
) {

  fun toCatalogDescriptor(): CatalogDescriptor {
    return CatalogDescriptor(
      siteKey = SiteKey(siteKey),
      boardCode = boardCode
    )
  }

  companion object {
    fun fromCatalogDescriptor(catalogDescriptor: CatalogDescriptor): LastVisitedCatalog {
      return LastVisitedCatalog(
        siteKey = catalogDescriptor.siteKeyActual,
        boardCode = catalogDescriptor.boardCode
      )
    }
  }
}

@JsonClass(generateAdapter = true)
data class LastVisitedThread(
  @Json (name = "site_key") val siteKey: String,
  @Json (name = "board_code") val boardCode: String,
  @Json (name = "thread_no") val threadNo: Long,
) {

  fun toThreadDescriptor(): ThreadDescriptor {
    return ThreadDescriptor.create(
      siteKey = SiteKey(siteKey),
      boardCode = boardCode,
      threadNo = threadNo
    )
  }

  companion object {
    fun fromThreadDescriptor(threadDescriptor: ThreadDescriptor): LastVisitedThread {
      return LastVisitedThread(
        siteKey = threadDescriptor.siteKeyActual,
        boardCode = threadDescriptor.boardCode,
        threadNo = threadDescriptor.threadNo
      )
    }
  }
}

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