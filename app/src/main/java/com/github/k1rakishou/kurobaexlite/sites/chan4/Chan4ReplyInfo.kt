package com.github.k1rakishou.kurobaexlite.sites.chan4

import android.webkit.MimeTypeMap
import com.github.k1rakishou.kurobaexlite.helpers.network.CloudFlareInterceptor
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.Generators
import com.github.k1rakishou.kurobaexlite.helpers.util.asFormattedToken
import com.github.k1rakishou.kurobaexlite.helpers.util.domain
import com.github.k1rakishou.kurobaexlite.helpers.util.extractFileNameExtension
import com.github.k1rakishou.kurobaexlite.helpers.util.groupOrNull
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.model.data.local.ReplyData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.ReplyEvent
import com.github.k1rakishou.kurobaexlite.sites.ReplyResponse
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.github.k1rakishou.kurobaexlite.sites.settings.Chan4SiteSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import logcat.logcat
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.koin.core.context.GlobalContext
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern

class Chan4ReplyInfo(
  private val site: Chan4,
  private val proxiedOkHttpClient: IKurobaOkHttpClient
) : Site.ReplyInfo {
  private val appSettings: AppSettings by lazy { GlobalContext.get().get() }

  override suspend fun replyUrl(chanDescriptor: ChanDescriptor): String {
    return "https://sys.4chan.org/${chanDescriptor.boardCode}/post"
  }

  override fun sendReply(replyData: ReplyData): Flow<ReplyEvent> {
    return channelFlow {
      send(ReplyEvent.Start)

      try {
        val request = run {
          val requestBuilder = Request.Builder()
          val replyUrl = replyUrl(replyData.chanDescriptor)

          requestBuilder.url(replyUrl)

          val boundary = "------WebKitFormBoundary${Generators.generateHttpBoundary()}"
          val request = buildRequest(
            requestBuilder = requestBuilder,
            replyData = replyData,
            replyUrl = replyUrl,
            boundary = boundary
          ).toRequestBody()

          requestBuilder.post(request)
          site.requestModifier().modifyReplyRequest(requestBuilder)

          requestBuilder.build()
        }

        proxiedOkHttpClient.okHttpClient().suspendCall(request).unwrap().use { response ->
          updateCookies(response.headers)

          val replyResponse = processResponse(
            chanDescriptor = replyData.chanDescriptor,
            responseString = response.body?.string() ?: ""
          )

          send(ReplyEvent.Success(replyResponse))
        }

      } catch (error: Throwable) {
        send(ReplyEvent.Error(error))
      }
    }.flowOn(Dispatchers.IO)
  }

  private suspend fun buildRequest(
    requestBuilder: Request.Builder,
    replyData: ReplyData,
    replyUrl: String,
    boundary: String
  ): String {
    val capacity = replyData.attachedMediaList.sumOf { attachedMedia -> attachedMedia.asFile.length() } + 4096
    val requestBody = buildRequestBody("--${boundary}", replyData)

    val request = buildString(capacity = capacity.toInt()) {
      arrayOf("Referer", "User-Agent", "Accept-Encoding", "Cookie", "Content-Type", "Content-Length", "Host", "Connection")
        .forEach { header -> requestBuilder.removeHeader(header) }

      requestBuilder.addHeader("Host", "sys.4chan.org")
      requestBuilder.addHeader("User-Agent", appSettings.userAgent.read())
      requestBuilder.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
      requestBuilder.addHeader("Accept-Language", "en-US,en;q=0.5")
      requestBuilder.addHeader("Accept-Encoding", "gzip")
      requestBuilder.addHeader("Content-Type", "multipart/form-data; boundary=${boundary}")
      requestBuilder.addHeader("Content-Length", "${requestBody.length}")
      requestBuilder.addHeader("Origin", "https://boards.4chan.org")
      requestBuilder.addHeader("Connection", "Keep-Alive")
      requestBuilder.addHeader("Referer", replyUrl)
      requestBuilder.addHeader("Cookie", readCookies(replyUrl.toHttpUrl()))
      requestBuilder.addHeader("Sec-Fetch-Dest", "document")
      requestBuilder.addHeader("Sec-Fetch-Mode", "navigate")
      requestBuilder.addHeader("Sec-Fetch-Site", "same-site")
      requestBuilder.addHeader("Sec-Fetch-User", "?1")
      append(requestBody)
      appendRequestLine()
    }

    return request
  }

  private fun buildRequestBody(boundary: String, replyData: ReplyData): String {
    val capacity = replyData.attachedMediaList.sumOf { attachedMedia -> attachedMedia.asFile.length() } + 1024

    fun StringBuilder.appendFormDataSegment(name: String, value: String?) {
      val actualValue = value ?: ""

      appendRequestLine(boundary)
      appendRequestLine("Content-Disposition: form-data; name=\"${name}\"")
      appendRequestLine("Content-Length: ${actualValue.length}")
      appendRequestLine()
      appendRequestLine(actualValue)
    }

    val chanDescriptor = replyData.chanDescriptor

    return buildString(capacity = capacity.toInt()) {
      appendFormDataSegment("mode", "regist")
      appendFormDataSegment("pwd", Generators.generateRandomHexString(symbolsCount = 16))

      if (chanDescriptor is ThreadDescriptor) {
        val threadNo = chanDescriptor.threadNo
        appendFormDataSegment("resto", threadNo.toString())
      }

      appendFormDataSegment("com", replyData.message)

      when (val captchaSolution = replyData.captchaSolution) {
        is CaptchaSolution.ChallengeWithSolution -> {
          appendFormDataSegment("t-challenge", captchaSolution.challenge)
          appendFormDataSegment("t-response", captchaSolution.solution)
        }
        is CaptchaSolution.UsePasscode -> {
          // no-op
        }
        null -> {
          // no-op
        }
      }

      val attachedMedia = replyData.attachedMediaList.firstOrNull()
      if (attachedMedia != null) {
        val extension = attachedMedia.fileName.extractFileNameExtension()
          ?: throw IOException("AttachedFileHasNoName")
        val fileChars = attachedMedia.asFile.readText().toCharArray()

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

        appendRequestLine(boundary)
        appendRequestLine("Content-Disposition: form-data; name=\"upfile\"; filename=\"${attachedMedia.fileName}\"")
        appendRequestLine("Content-Type: ${mimeType}")
        appendRequestLine()
        appendRequestLine(fileChars)
      }

      append("${boundary}--")
    }
  }

  private suspend fun readCookies(requestUrl: HttpUrl): String {
    val domainOrHost = requestUrl.domain() ?: requestUrl.host
    val host = requestUrl.host

    val cloudflareCookie = site.siteSettings.cloudFlareClearanceCookie.get(domainOrHost)

    return buildString {
      if (cloudflareCookie.isNotNullNorEmpty()) {
        logcat(TAG) { "readCookies() domainOrHost=${domainOrHost}, cf_clearance=${cloudflareCookie.asFormattedToken()}" }
        append("${CloudFlareInterceptor.CF_CLEARANCE}=$cloudflareCookie")
      }

      val chan4SiteSettings = site.siteSettings as Chan4SiteSettings

      val rememberCaptchaCookies = chan4SiteSettings.rememberCaptchaCookies.read()
      if (rememberCaptchaCookies) {
        val captchaCookie = chan4SiteSettings.chan4CaptchaCookie.read()
        if (captchaCookie.isNotBlank()) {
          logcat(TAG) { "readCookies() host=${host}, captchaCookie=${captchaCookie.asFormattedToken()}" }

          if (isNotEmpty()) {
            append("; ")
          }

          append("${Chan4.Chan4RequestModifier.CAPTCHA_COOKIE_KEY}=${captchaCookie}")
        }
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

  private suspend fun updateCookies(headers: Headers) {
    val chan4Settings = site.siteSettings as Chan4SiteSettings

    if (!chan4Settings.rememberCaptchaCookies.read()) {
      logcat(TAG) { "updateCookies() rememberCaptchaCookies is false" }
      return
    }

    val wholeCookieHeader = headers
      .filter { (key, _) -> key.contains(SET_COOKIE_HEADER, ignoreCase = true) }
      .firstOrNull { (_, value) -> value.startsWith(CAPTCHA_COOKIE_PREFIX) }
      ?.second

    if (wholeCookieHeader.isNullOrEmpty()) {
      logcat(TAG) { "updateCookies() Set-Cookie header not found" }
      return
    }

    val newCookie = wholeCookieHeader
      .substringAfter(CAPTCHA_COOKIE_PREFIX)
      .substringBefore(';')

    val domain = wholeCookieHeader
      .substringAfter(DOMAIN_PREFIX)
      .substringBefore(';')

    logcat(TAG) { "updateCookies() newCookie='${newCookie.asFormattedToken()}', " +
      "domain='${domain}', wholeCookieHeader='${wholeCookieHeader}'" }

    if (newCookie.isEmpty()) {
      logcat(TAG) { "updateCookies() newCookie is empty" }
      return
    }

    if (domain.isEmpty()) {
      logcat(TAG) { "updateCookies() domain is empty" }
      return
    }

    val oldCookie = chan4Settings.chan4CaptchaCookie.read()

    logcat(TAG) {
      "oldCookie='${oldCookie.asFormattedToken()}', " +
        "newCookie='${newCookie.asFormattedToken()}', " +
        "domain='${domain}'"
    }

    if (oldCookie != null && oldCookie.isNotEmpty() && oldCookie == newCookie) {
      logcat(TAG) { "updateCookies() cookie is still ok. oldCookie='${oldCookie.asFormattedToken()}'" }
      return
    }

    logcat(TAG) { "updateCookies() cookie needs to be updated. " +
      "oldCookie='${oldCookie.asFormattedToken()}', domain='${domain}'" }

    if (domain.isNullOrEmpty() || newCookie.isNullOrEmpty()) {
      logcat(TAG) { "updateCookies() failed to parse 4chan_pass " +
        "cookie (${newCookie.asFormattedToken()}) or domain (${domain})" }
      return
    }

    chan4Settings.chan4CaptchaCookie.write(newCookie)
    logcat(TAG) { "updateCookies() successfully updated cookie with '${newCookie.asFormattedToken()}'" }
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

  private fun StringBuilder.appendRequestLine(): StringBuilder = append("\r\n")
  private fun StringBuilder.appendRequestLine(value: String?): StringBuilder = append(value).appendRequestLine()
  private fun StringBuilder.appendRequestLine(value: CharArray): StringBuilder = append(value).appendRequestLine()

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