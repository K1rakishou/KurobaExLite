package com.github.k1rakishou.kpnc.domain

import android.content.SharedPreferences
import com.github.k1rakishou.kpnc.AppConstants
import com.github.k1rakishou.kpnc.helpers.Try
import com.github.k1rakishou.kpnc.helpers.extractServerResponse
import com.github.k1rakishou.kpnc.helpers.isNotNullNorBlank
import com.github.k1rakishou.kpnc.helpers.logcatDebug
import com.github.k1rakishou.kpnc.helpers.logcatError
import com.github.k1rakishou.kpnc.model.data.Endpoints
import com.github.k1rakishou.kpnc.model.data.network.DefaultSuccessResponse
import com.github.k1rakishou.kpnc.model.data.network.DefaultSuccessResponseWrapper
import com.github.k1rakishou.kpnc.model.data.network.MessageDeliveredRequest
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ServerDeliveryNotifierImpl(
  private val sharedPrefs: SharedPreferences,
  private val endpoints: Endpoints,
  private val moshi: Moshi,
  private val okHttpClient: OkHttpClient
) : ServerDeliveryNotifier {

  override fun notifyPostUrlsDelivered(replyMessageIds: List<Long>): Result<Unit> {
    return Result.Try {
      logcatDebug(TAG) { "notifyPostUrlsDelivered() replyMessageIds=${replyMessageIds.size}" }

      val userId = sharedPrefs.getString(AppConstants.PrefKeys.USER_ID, null)
        .takeIf { it.isNotNullNorBlank() }

      if (userId == null) {
        logcatError(TAG) { "notifyPostUrlsDelivered() userId is null" }
        return@Try
      }

      val instanceAddress = sharedPrefs.getString(AppConstants.PrefKeys.INSTANCE_ADDRESS, null)
        .takeIf { it.isNotNullNorBlank() }

      if (instanceAddress == null) {
        logcatError(TAG) { "notifyPostUrlsDelivered() instanceAddress is null" }
        return@Try
      }

      if (replyMessageIds.isEmpty()) {
        logcatError(TAG) { "notifyPostUrlsDelivered() replyMessageIds is empty" }
        return@Try
      }

      val messageDeliveredRequest = MessageDeliveredRequest(
        userId = userId,
        replyIds = replyMessageIds
      )

      val updateTokenRequestJson = moshi.adapter(MessageDeliveredRequest::class.java)
        .toJson(messageDeliveredRequest)

      val requestBody = updateTokenRequestJson.toRequestBody("application/json".toMediaType())

      val request = Request.Builder()
        .url(endpoints.updateMessageDelivered(instanceAddress))
        .post(requestBody)
        .build()

      logcatDebug(TAG) { "notifyPostUrlsDelivered() marking replies as delivered..." }

      val response = okHttpClient.newCall(request).execute()
      val defaultSuccessResponse = extractServerResponse<DefaultSuccessResponse, DefaultSuccessResponseWrapper>(
        moshi = moshi,
        response = response
      )

      logcatDebug(TAG) {
        "notifyPostUrlsDelivered() marking replies as delivered... Success: ${defaultSuccessResponse.success}"
      }
    }
  }

  companion object {
    private const val TAG = "ServerDeliveryNotifierImpl"
  }

}