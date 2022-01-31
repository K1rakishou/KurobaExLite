package com.github.k1rakishou.kurobaexlite.model.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ThreadDataJson(
  val posts: List<ThreadPostDataJson>
)

@JsonClass(generateAdapter = true)
data class ThreadPostDataJson(
  val no: Long,
  val com: String?,
  val sub: String?,
  val ext: String?,
  val tim: Long?,
  val replies: Int?,
  val images: Int?,
)