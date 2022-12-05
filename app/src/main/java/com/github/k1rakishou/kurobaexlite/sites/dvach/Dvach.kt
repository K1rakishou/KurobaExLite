package com.github.k1rakishou.kurobaexlite.sites.dvach

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.parser.AbstractSitePostParser
import com.github.k1rakishou.kurobaexlite.helpers.parser.DvachPostParser
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.appendCookieHeader
import com.github.k1rakishou.kurobaexlite.helpers.util.domain
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogPagesData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogsData
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.data.local.LoginDetails
import com.github.k1rakishou.kurobaexlite.model.data.local.LoginResult
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadBookmarkData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.IBoardDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IBookmarkDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogPagesDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ILoginDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ILogoutDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IThreadDataSource
import com.github.k1rakishou.kurobaexlite.model.source.dvach.DvachDataSource
import com.github.k1rakishou.kurobaexlite.sites.FormattingButton
import com.github.k1rakishou.kurobaexlite.sites.RequestModifier
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.github.k1rakishou.kurobaexlite.sites.SiteCaptcha
import com.github.k1rakishou.kurobaexlite.sites.settings.DvachSiteSettings
import com.github.k1rakishou.kurobaexlite.sites.settings.SiteSettings
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

class Dvach(
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val dvachDataSource: DvachDataSource,
  private val appSettings: AppSettings,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val moshi: Moshi,
) : Site {
  override val siteKey: SiteKey = SITE_KEY
  override val readableName: String = "Dvach"
  override val firewallChallengeEndpoint: HttpUrl? = null
  override val siteCaptcha: SiteCaptcha = SiteCaptcha.DvachCaptcha()
  override val siteSettings: SiteSettings by lazy { DvachSiteSettings(appContext, moshi, defaultDomain) }

  private val dvachSiteSettings: DvachSiteSettings
    get() = siteSettings as DvachSiteSettings

  private val icon: HttpUrl
    get() = "https://${currentDomain()}/favicon.ico".toHttpUrl()

  private val dvachPostParser by lazy { DvachPostParser() }
  private val chan4RequestModifier by lazy { DvachRequestModifier(this, appSettings) }
  private val dvachBoardsInfo by lazy {
    BoardsInfo(
      dvachDataSource = dvachDataSource,
      getCurrentDomain = { currentDomain() }
    )
  }
  private val catalogInfo by lazy { CatalogInfo(dvachDataSource = dvachDataSource, getCurrentDomain = { currentDomain() }) }
  private val threadInfo by lazy { ThreadInfo(dvachDataSource = dvachDataSource, getCurrentDomain = { currentDomain() }) }
  private val postImageInfo by lazy { PostImageInfo(getCurrentDomain = { currentDomain() }) }
  private val catalogPagesInfo by lazy { CatalogPagesInfo(dvachDataSource = dvachDataSource, getCurrentDomain = { currentDomain() }) }
  private val replyInfo by lazy { DvachReplyInfo(site = this, moshi = moshi, proxiedOkHttpClient = proxiedOkHttpClient) }
  private val passcodeInfo by lazy { PasscodeInfo(dvachDataSource = dvachDataSource, getCurrentDomain = { currentDomain() }) }
  private val bookmarkInfo by lazy { BookmarkInfo(dvachDataSource = dvachDataSource, getCurrentDomain = { currentDomain() }) }

  @Volatile
  private var _currentDomain = defaultDomain
  val currentDomain: String
    get() = _currentDomain

  init {
    appScope.launch(Dispatchers.Main.immediate) {
      _currentDomain = dvachSiteSettings.currentDomain.read()
      dvachSiteSettings.currentDomain.listen().collect { domain -> _currentDomain = domain }
    }
  }

  private fun currentDomain(): String {
    return "https://${_currentDomain}".toHttpUrlOrNull()?.domain()?.removeSuffix("/") ?: defaultDomain
  }

  override fun catalogInfo(): Site.CatalogInfo = catalogInfo
  override fun threadInfo(): Site.ThreadInfo = threadInfo
  override fun boardsInfo(): Site.BoardsInfo = dvachBoardsInfo
  override fun postImageInfo(): Site.PostImageInfo = postImageInfo
  override fun catalogPagesInfo(): Site.CatalogPagesInfo = catalogPagesInfo
  override fun replyInfo(): Site.ReplyInfo = replyInfo
  override fun bookmarkInfo(): Site.BookmarkInfo = bookmarkInfo

  override fun globalSearchInfo(): Site.GlobalSearchInfo? {
    // TODO: Dvach support
    return null
  }

  override fun passcodeInfo(): Site.PasscodeInfo = passcodeInfo

  override fun matchesUrl(httpUrl: HttpUrl): Boolean {
    val domain = httpUrl.domain()
    if (domain.isNullOrEmpty()) {
      return false
    }

    return siteDomains.any { siteHost -> siteHost.contains(domain) }
  }

  override fun parser(): AbstractSitePostParser  = dvachPostParser

  override fun icon(): HttpUrl {
    return icon
  }

  override fun resolveDescriptorFromUrl(url: HttpUrl): ResolvedDescriptor? {
    val parts = url.pathSegments.filter { segment -> segment.isNotBlank() }
    if (parts.isEmpty()) {
      logcatError { "Failed to parse \'$url\' (no pathSegments)" }
      return null
    }

    val boardCode = parts[0]

    if (parts.size < 3) {
      // Board mode
      return ResolvedDescriptor.CatalogOrThread(CatalogDescriptor(siteKey, boardCode))
    }

    // Thread mode
    val threadNo: Long? = parts[2].takeWhile { ch -> ch != '.' }.toLongOrNull()
    var postId: Long? = null
    val fragment = url.fragment

    if (fragment != null) {
      postId = fragment.toLongOrNull()
    }

    if (threadNo == null || threadNo <= 0L) {
      logcatError { "Failed to parse \'$url\' (threadNo is bad)" }
      return null
    }

    val threadDescriptor = ThreadDescriptor.create(
      siteKey = siteKey,
      boardCode = boardCode,
      threadNo = threadNo
    )

    if (postId == null || postId <= 0L) {
      return ResolvedDescriptor.CatalogOrThread(threadDescriptor)
    }

    val postDescriptor = PostDescriptor(threadDescriptor, postId)
    return ResolvedDescriptor.Post(postDescriptor)
  }

  override fun requestModifier(): RequestModifier<Site> = chan4RequestModifier as RequestModifier<Site>

  override fun desktopUrl(threadDescriptor: ThreadDescriptor, postNo: Long?, postSubNo: Long?): String {
    return buildString {
      append("https://${currentDomain()}/")
      append(threadDescriptor.boardCode)
      append("/res/")
      append(threadDescriptor.threadNo)
      append(".html")

      if (postNo != null && postNo > 0) {
        append("#")
        append(postNo)
      }
    }
  }

  override fun iconUrl(iconId: String, params: Map<String, String>): String? {
    when (iconId) {
      "country" -> {
        val countryCode = params["country_code"]?.removePrefix("/")
        if (countryCode.isNullOrBlank()) {
          return null
        }

        return "https://${currentDomain()}/${countryCode}"
      }
      "board_flag" -> {
        val boardFlagCode = params["board_flag_code"]?.removePrefix("/")
        if (boardFlagCode.isNullOrBlank()) {
          return null
        }

        return "https://${currentDomain()}/${boardFlagCode}"
      }
      else -> {
        return null
      }
    }
  }

  override fun commentFormattingButtons(chanCatalog: ChanCatalog): List<FormattingButton> {
    val formattingButtons = mutableListOf<FormattingButton>()

    formattingButtons += FormattingButton(
      title = AnnotatedString(">"),
      openTag = ">",
      closeTag = ""
    )

    formattingButtons += FormattingButton(
      title = AnnotatedString("[spoiler]"),
      openTag = "[spoiler]",
      closeTag = "[/spoiler]"
    )

    formattingButtons += FormattingButton(
      title = AnnotatedString("[b]"),
      openTag = "[b]",
      closeTag = "[/b]"
    )

    formattingButtons += FormattingButton(
      title = AnnotatedString("[i]"),
      openTag = "[i]",
      closeTag = "[/i]"
    )

    formattingButtons += FormattingButton(
      title = AnnotatedString("[u]"),
      openTag = "[u]",
      closeTag = "[/u]"
    )

    formattingButtons += FormattingButton(
      title = AnnotatedString("[s]"),
      openTag = "[s]",
      closeTag = "[/s]"
    )

    return formattingButtons
  }

  class BoardsInfo(
    private val dvachDataSource: DvachDataSource,
    private val getCurrentDomain: () -> String
  ) : Site.BoardsInfo {

    override fun boardsUrl(): String {
      return "https://${getCurrentDomain()}/api/mobile/v2/boards"
    }

    override fun siteBoardsDataSource(): IBoardDataSource<SiteKey, CatalogsData> {
      return dvachDataSource
    }
  }

  class CatalogInfo(
    private val dvachDataSource: DvachDataSource,
    private val getCurrentDomain: () -> String
  ) : Site.CatalogInfo {

    override fun catalogUrl(boardCode: String): String {
      return "https://${getCurrentDomain()}/${boardCode}/catalog.json"
    }

    override fun catalogDataSource(): ICatalogDataSource<CatalogDescriptor, CatalogData> {
      return dvachDataSource
    }

  }

  class ThreadInfo(
    private val dvachDataSource: DvachDataSource,
    private val getCurrentDomain: () -> String
  ) : Site.ThreadInfo {

    override fun fullThreadUrl(boardCode: String, threadNo: Long): String {
      return "https://${getCurrentDomain()}/${boardCode}/res/${threadNo}.json"
    }

    override fun partialThreadUrl(boardCode: String, threadNo: Long, afterPost: PostDescriptor): String {
      return "https://${getCurrentDomain()}/api/mobile/v2/after/${boardCode}/${threadNo}/${afterPost.postNo}"
    }

    override fun threadDataSource(): IThreadDataSource<ThreadDescriptor, ThreadData> {
      return dvachDataSource
    }

  }

  class PostImageInfo(
    private val getCurrentDomain: () -> String
  ) : Site.PostImageInfo {

    fun wrapFullImageParameters(path: String): Map<String, String> {
      return mapOf("path" to path)
    }

    fun wrapThumbnailParameters(thumbnail: String): Map<String, String> {
      return mapOf("thumbnail" to thumbnail)
    }

    override fun thumbnailUrl(params: Map<String, String>): HttpUrl? {
      val thumbnail = params["thumbnail"]?.removePrefix("/") ?: return null

      return "https://${getCurrentDomain()}/${thumbnail}".toHttpUrlOrNull()
    }

    override fun fullUrl(params: Map<String, String>): HttpUrl? {
      val path = params["path"]?.removePrefix("/") ?: return null

      return "https://${getCurrentDomain()}/${path}".toHttpUrlOrNull()
    }
  }

  class CatalogPagesInfo(
    private val dvachDataSource: DvachDataSource,
    private val getCurrentDomain: () -> String
  ): Site.CatalogPagesInfo {
    override fun catalogPagesUrl(boardCode: String): String {
      return "https://${getCurrentDomain()}/${boardCode}/catalog.json"
    }

    override fun catalogPagesDataSource(): ICatalogPagesDataSource<CatalogDescriptor, CatalogPagesData?> {
      return dvachDataSource
    }
  }

  class PasscodeInfo(
    private val dvachDataSource: DvachDataSource,
    private val getCurrentDomain: () -> String
  ): Site.PasscodeInfo {

    override fun loginUrl(): String {
      return "https://${getCurrentDomain()}/user/passlogin?json=1"
    }

    override fun loginDataSource(): ILoginDataSource<LoginDetails, LoginResult> {
      return dvachDataSource as ILoginDataSource<LoginDetails, LoginResult>
    }

    override fun logoutDataSource(): ILogoutDataSource<Unit, Unit> {
      return dvachDataSource
    }
  }

  class BookmarkInfo(
    private val dvachDataSource: DvachDataSource,
    private val getCurrentDomain: () -> String
  ): Site.BookmarkInfo {

    override fun bookmarkUrl(boardCode: String, threadNo: Long): String {
      return "https://${getCurrentDomain()}/${boardCode}/res/${threadNo}.json"
    }

    override fun bookmarkDataSource(): IBookmarkDataSource<ThreadDescriptor, ThreadBookmarkData> {
      return dvachDataSource
    }
  }

  private class DvachRequestModifier(
    site: Dvach,
    appSettings: AppSettings
  ) : RequestModifier<Dvach>(site, appSettings) {

    override suspend fun modifyReplyRequest(requestBuilder: Request.Builder) {
      super.modifyReplyRequest(requestBuilder)
      addUserCodeCookie(requestBuilder)

      val passcodeCookie = (site.siteSettings as DvachSiteSettings).passcodeCookie.read()
      if (passcodeCookie.isNotBlank()) {
        requestBuilder.appendCookieHeader("passcode_auth=${passcodeCookie}")
      }
    }

    override suspend fun modifyCaptchaGetRequest(requestBuilder: Request.Builder) {
      super.modifyCaptchaGetRequest(requestBuilder)
      addUserCodeCookie(requestBuilder)
    }

    override suspend fun modifyGetCatalogPagesRequest(requestBuilder: Request.Builder) {
      super.modifyGetCatalogPagesRequest(requestBuilder)
      addUserCodeCookie(requestBuilder)
    }

    override suspend fun modifySearchRequest(requestBuilder: Request.Builder) {
      super.modifySearchRequest(requestBuilder)
      addUserCodeCookie(requestBuilder)
    }

    override suspend fun modifyLoginRequest(requestBuilder: Request.Builder) {
      super.modifyLoginRequest(requestBuilder)
      addUserCodeCookie(requestBuilder)
    }

    override suspend fun modifyGetBoardsRequest(requestBuilder: Request.Builder) {
      super.modifyGetBoardsRequest(requestBuilder)
      addUserCodeCookie(requestBuilder)
    }

    override suspend fun modifyCatalogOrThreadGetRequest(
      chanDescriptor: ChanDescriptor,
      requestBuilder: Request.Builder
    ) {
      super.modifyCatalogOrThreadGetRequest(chanDescriptor, requestBuilder)
      addUserCodeCookie(requestBuilder)
    }

    override suspend fun modifyGetMediaRequest(requestBuilder: Request.Builder) {
      super.modifyGetMediaRequest(requestBuilder)
      addUserCodeCookie(requestBuilder)
    }

    override suspend fun modifyCoilImageRequest(requestUrl: HttpUrl, imageRequestBuilder: ImageRequest.Builder) {
      super.modifyCoilImageRequest(requestUrl, imageRequestBuilder)
      addUserCodeCookie(imageRequestBuilder)
    }

    private suspend fun addUserCodeCookie(
      requestBuilder: Request.Builder
    ) {
      val userCodeCookie = (site.siteSettings as DvachSiteSettings).userCodeCookie.read()
      if (userCodeCookie.isEmpty()) {
        return
      }

      requestBuilder.appendCookieHeader("${USER_CODE_COOKIE_KEY}=${userCodeCookie}")
    }

    private suspend fun addUserCodeCookie(
      imageRequestBuilder: ImageRequest.Builder
    ) {
      val userCodeCookie = (site.siteSettings as DvachSiteSettings).userCodeCookie.read()
      if (userCodeCookie.isEmpty()) {
        return
      }

      imageRequestBuilder.addHeader("Cookie", "${USER_CODE_COOKIE_KEY}=${userCodeCookie}")
    }

  }

  companion object {
    const val USER_CODE_COOKIE_KEY = "usercode_auth"

    val SITE_KEY = SiteKey("Dvach")

    private val siteDomains = arrayOf(
      "2ch.hk",
      "2ch.life",
    )

    private val defaultDomain: String
      get() = siteDomains.first()
  }

}