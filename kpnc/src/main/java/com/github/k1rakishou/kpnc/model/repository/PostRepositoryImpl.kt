package com.github.k1rakishou.kpnc.model.repository

import android.content.SharedPreferences
import com.github.k1rakishou.kpnc.AppConstants
import com.github.k1rakishou.kpnc.helpers.Try
import com.github.k1rakishou.kpnc.helpers.isNotNullNorBlank
import com.github.k1rakishou.kpnc.helpers.suspendConvertWithJsonAdapter
import com.github.k1rakishou.kpnc.helpers.unwrap
import com.github.k1rakishou.kpnc.model.GenericClientException
import com.github.k1rakishou.kpnc.model.data.Endpoints
import com.github.k1rakishou.kpnc.model.data.network.ApplicationType
import com.github.k1rakishou.kpnc.model.data.network.UnwatchPostRequest
import com.github.k1rakishou.kpnc.model.data.network.UnwatchPostResponseWrapper
import com.github.k1rakishou.kpnc.model.data.network.WatchPostRequest
import com.github.k1rakishou.kpnc.model.data.network.WatchPostResponseWrapper
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PostRepositoryImpl(
  private val sharedPrefs: SharedPreferences,
  private val endpoints: Endpoints,
  private val moshi: Moshi,
  private val okHttpClient: OkHttpClient
) : PostRepository {

  override suspend fun watchPost(postUrl: String): Result<Boolean> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val userId = sharedPrefs.getString(AppConstants.PrefKeys.USER_ID, null)
          ?.takeIf { it.isNotNullNorBlank() }
          ?: throw GenericClientException("UserId is not set")
        val instanceAddress = sharedPrefs.getString(AppConstants.PrefKeys.INSTANCE_ADDRESS, null)
          ?.takeIf { it.isNotNullNorBlank() }
          ?: throw GenericClientException("InstanceAddress is not set")

        val watchPostRequest = WatchPostRequest(
          userId = userId,
          postUrl = postUrl,
          applicationType = ApplicationType.fromFlavor().value
        )

        val watchPostRequestJson = moshi.adapter(WatchPostRequest::class.java)
          .toJson(watchPostRequest)

        val requestBody = watchPostRequestJson.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
          .url(endpoints.watchPost(instanceAddress))
          .post(requestBody)
          .build()

        val watchPostResponseWrapperAdapter = moshi
          .adapter<WatchPostResponseWrapper>(WatchPostResponseWrapper::class.java)

        okHttpClient.suspendConvertWithJsonAdapter(
          request = request,
          adapter = watchPostResponseWrapperAdapter
        )
          .unwrap()
          .dataOrThrow()
          .ensureSuccess()

        return@Try true
      }
    }
  }

  override suspend fun unwatchPost(postUrl: String): Result<Boolean> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val userId = sharedPrefs.getString(AppConstants.PrefKeys.USER_ID, null)
          ?.takeIf { it.isNotNullNorBlank() }
          ?: throw GenericClientException("UserId is not set")
        val instanceAddress = sharedPrefs.getString(AppConstants.PrefKeys.INSTANCE_ADDRESS, null)
          ?.takeIf { it.isNotNullNorBlank() }
          ?: throw GenericClientException("InstanceAddress is not set")

        val unwatchPostRequest = UnwatchPostRequest(
          userId = userId,
          postUrl = postUrl,
          applicationType = ApplicationType.fromFlavor().value
        )

        val unwatchPostRequestJson = moshi.adapter(UnwatchPostRequest::class.java)
          .toJson(unwatchPostRequest)

        val requestBody = unwatchPostRequestJson.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
          .url(endpoints.unwatchPost(instanceAddress))
          .post(requestBody)
          .build()

        val unwatchPostResponseWrapperAdapter = moshi
          .adapter<UnwatchPostResponseWrapper>(UnwatchPostResponseWrapper::class.java)

        okHttpClient.suspendConvertWithJsonAdapter(
          request = request,
          adapter = unwatchPostResponseWrapperAdapter
        )
          .unwrap()
          .dataOrThrow()
          .ensureSuccess()

        return@Try true
      }
    }
  }

  companion object {
    private const val TAG = "PostRepositoryImpl"
  }

}