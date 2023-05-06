package com.github.k1rakishou.kpnc.helpers

import com.github.k1rakishou.kpnc.model.JsonConversionException
import com.github.k1rakishou.kpnc.model.data.network.ServerResponseWrapper
import com.squareup.moshi.Moshi
import okhttp3.Response

inline fun <ServerResponse, reified Wrapper : ServerResponseWrapper<ServerResponse>> extractServerResponse(
  moshi: Moshi,
  response: Response
): ServerResponse {
  val responseBody = response.body!!

  val wrappedResponse = responseBody.source().use { source ->
    moshi
      .adapter<Wrapper>(Wrapper::class.java)
      .fromJson(source)
  }

  if (wrappedResponse == null) {
    throw JsonConversionException("Failed to convert response body into ${Wrapper::class.java.simpleName}")
  }

  return wrappedResponse.dataOrThrow()
}