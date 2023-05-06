package com.github.k1rakishou.kpnc.model.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WatchPostRequest(
  @Json(name = "user_id")
  val userId: String,
  @Json(name = "post_url")
  val postUrl: String,
  @Json(name = "application_type")
  val applicationType: ApplicationType
)

@JsonClass(generateAdapter = true)
class WatchPostResponseWrapper(
  override val data: DefaultSuccessResponse?,
  override val error: String?
) : ServerResponseWrapper<DefaultSuccessResponse>()