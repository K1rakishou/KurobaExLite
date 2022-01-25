package com.github.k1rakishou.kurobaexlite.model.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CatalogPageDataJson(
  @Json(name = "page") val page: Int,
  @Json(name = "threads") val threads: List<CatalogThreadDataJson>
)

@JsonClass(generateAdapter = true)
data class CatalogThreadDataJson(
  val no: Long,
  val sub: String?,
  val com: String?,
  val ext: String?,
  val tim: Long?
)