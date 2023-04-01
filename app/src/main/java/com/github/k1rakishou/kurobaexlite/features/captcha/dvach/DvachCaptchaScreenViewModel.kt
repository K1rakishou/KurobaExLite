package com.github.k1rakishou.kurobaexlite.features.captcha.dvach

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendConvertWithJsonAdapter
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.sites.SiteCaptcha
import com.github.k1rakishou.kurobaexlite.sites.dvach.Dvach
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.logcat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class DvachCaptchaScreenViewModel(
  private val proxiedOkHttpClient: IKurobaOkHttpClient,
  private val siteManager: SiteManager,
  private val moshi: Moshi,
) : BaseViewModel() {

  private var activeJob: Job? = null
  var captchaInfoToShow = mutableStateOf<AsyncData<CaptchaInfo>>(AsyncData.Uninitialized)
  var currentInputValue = mutableStateOf<TextFieldValue>(TextFieldValue())

  fun requestCaptcha() {
    activeJob?.cancel()
    activeJob = null
    currentInputValue.value = TextFieldValue()

    activeJob = viewModelScope.launch {
      val captchaIdUrl = (siteManager.bySiteKey(Dvach.SITE_KEY) as? Dvach)
        ?.let { dvach -> (dvach.siteCaptcha as SiteCaptcha.DvachCaptcha).formatCaptchaRequestUrl(dvach.currentDomain) }

      if (captchaIdUrl.isNullOrBlank()) {
        captchaInfoToShow.value = AsyncData.Error(DvachCaptchaError("Failed to get Dvach captcha request url"))
        return@launch
      }

      captchaInfoToShow.value = AsyncData.Loading
      val result = Result.Try { requestCaptchaIdInternal(captchaIdUrl) }

      captchaInfoToShow.value = if (result.isFailure) {
        AsyncData.Error(result.exceptionOrThrow())
      } else {
        AsyncData.Data(result.getOrThrow())
      }
    }
  }

  private suspend fun requestCaptchaIdInternal(captchaIdUrl: String): CaptchaInfo {
    logcat(TAG) { "requestCaptchaInternal() requesting $captchaIdUrl" }

    val requestBuilder = Request.Builder()
      .url(captchaIdUrl)
      .get()

    siteManager.bySiteKey(Dvach.SITE_KEY)?.let { site ->
      site.requestModifier().modifyCaptchaGetRequest(requestBuilder)
    }

    val request = requestBuilder.build()
    val captchaInfoAdapter = moshi.adapter(CaptchaInfo::class.java)

    val captchaInfo = proxiedOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
      request = request,
      adapter = captchaInfoAdapter
    ).unwrap()

    if (captchaInfo == null) {
      throw DvachCaptchaError("Failed to convert json into CaptchaInfo")
    }

    if (!captchaInfo.isValidDvachCaptcha()) {
      throw DvachCaptchaError("Invalid dvach captcha info: ${captchaInfo}")
    }

    return captchaInfo
  }

  fun cleanup() {
    currentInputValue.value = TextFieldValue()
    captchaInfoToShow.value = AsyncData.Uninitialized

    activeJob?.cancel()
    activeJob = null
  }

  class DvachCaptchaError(message: String) : Exception(message)

  @JsonClass(generateAdapter = true)
  data class CaptchaInfo(
    val id: String?,
    val type: String?,
    val input: String?
  ) {
    fun isValidDvachCaptcha(): Boolean {
      return id.isNotNullNorEmpty() && type == "2chcaptcha"
    }

    fun fullRequestUrl(siteManager: SiteManager): HttpUrl? {
      if (id == null) {
        return null
      }

      val dvach = siteManager.bySiteKey(Dvach.SITE_KEY) as? Dvach
        ?: return null

      return "https://${dvach.currentDomain}/api/captcha/2chcaptcha/show?id=$id".toHttpUrl()
    }
  }

  companion object {
    private const val TAG = "DvachCaptchaLayoutViewModel"
  }

}