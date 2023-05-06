package com.github.k1rakishou.kpnc.model.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AccountInfoRequest(
  @Json(name = "user_id")
  val userId: String,
  @Json(name = "application_type")
  val applicationType: ApplicationType
)

@JsonClass(generateAdapter = true)
class AccountInfoResponseWrapper(
  override val data: AccountInfoResponse?,
  override val error: String?
) : ServerResponseWrapper<AccountInfoResponse>() {
}

@JsonClass(generateAdapter = true)
data class AccountInfoResponse(
  @Json(name = "account_id")
  val accountId: String,
  @Json(name = "is_valid")
  val isValid: Boolean,
  @Json(name = "valid_until")
  val validUntil: Long?
)