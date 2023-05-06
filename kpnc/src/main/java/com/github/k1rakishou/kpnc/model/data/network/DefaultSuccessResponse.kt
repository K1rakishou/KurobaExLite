package com.github.k1rakishou.kpnc.model.data.network

import com.github.k1rakishou.kpnc.model.GenericClientException
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class DefaultSuccessResponseWrapper(
  override val data: DefaultSuccessResponse?,
  override val error: String?
) : ServerResponseWrapper<DefaultSuccessResponse>()

@JsonClass(generateAdapter = true)
data class DefaultSuccessResponse(
  @Json(name = "success")
  val success: Boolean
) {
  fun ensureSuccess() {
    if (!success) {
      throw GenericClientException("Server returned DefaultSuccessResponse with success != true")
    }
  }
}