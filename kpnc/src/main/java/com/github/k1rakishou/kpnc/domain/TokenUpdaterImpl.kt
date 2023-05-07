package com.github.k1rakishou.kpnc.domain

import android.content.SharedPreferences
import com.github.k1rakishou.kpnc.AppConstants
import com.github.k1rakishou.kpnc.helpers.Try
import com.github.k1rakishou.kpnc.helpers.extractServerResponse
import com.github.k1rakishou.kpnc.helpers.logcatDebug
import com.github.k1rakishou.kpnc.helpers.logcatError
import com.github.k1rakishou.kpnc.model.data.Endpoints
import com.github.k1rakishou.kpnc.model.data.network.ApplicationType
import com.github.k1rakishou.kpnc.model.data.network.DefaultSuccessResponse
import com.github.k1rakishou.kpnc.model.data.network.DefaultSuccessResponseWrapper
import com.github.k1rakishou.kpnc.model.data.network.UpdateTokenRequest
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class TokenUpdaterImpl(
  private val sharedPrefs: SharedPreferences,
  private val endpoints: Endpoints,
  private val moshi: Moshi,
  private val okHttpClient: OkHttpClient
) : TokenUpdater {
  private var updateTokenLatch = CountDownLatch(1)
  private val updatingToken = AtomicBoolean(false)

  override fun reset() {
    if (updatingToken.get()) {
      return
    }

    logcatDebug(TAG) { "reset()" }

    synchronized(this) {
      updateTokenLatch = CountDownLatch(1)
      updatingToken.set(false)
    }
  }

  override fun updateToken(instanceAddress: String?, userId: String?, token: String): Result<Boolean> {
    return Result.Try {
      if (!updatingToken.compareAndSet(false, true)) {
        awaitUntilTokenUpdated()
        return@Try false
      }

      val prevLatch = synchronized(this) { updateTokenLatch }
      logcatDebug(TAG) { "updateToken()" }

      try {
        var userIdToUse = userId
        if (userIdToUse.isNullOrEmpty()) {
          userIdToUse = sharedPrefs.getString(AppConstants.PrefKeys.USER_ID, null)
          if (userIdToUse.isNullOrEmpty()) {
            logcatError(TAG) { "onNewToken() accountId is null or empty" }
            return@Try false
          }
        }

        var instanceAddressToUse = instanceAddress
        if (instanceAddressToUse.isNullOrEmpty()) {
          instanceAddressToUse = sharedPrefs.getString(AppConstants.PrefKeys.INSTANCE_ADDRESS, null)
          if (instanceAddressToUse.isNullOrEmpty()) {
            logcatError(TAG) { "onNewToken() instanceAddress is null or empty" }
            return@Try false
          }
        }

        val updateTokenRequest = UpdateTokenRequest(
          userId = userIdToUse,
          firebaseToken = token,
          applicationType = ApplicationType.fromFlavor().value
        )

        val updateTokenRequestJson = moshi.adapter(UpdateTokenRequest::class.java)
          .toJson(updateTokenRequest)

        val requestBody = updateTokenRequestJson.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
          .url(endpoints.updateFirebaseTokenEndpoint(instanceAddressToUse))
          .post(requestBody)
          .build()

        logcatDebug(TAG) { "onNewToken() updating token on the server..." }

        val response = okHttpClient.newCall(request).execute()
        val defaultSuccessResponse = extractServerResponse<DefaultSuccessResponse, DefaultSuccessResponseWrapper>(
          moshi = moshi,
          response = response
        )

        logcatDebug(TAG) {
          "onNewToken() updating token on the server... Done. Success: ${defaultSuccessResponse.success}"
        }

        return@Try defaultSuccessResponse.success
      } finally {
        synchronized(this) {
          if (prevLatch === updateTokenLatch) {
            updateTokenLatch.countDown()
          }
        }

        updatingToken.set(false)
      }
    }
  }

  override fun awaitUntilTokenUpdated() {
    val latch = synchronized(this) { updateTokenLatch }
    latch.await()
  }

  companion object {
    private const val TAG = "TokenUpdaterImpl"
  }
}