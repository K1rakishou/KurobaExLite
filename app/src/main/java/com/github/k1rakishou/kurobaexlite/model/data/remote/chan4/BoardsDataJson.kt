package com.github.k1rakishou.kurobaexlite.model.data.remote.chan4

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BoardsDataJson(
  @Json(name = "boards") val boards: List<BoardDataJson>
)

@JsonClass(generateAdapter = true)
data class BoardDataJson(
  @Json(name = "board") val boardCode: String?,
  @Json(name = "title") val boardTitle: String?,
  @Json(name = "meta_description") val boardDescription: String?,
  @Json(name = "ws_board") val workSafe: Int?
)