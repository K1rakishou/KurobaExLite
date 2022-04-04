package com.github.k1rakishou.kurobaexlite.model.data.remote.chan4

import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
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

interface PostImageDataJson {
  val filename: String?
  val ext: String?
  val tim: Long?
  val w: Int?
  val h: Int?
  val fsize: Int?

  fun hasImage(): Boolean {
    return filename.isNotNullNorEmpty()
      && ext.isNotNullNorEmpty()
      && tim != null
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
  @Json(name = "sticky") val sticky: Int?,
  @Json(name = "bumplimit") val bumpLimit: Int?,
  @Json(name = "imagelimit") val imageLimit: Int?,

  // image info
  override val filename: String?,
  override val ext: String?,
  override val tim: Long?,
  override val w: Int?,
  override val h: Int?,
  override val fsize: Int?
) : PostImageDataJson

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
  @Json(name = "sticky") val sticky: Int?,
  @Json(name = "bumplimit") val bumpLimit: Int?,
  @Json(name = "imagelimit") val imageLimit: Int?,

  // image info
  override val filename: String?,
  override val ext: String?,
  override val tim: Long?,
  override val w: Int?,
  override val h: Int?,
  override val fsize: Int?
) : PostImageDataJson