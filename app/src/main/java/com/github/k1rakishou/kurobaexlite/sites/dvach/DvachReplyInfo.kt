package com.github.k1rakishou.kurobaexlite.sites.dvach

import com.github.k1rakishou.kurobaexlite.features.reply.AttachedMedia
import com.github.k1rakishou.kurobaexlite.helpers.network.ProgressRequestBody
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.model.data.local.ReplyData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.ReplyEvent
import com.github.k1rakishou.kurobaexlite.sites.ReplyResponse
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.github.k1rakishou.kurobaexlite.sites.settings.DvachSiteSettings
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

class DvachReplyInfo(
  private val site: Dvach,
  private val moshi: Moshi,
  private val proxiedOkHttpClient: IKurobaOkHttpClient
) : Site.ReplyInfo {

  override suspend fun replyUrl(chanDescriptor: ChanDescriptor): String {
    return "https://${site.currentDomain}/user/posting"
  }

  override fun sendReply(replyData: ReplyData): Flow<ReplyEvent> {
    return channelFlow {
      send(ReplyEvent.Start)

      try {
        val requestBuilder = Request.Builder()

        val multipartBody = initReplyBody(
          replyData = replyData,
          onProgress = { progress -> trySend(ReplyEvent.Progress(progress)) }
        )

        val replyUrl = replyUrl(replyData.chanDescriptor)
        requestBuilder.url(replyUrl)
        requestBuilder.addHeader("Referer", replyUrl)

        requestBuilder.post(multipartBody)
        site.requestModifier().modifyReplyRequest(requestBuilder)

        val request = requestBuilder.build()

        proxiedOkHttpClient.okHttpClient().suspendCall(request).unwrap().use { response ->
          val replyResponse = processResponse(
            response = response,
            replyChanDescriptor = replyData.chanDescriptor,
            result = response.body?.string() ?: ""
          )

          send(ReplyEvent.Success(replyResponse))
        }

      } catch (error: Throwable) {
        send(ReplyEvent.Error(error))
      }
    }
  }

  private suspend fun processResponse(
    response: Response,
    replyChanDescriptor: ChanDescriptor,
    result: String
  ): ReplyResponse {
    val postingResult = try {
      moshi
        .adapter(PostingResult::class.java)
        .fromJson(result)
    } catch (error: Throwable) {
      logcatError(TAG) { "Couldn't handle server response! (fromJson error) response: \"$result\"" }

      val errorMessage = "Failed process server response, error: ${error.errorMessageOrClassName(userReadable = true)}"
      return ReplyResponse.Error(errorMessage)
    }

    if (postingResult == null) {
      logcatError(TAG) { "Couldn't handle server response! (postingResult == null) response: \"$result\"" }

      val errorMessage = "Failed process server response (postingResult == null)"
      return ReplyResponse.Error(errorMessage)
    }

    if (postingResult.error != null) {
      val errorCode = postingResult.error.code
      val errorText = postingResult.error.message
      val fullErrorMessage = "${errorText} (${errorCode})"

      if (errorCode == INVALID_CAPTCHA_ERROR_CODE || errorText.equals(INVALID_CAPTCHA_ERROR_TEXT, ignoreCase = true)) {
        return ReplyResponse.AuthenticationRequired(
          forgotCaptcha = false,
          mistypedCaptcha = true
        )
      }

      if (replyChanDescriptor is ThreadDescriptor) {
        // Only check for rate limits when replying in threads. Do not do this when creating new
        // threads.
        if (errorCode == RATE_LIMITED_ERROR_CODE || errorText.contains(RATE_LIMITED_PATTERN, ignoreCase = true)) {
          return ReplyResponse.RateLimited(timeToWaitMs = POSTING_COOLDOWN_MS)
        }
      }

      if (errorText.contains(PROBABLY_BANNED_TEXT, ignoreCase = true)) {
        return ReplyResponse.NotAllowedToPost(errorMessage = fullErrorMessage)
      }

      return ReplyResponse.Error(errorMessage = fullErrorMessage)
    }

    if (!response.isSuccessful) {
      return ReplyResponse.Error(errorMessage = "Failed to post, bad response status code: ${response.code}")
    }

    var threadNo = 0L
    var postNo = 0L

    when (replyChanDescriptor) {
      is CatalogDescriptor -> {
        threadNo = postingResult.threadNo ?: 0L
        postNo = threadNo
      }
      is ThreadDescriptor -> {
        threadNo = replyChanDescriptor.threadNo
        postNo = postingResult.postNo ?: 0L
      }
    }

    if (threadNo > 0 && postNo > 0) {
      setDvachHeaders(response.headers)

      val resultPostDescriptor = PostDescriptor.create(
        siteKey = replyChanDescriptor.siteKey,
        boardCode = replyChanDescriptor.boardCode,
        threadNo = threadNo,
        postNo = postNo
      )

      return ReplyResponse.Success(resultPostDescriptor)
    }

    logcatError(TAG) { "Couldn't handle server response! (threadNo: ${threadNo}, postNo=${postNo}) response: \"$result\"" }
    return ReplyResponse.Error(errorMessage = "Failed to post, see the logs for more info")
  }

  private suspend fun setDvachHeaders(headers: Headers) {
    val dvachSiteSettings = (site.siteSettings as DvachSiteSettings)

    val userCodeCookie = headers
      .firstOrNull { cookie -> cookie.second.startsWith(Dvach.USER_CODE_COOKIE_KEY) }
      ?: return

    val userCodeCookieValue = userCodeCookie.second
      .split(";")
      .firstOrNull { value -> value.startsWith(Dvach.USER_CODE_COOKIE_KEY) }
      ?.removePrefix("${Dvach.USER_CODE_COOKIE_KEY}=")

    if (userCodeCookieValue.isNullOrBlank()) {
      return
    }

    dvachSiteSettings.userCodeCookie.write(userCodeCookieValue)
  }

  private fun initReplyBody(
    replyData: ReplyData,
    onProgress: (Float) -> Unit
  ): MultipartBody {
    val chanDescriptor = replyData.chanDescriptor

    val formBuilder = MultipartBody.Builder()
    formBuilder.setType(MultipartBody.FORM)

    if (replyData.chanDescriptor is ThreadDescriptor) {
      formBuilder.addFormDataPart("post", "New Reply")
    } else {
      formBuilder.addFormDataPart("post", "New Thread")
      formBuilder.addFormDataPart("page", "1")
    }

    formBuilder.addFormDataPart("board", chanDescriptor.boardCode)

    if (chanDescriptor is ThreadDescriptor) {
      formBuilder.addFormDataPart("thread", chanDescriptor.threadNo.toString())
    }

    formBuilder.addFormDataPart("name", replyData.name ?: "")
    formBuilder.addFormDataPart("email", replyData.options ?: "")
    formBuilder.addFormDataPart("comment", replyData.message)

    replyData.flag?.flagId?.let { flagId ->
      formBuilder.addFormDataPart("icon", flagId.toString())
    }

    if (chanDescriptor is CatalogDescriptor && replyData.subject.isNotNullNorEmpty()) {
      formBuilder.addFormDataPart("subject", replyData.subject)
    }

    when (val captchaSolution = replyData.captchaSolution) {
      is CaptchaSolution.ChallengeWithSolution -> {
        formBuilder.addFormDataPart("captcha_type", "2chcaptcha")
        formBuilder.addFormDataPart("2chcaptcha_value", captchaSolution.solution)
        formBuilder.addFormDataPart("2chcaptcha_id", captchaSolution.challenge)
      }
      is CaptchaSolution.UsePasscode -> {
        // no-op
      }
      null -> {
        // no-op
      }
    }

    if (replyData.attachedMediaList.isNotEmpty()) {
      replyData.attachedMediaList.forEachIndexed { index, attachedMedia ->
        attachFile(
          fileIndex = index + 1,
          totalFiles = replyData.attachedMediaList.size,
          formBuilder = formBuilder,
          attachedMedia = attachedMedia,
          progressListener = { progress -> onProgress(progress) }
        )
      }
    }

    return formBuilder.build()
  }

  private fun attachFile(
    fileIndex: Int,
    totalFiles: Int,
    formBuilder: MultipartBody.Builder,
    attachedMedia: AttachedMedia,
    progressListener: ProgressRequestBody.ProgressRequestListener,
  ) {
    val mediaType = "application/octet-stream".toMediaType()
    val attachedMediaFile = attachedMedia.asFile

    val progressRequestBody = ProgressRequestBody(
      fileIndex,
      totalFiles,
      attachedMediaFile.asRequestBody(mediaType),
      progressListener
    )

    formBuilder.addFormDataPart("file[]", attachedMedia.actualFileName, progressRequestBody)
  }

  @JsonClass(generateAdapter = true)
  data class PostingResult(
    val result: Int,
    val error: DvachError?,
    @Json(name = "thread")
    val threadNo: Long?,
    @Json(name = "num")
    val postNo: Long?
  )

  @JsonClass(generateAdapter = true)
  data class DvachError(
    val code: Int,
    val message: String
  )

  companion object {
    private const val TAG = "DvachReplyInfo"

    private const val INVALID_CAPTCHA_ERROR_CODE = -5
    private const val RATE_LIMITED_ERROR_CODE = -8

    private const val INVALID_CAPTCHA_ERROR_TEXT = "Капча невалидна"
    private const val RATE_LIMITED_PATTERN = "Вы постите слишком быстро"

    private val POSTING_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(35)

    private const val PROBABLY_BANNED_TEXT = "Постинг запрещён"
  }

}