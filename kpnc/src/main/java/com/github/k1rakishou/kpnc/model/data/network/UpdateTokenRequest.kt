package com.github.k1rakishou.kpnc.model.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateTokenRequest(
  @Json(name = "user_id")
  val userId: String,
  @Json(name = "firebase_token")
  val firebaseToken: String,
  @Json(name = "application_type")
  val applicationType: Int
)