package com.github.k1rakishou.kurobaexlite.model.data.remote.chan4

import com.github.k1rakishou.kurobaexlite.model.data.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.data.CountryFlag
import com.github.k1rakishou.kurobaexlite.model.data.PostDataSticky
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CatalogPageDataJson(
  @Json(name = "page") val page: Int,
  @Json(name = "threads") val threads: List<CatalogThreadDataJson>
)

@JsonClass(generateAdapter = true)
data class ThreadDataJson(
  val posts: List<ThreadPostDataJson>
)

abstract class SharedDataJson {
  abstract val filename: String?
  abstract val ext: String?
  abstract val tim: Long?
  abstract val w: Int?
  abstract val h: Int?
  abstract val fsize: Int?

  abstract val sticky: Int?
  abstract val stickyCap: Int?

  abstract val country: String?
  abstract val countryName: String?
  abstract val boardFlag: String?
  abstract val boardFlagName: String?

  fun sticky(): PostDataSticky? {
    if (sticky == null || sticky != 1) {
      return null
    }

    return PostDataSticky(capacity = stickyCap)
  }

  fun countryFlag(): CountryFlag? {
    if (country.isNullOrEmpty()) {
      return null
    }

    return CountryFlag(
      flagId = country!!,
      flagName = countryName
    )
  }

  fun boardFlag(): BoardFlag? {
    if (boardFlag.isNullOrEmpty()) {
      return null
    }

    return BoardFlag(
      flagId = boardFlag!!,
      flagName = boardFlagName
    )
  }

  fun hasImage(): Boolean {
    return tim != null
      && w != null
      && h != null
      && fsize != null
  }

}

@JsonClass(generateAdapter = true)
data class ThreadPostDataJson(
  val no: Long,
  val com: String?,
  val sub: String?,
  val time: Long?,
  val replies: Int?,
  val images: Int?,
  @Json(name = "last_modified") val lastModified: Long?,
  @Json(name = "unique_ips") val posters: Int?,
  @Json(name = "archived") val archived: Int?,
  @Json(name = "closed") val closed: Int?,
  @Json(name = "sticky") override val sticky: Int?,
  @Json(name = "sticky_cap") override val stickyCap: Int?,
  @Json(name = "bumplimit") val bumpLimit: Int?,
  @Json(name = "imagelimit") val imageLimit: Int?,

  val name: String?,
  val trip: String?,
  val id: String?,

  @Json(name = "country") override val country: String? = null,
  @Json(name = "country_name") override val countryName: String? = null,
  @Json(name = "board_flag") override val boardFlag: String? = null,
  @Json(name = "flag_name") override val boardFlagName: String? = null,

  // image info
  override val filename: String?,
  override val ext: String?,
  override val tim: Long?,
  override val w: Int?,
  override val h: Int?,
  override val fsize: Int?
) : SharedDataJson()

@JsonClass(generateAdapter = true)
data class CatalogThreadDataJson(
  val no: Long,
  val sub: String?,
  val com: String?,
  val time: Long?,
  val replies: Int?,
  val images: Int?,
  @Json(name = "last_modified") val lastModified: Long?,
  @Json(name = "unique_ips") val posters: Int?,
  @Json(name = "archived") val archived: Int?,
  @Json(name = "closed") val closed: Int?,
  @Json(name = "sticky") override val sticky: Int?,
  @Json(name = "sticky_cap") override val stickyCap: Int?,
  @Json(name = "bumplimit") val bumpLimit: Int?,
  @Json(name = "imagelimit") val imageLimit: Int?,

  val name: String?,
  val trip: String?,
  val id: String?,

  @Json(name = "country") override val country: String? = null,
  @Json(name = "country_name") override val countryName: String? = null,
  @Json(name = "board_flag") override val boardFlag: String? = null,
  @Json(name = "flag_name") override val boardFlagName: String? = null,

  // image info
  override val filename: String?,
  override val ext: String?,
  override val tim: Long?,
  override val w: Int?,
  override val h: Int?,
  override val fsize: Int?
) : SharedDataJson()

@JsonClass(generateAdapter = true)
data class ThreadBookmarkInfoJson(
  @Json(name = "posts") val postInfoForBookmarkList: List<PostInfoForBookmarkJson>
)

@JsonClass(generateAdapter = true)
data class PostInfoForBookmarkJson(
  @Json(name = "no") val postNo: Long,
  @Json(name = "archived") val archived: Int?,
  @Json(name = "closed") val closed: Int?,
  val sub: String?,
  val com: String?,
  val resto: Int,
  @Json(name = "bumplimit") val bumpLimit: Int?,
  @Json(name = "imagelimit") val imageLimit: Int?,
  @Json(name = "sticky") val sticky: Int?,
  @Json(name = "sticky_cap") val stickyCap: Int?,

  val tim: Long?
) {
  val isOp: Boolean
    get() = resto == 0
  val isSticky: Boolean
    get() = sticky == 1
  val isClosed: Boolean
    get() = closed == 1
  val isArchived: Boolean
    get() = archived == 1
  val isBumpLimit: Boolean
    get() = bumpLimit == 1
  val isImageLimit: Boolean
    get() = imageLimit == 1
  val comment: String
    get() = com ?: ""
}

@JsonClass(generateAdapter = true)
data class CatalogPageJson(
  val page: Int,
  val threads: List<CatalogPageThreadJson>
)

@JsonClass(generateAdapter = true)
data class CatalogPageThreadJson(
  @Json(name = "no") val postNo: Long,
)