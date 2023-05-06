package com.github.k1rakishou.kpnc.model.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MessageDeliveredRequest(
  @Json(name = "user_id")
  val userId: String,
  @Json(name = "reply_ids")
  val replyIds: List<Long>
)