package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.helpers.cache.site_data.PersistableSiteData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class BoardsData(
  val siteBoards: List<SiteBoard>
)

data class SiteBoard(
  val catalogDescriptor: CatalogDescriptor,
  val boardTitle: String?,
  val boardDescription: String?,
  val workSafe: Boolean
)

@JsonClass(generateAdapter = true)
data class PersistableSiteBoards(
  @Json(name = "cached_on") val cachedDate: Long,
  @Json(name = "page") val page: Int,
  @Json(name = "site_boards") val list: List<PersistableSiteBoard>
) : PersistableSiteData {
  companion object {
    private const val FILE_NAME = "board_list_%d.json"

    fun boardList(page: Int) = String.format(FILE_NAME, page)
  }
}

@JsonClass(generateAdapter = true)
data class PersistableSiteBoard(
  @Json(name = "sort_order") val sortOrder: Int,
  @Json(name = "site_key") val siteKey: String,
  @Json(name = "board_code") val boardCode: String,
  @Json(name = "board_title") val boardTitle: String?,
  @Json(name = "board_description") val boardDescription: String?,
  @Json(name = "work_safe") val workSafe: Boolean?
) {

  fun toSiteBoard(): SiteBoard {
    return SiteBoard(
      catalogDescriptor = CatalogDescriptor(SiteKey(siteKey), boardCode),
      boardTitle = boardTitle,
      boardDescription = boardDescription,
      workSafe = workSafe == true
    )
  }

  companion object {
    fun fromSiteBoard(sortOrder: Int, siteBoard: SiteBoard): PersistableSiteBoard {
      return PersistableSiteBoard(
        sortOrder = sortOrder,
        siteKey = siteBoard.catalogDescriptor.siteKeyActual,
        boardCode = siteBoard.catalogDescriptor.boardCode,
        boardTitle = siteBoard.boardTitle,
        boardDescription = siteBoard.boardDescription,
        workSafe = siteBoard.workSafe
      )
    }
  }

}