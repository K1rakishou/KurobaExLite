package com.github.k1rakishou.kurobaexlite.sites.chan4

import com.github.k1rakishou.kurobaexlite.features.reply.AttachedMedia
import com.github.k1rakishou.kurobaexlite.helpers.network.ProgressRequestBody
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.util.asFormattedToken
import com.github.k1rakishou.kurobaexlite.helpers.util.groupOrNull
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.model.data.local.ReplyData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.ReplyEvent
import com.github.k1rakishou.kurobaexlite.sites.ReplyResponse
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.github.k1rakishou.kurobaexlite.sites.settings.Chan4SiteSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import logcat.logcat
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.jsoup.Jsoup
import java.util.regex.Matcher
import java.util.regex.Pattern

class Chan4ReplyInfo(
  private val site: Chan4,
  private val proxiedOkHttpClient: IKurobaOkHttpClient
) : Site.ReplyInfo {

  override suspend fun replyUrl(chanDescriptor: ChanDescriptor): String {
    return "https://sys.4chan.org/${chanDescriptor.boardCode}/post"
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
          setChan4CaptchaHeader(response.headers)

          val replyResponse = processResponse(
            chanDescriptor = replyData.chanDescriptor,
            responseString = response.body?.string() ?: ""
          )

          send(ReplyEvent.Success(replyResponse))
        }

      } catch (error: Throwable) {
        send(ReplyEvent.Error(error))
      }
    }
  }

  private fun processResponse(
    chanDescriptor: ChanDescriptor,
    responseString: String
  ): ReplyResponse {
    val errorMessageMatcher = ERROR_MESSAGE_PATTERN.matcher(responseString)
    if (errorMessageMatcher.find()) {
      val errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text()

      val forgotCaptcha = errorMessage.contains(FORGOT_TO_SOLVE_CAPTCHA, ignoreCase = true)
      val mistypedCaptcha = errorMessage.contains(MISTYPED_CAPTCHA, ignoreCase = true)

      if (forgotCaptcha || mistypedCaptcha) {
        logcatError(TAG) {
          "processResponse() requireAuthentication " +
            "(forgotCaptcha=$forgotCaptcha, mistypedCaptcha=$mistypedCaptcha)"
        }

        return ReplyResponse.AuthenticationRequired(
          forgotCaptcha = forgotCaptcha,
          mistypedCaptcha = mistypedCaptcha
        )
      }

      val notAllowedToPostReason = tryExtractNotAllowedToPostReason(errorMessage)
      if (notAllowedToPostReason != null) {
        return notAllowedToPostReason
      }

      if (chanDescriptor is ThreadDescriptor) {
        // Only check for rate limits when replying in threads. Do not do this when creating new
        // threads.
        val rateLimitMatcher = RATE_LIMITED_PATTERN.matcher(errorMessage)
        if (rateLimitMatcher.find()) {
          return ReplyResponse.RateLimited(extractTimeToWait(rateLimitMatcher))
        }
      }

      return ReplyResponse.Error(errorMessage)
    }

    val threadNoMatcher = THREAD_NO_PATTERN.matcher(responseString)
    if (!threadNoMatcher.find()) {
      logcatError(TAG) { "processResponse() Couldn't handle server response! response: \'$responseString\'" }
      return ReplyResponse.Error("Couldn't handle server response (threadNo not found) see logs for more info!")
    }

    var threadNo = threadNoMatcher.groupOrNull(1)?.toInt()?.toLong() ?: 0L
    val postNo = threadNoMatcher.groupOrNull(2)?.toInt()?.toLong() ?: 0L

    check(threadNo >= 0) { "Bad threadNo: ${threadNo}" }
    check(postNo >= 0) { "Bad postNo: ${postNo}" }

    if (threadNo == 0L) {
      threadNo = postNo
    }

    if (threadNo > 0 && postNo > 0) {
      val resultPostDescriptor = PostDescriptor.create(
        siteKey = chanDescriptor.siteKey,
        boardCode = chanDescriptor.boardCode,
        threadNo = threadNo,
        postNo = postNo,
        postSubNo = 0
      )

      return ReplyResponse.Success(resultPostDescriptor)
    }

    logcatError(TAG) {
      "processResponse() Couldn't handle server response! response = \"$responseString\""
    }

    return ReplyResponse.Error("Couldn't handle server response " +
      "(bad threadNo: \'${threadNo}\' or postNo: \'${postNo}\') see logs for more info!")
  }

  private fun initReplyBody(
    replyData: ReplyData,
    onProgress: (Float) -> Unit
  ): MultipartBody {
    val chanDescriptor = replyData.chanDescriptor
    val formBuilder = MultipartBody.Builder()
    formBuilder.setType(MultipartBody.FORM)
    formBuilder.addFormDataPart("mode", "regist")

    if (replyData.password.isNotNullNorEmpty()) {
      formBuilder.addFormDataPart("pwd", replyData.password)
    }

    if (chanDescriptor is ThreadDescriptor) {
      val threadNo = chanDescriptor.threadNo
      formBuilder.addFormDataPart("resto", threadNo.toString())
    }

    if (replyData.name.isNotNullNorEmpty()) {
      formBuilder.addFormDataPart("name", replyData.name)
    }

    if (replyData.options.isNotNullNorEmpty()) {
      formBuilder.addFormDataPart("email", replyData.options)
    }

    if (chanDescriptor is CatalogDescriptor && replyData.subject.isNotNullNorEmpty()) {
      formBuilder.addFormDataPart("sub", replyData.subject)
    }

    formBuilder.addFormDataPart("com", replyData.message)

    when (val captchaSolution = replyData.captchaSolution) {
      is CaptchaSolution.ChallengeWithSolution -> {
        formBuilder.addFormDataPart("t-challenge", captchaSolution.challenge)
        formBuilder.addFormDataPart("t-response", captchaSolution.solution)
      }
      is CaptchaSolution.UsePasscode -> {
        // no-op
      }
      null -> {
        // no-op
      }
    }

    if (replyData.flag != null) {
      formBuilder.addFormDataPart("flag", replyData.flag.key)
    }

    val attachedMedia = replyData.attachedMediaList.firstOrNull()
    if (attachedMedia != null) {
      attachFile(
        formBuilder = formBuilder,
        attachedMedia = attachedMedia,
        progressListener = { progress -> onProgress(progress) }
      )

      // TODO(KurobaEx):
      //  if (replyFileMetaInfo.spoiler) {
      //    formBuilder.addFormDataPart("spoiler", "on")
      //  }
    }

    return formBuilder.build()
  }

  private fun attachFile(
    formBuilder: MultipartBody.Builder,
    attachedMedia: AttachedMedia,
    progressListener: ProgressRequestBody.ProgressRequestListener,
  ) {
    val mediaType = "application/octet-stream".toMediaType()
    val attachedMediaFile = attachedMedia.asFile

    val progressRequestBody = ProgressRequestBody(
      fileIndex = 1,
      totalFiles = 1,
      delegate = attachedMediaFile.asRequestBody(mediaType),
      progressListener
    )

    formBuilder.addFormDataPart(
      "upfile",
      attachedMedia.actualFileName,
      progressRequestBody
    )
  }

  private suspend fun setChan4CaptchaHeader(headers: Headers) {
    val chan4Settings = site.siteSettings as Chan4SiteSettings

    if (!chan4Settings.rememberCaptchaCookies.read()) {
      logcat(TAG) { "setChan4CaptchaHeader() rememberCaptchaCookies is false" }
      return
    }

    val wholeCookieHeader = headers
      .filter { (key, _) -> key.contains(SET_COOKIE_HEADER, ignoreCase = true) }
      .firstOrNull { (_, value) -> value.startsWith(CAPTCHA_COOKIE_PREFIX) }
      ?.second

    if (wholeCookieHeader.isNullOrEmpty()) {
      logcat(TAG) { "setChan4CaptchaHeader() Set-Cookie header not found" }
      return
    }

    val newCookie = wholeCookieHeader
      .substringAfter(CAPTCHA_COOKIE_PREFIX)
      .substringBefore(';')

    val domain = wholeCookieHeader
      .substringAfter(DOMAIN_PREFIX)
      .substringBefore(';')

    logcat(TAG) { "setChan4CaptchaHeader() newCookie='${newCookie.asFormattedToken()}', " +
      "domain='${domain}', wholeCookieHeader='${wholeCookieHeader}'" }

    if (newCookie.isEmpty()) {
      logcat(TAG) { "setChan4CaptchaHeader() newCookie is empty" }
      return
    }

    if (domain.isEmpty()) {
      logcat(TAG) { "setChan4CaptchaHeader() domain is empty" }
      return
    }

    val oldCookie = chan4Settings.chan4CaptchaCookie.read()

    logcat(TAG) {
      "oldCookie='${oldCookie.asFormattedToken()}', " +
        "newCookie='${newCookie.asFormattedToken()}', " +
        "domain='${domain}'"
    }

    if (oldCookie != null && oldCookie.isNotEmpty()) {
      logcat(TAG) { "setChan4CaptchaHeader() cookie is still ok. oldCookie='${oldCookie.asFormattedToken()}'" }
      return
    }

    logcat(TAG) { "setChan4CaptchaHeader() cookie needs to be updated. " +
      "oldCookie='${oldCookie.asFormattedToken()}', domain='${domain}'" }

    if (domain.isNullOrEmpty() || newCookie.isNullOrEmpty()) {
      logcat(TAG) { "setChan4CaptchaHeader() failed to parse 4chan_pass " +
        "cookie (${newCookie.asFormattedToken()}) or domain (${domain})" }
      return
    }

    chan4Settings.chan4CaptchaCookie.write(newCookie)
  }

  private fun extractTimeToWait(rateLimitMatcher: Matcher): Long {
    val minutes = (rateLimitMatcher.groupOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 60)
    val seconds = (rateLimitMatcher.groupOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, 60)

    return ((minutes * 60) + seconds) * 1000L
  }

  private fun tryExtractNotAllowedToPostReason(errorMessage: String?): ReplyResponse.NotAllowedToPost? {
    if (errorMessage == null) {
      return null
    }

    val isBannedFound = errorMessage.contains(PROBABLY_BANNED_TEXT)
    if (isBannedFound) {
      return ReplyResponse.NotAllowedToPost(errorMessage)
    }

    val isWarnedFound = errorMessage.contains(PROBABLY_WARNED_TEXT)
    if (isWarnedFound) {
      return ReplyResponse.NotAllowedToPost(errorMessage)
    }

    if (errorMessage.contains(PROBABLY_IP_RANGE_BLOCKED)) {
      return ReplyResponse.NotAllowedToPost(errorMessage)
    }

    return null
  }

  companion object {
    private const val TAG = "Chan4ReplyInfo"

    private const val PROBABLY_BANNED_TEXT = "banned"
    private const val PROBABLY_WARNED_TEXT = "warned"
    private const val PROBABLY_IP_RANGE_BLOCKED = "Posting from your IP range has been blocked due to abuse"
    private const val FORGOT_TO_SOLVE_CAPTCHA = "Error: You forgot to solve the CAPTCHA"
    private const val MISTYPED_CAPTCHA = "Error: You seem to have mistyped the CAPTCHA"

    private val THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->")
    private val ERROR_MESSAGE_PATTERN = Pattern.compile("\"errmsg\"[^>]*>(.*?)</span")

    // Error: You must wait 1 minute 17 seconds before posting a duplicate reply.
    // Error: You must wait 2 minutes 1 second before posting a duplicate reply.
    // Error: You must wait 17 seconds before posting a duplicate reply.
    private val RATE_LIMITED_PATTERN = Pattern.compile("must wait (?:(\\d+)\\s+minutes?)?.*?(?:(\\d+)\\s+seconds?)")

    private const val SET_COOKIE_HEADER = "set-cookie"
    private const val CAPTCHA_COOKIE_PREFIX = "4chan_pass="
    private const val DOMAIN_PREFIX = "domain="
  }

}