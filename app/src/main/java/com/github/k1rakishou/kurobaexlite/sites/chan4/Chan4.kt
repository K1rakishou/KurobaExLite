package com.github.k1rakishou.kurobaexlite.sites.chan4

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.html.StaticHtmlColorRepository
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.parser.AbstractSitePostParser
import com.github.k1rakishou.kurobaexlite.helpers.parser.Chan4PostParser
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.appendCookieHeader
import com.github.k1rakishou.kurobaexlite.helpers.util.asFormattedToken
import com.github.k1rakishou.kurobaexlite.helpers.util.domain
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogPagesData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogsData
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.data.local.LoginDetails
import com.github.k1rakishou.kurobaexlite.model.data.local.LoginResult
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchParams
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchResult
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadBookmarkData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.remote.IBoardDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.IBookmarkDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.ICatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.ICatalogPagesDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.IGlobalSearchDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.ILoginDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.ILogoutDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.IThreadDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.chan4.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.sites.FormattingButton
import com.github.k1rakishou.kurobaexlite.sites.RequestModifier
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.github.k1rakishou.kurobaexlite.sites.SiteCaptcha
import com.github.k1rakishou.kurobaexlite.sites.settings.Chan4SiteSettings
import com.github.k1rakishou.kurobaexlite.sites.settings.SiteSettings
import com.squareup.moshi.Moshi
import logcat.logcat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.*

class Chan4(
  private val appContext: Context,
  private val chan4DataSource: Chan4DataSource,
  private val appSettings: AppSettings,
  private val moshi: Moshi,
  private val proxiedOkHttpClient: IKurobaOkHttpClient,
  private val staticHtmlColorRepository: StaticHtmlColorRepository
) : Site {
  private val chan4CatalogInfo by lazy { CatalogInfo(chan4DataSource) }
  private val chan4ThreadInfo by lazy { ThreadInfo(chan4DataSource) }
  private val chan4BoardsInfo by lazy { BoardsInfo(chan4DataSource) }
  private val chan4PostImageInfo by lazy { PostImageInfo() }
  private val chan4RequestModifier by lazy { Chan4RequestModifier(this, appSettings) }
  private val chan4SiteSettings by lazy { Chan4SiteSettings(appContext, moshi, defaultDomain) }
  private val chan4ReplyInfo by lazy { Chan4ReplyInfo(this, proxiedOkHttpClient) }
  private val chan4BookmarkInfo by lazy { BookmarkInfo(chan4DataSource) }
  private val chan4CatalogPagesInfo by lazy { CatalogPagesInfo(chan4DataSource) }
  private val chan4GlobalSearchInfo by lazy { GlobalSearchInfo(chan4DataSource) }
  private val chan4PasscodeInfo by lazy { Chan4PasscodeInfo(chan4DataSource) }
  private val chan4PostParser by lazy { Chan4PostParser(staticHtmlColorRepository) }
  private val icon by lazy { "https://s.4cdn.org/image/favicon.ico".toHttpUrl() }

  override val siteKey: SiteKey = SITE_KEY
  override val readableName: String = "4chan"
  override val firewallChallengeEndpoint: HttpUrl? = null
  override val siteCaptcha: SiteCaptcha = SiteCaptcha.Chan4Captcha
  override val siteSettings: SiteSettings = chan4SiteSettings

  override fun catalogInfo(): Site.CatalogInfo = chan4CatalogInfo
  override fun threadInfo(): Site.ThreadInfo = chan4ThreadInfo
  override fun boardsInfo(): Site.BoardsInfo = chan4BoardsInfo
  override fun postImageInfo(): Site.PostImageInfo = chan4PostImageInfo
  override fun replyInfo(): Site.ReplyInfo = chan4ReplyInfo
  override fun bookmarkInfo(): Site.BookmarkInfo = chan4BookmarkInfo
  override fun catalogPagesInfo(): Site.CatalogPagesInfo = chan4CatalogPagesInfo
  override fun globalSearchInfo(): Site.GlobalSearchInfo = chan4GlobalSearchInfo
  override fun passcodeInfo(): Site.PasscodeInfo = chan4PasscodeInfo

  override fun matchesUrl(httpUrl: HttpUrl): Boolean {
    val domain = httpUrl.domain()
    if (domain.isNullOrEmpty()) {
      return false
    }

    if (siteDomains.any { siteHost -> siteHost.contains(domain) }) {
      return true
    }

    if (mediaDomains.any { siteHost -> siteHost.contains(domain) }) {
      return true
    }

    return false
  }

  override fun parser(): AbstractSitePostParser = chan4PostParser
  override fun icon(): HttpUrl = icon
  override fun requestModifier(): RequestModifier<Site> = chan4RequestModifier as RequestModifier<Site>

  override fun resolveDescriptorFromUrl(url: HttpUrl): ResolvedDescriptor? {
    val parts = url.pathSegments
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
    val threadNo: Long? = parts[2].toLongOrNull()
    var postId: Long? = null
    val fragment = url.fragment

    if (fragment != null) {
      val index = fragment.indexOf("p")
      if (index >= 0) {
        postId = fragment.substring(index + 1).toLongOrNull()
      }
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

  override fun desktopUrl(
    threadDescriptor: ThreadDescriptor,
    postNo: Long?,
    postSubNo: Long?
  ): String {
    return buildString {
      append("https://boards.4chan.org/")
      append(threadDescriptor.boardCode)
      append("/thread/")
      append(threadDescriptor.threadNo)

      if (postNo != null && postNo > 0) {
        append("#p")
        append(postNo)
      }
    }
  }

  override fun iconUrl(iconId: String, params: Map<String, String>): String? {
    return buildString {
      when (iconId) {
        "country" -> {
          val countryCode = requireNotNull(params.get("country_code")) { "Bad params map: ${params}" }

          append("https://s.4cdn.org/image/country/")
          append(countryCode.lowercase(Locale.ENGLISH))
          append(".gif")
        }
        "board_flag" -> {
          val boardFlagCode = requireNotNull(params.get("board_flag_code")) { "Bad params map: ${params}" }
          val boardCode = requireNotNull(params.get("board_code")) { "Bad params map: ${params}" }

          append("https://s.4cdn.org/image/flags/")
          append(boardCode)
          append("/")
          append(boardFlagCode.lowercase(Locale.ENGLISH))
          append(".gif")
        }
        "since4pass" -> {
          append("https://s.4cdn.org/image/minileaf.gif")
        }
      }
    }
  }

  override fun commentFormattingButtons(chanCatalog: ChanCatalog): List<FormattingButton> {
    val boardCode = chanCatalog.catalogDescriptor.boardCode
    val formattingButtons = mutableListOf<FormattingButton>()

    formattingButtons += FormattingButton(
      title = AnnotatedString(">"),
      openTag = ">",
      closeTag = ""
    )

    if (boardCode == "g") {
      formattingButtons += FormattingButton(
        title = AnnotatedString("[c]"),
        openTag = "[code]",
        closeTag = "[/code]"
      )
    }

    if (boardCode == "sci") {
      formattingButtons += FormattingButton(
        title = AnnotatedString("[eqn]"),
        openTag = "[eqn]",
        closeTag = "[/eqn]"
      )

      formattingButtons += FormattingButton(
        title = AnnotatedString("[math]"),
        openTag = "[math]",
        closeTag = "[/math]"
      )
    }

    // TODO: spoiler button for boards that support it

    if (boardCode == "jp" || boardCode == "vip") {
      formattingButtons += FormattingButton(
        title = AnnotatedString("[sjis]"),
        openTag = "[sjis]",
        closeTag = "[/sjis]"
      )
    }

    return formattingButtons
  }

  class CatalogInfo(private val chan4DataSource: Chan4DataSource) : Site.CatalogInfo {

    override fun catalogUrl(boardCode: String): String {
      return "https://a.4cdn.org/${boardCode}/catalog.json"
    }

    override fun catalogDataSource(): ICatalogDataSource<CatalogDescriptor, CatalogData> {
      return chan4DataSource
    }

  }

  class ThreadInfo(private val chan4DataSource: Chan4DataSource) : Site.ThreadInfo {

    override fun fullThreadUrl(boardCode: String, threadNo: Long): String {
      return "https://a.4cdn.org/${boardCode}/thread/${threadNo}.json"
    }

    override fun partialThreadUrl(boardCode: String, threadNo: Long, afterPost: PostDescriptor): String {
      return fullThreadUrl(boardCode, threadNo)
    }

    override fun threadDataSource(): IThreadDataSource<ThreadDescriptor, ThreadData> {
      return chan4DataSource
    }

  }

  class BoardsInfo(private val chan4DataSource: Chan4DataSource) : Site.BoardsInfo {

    override fun boardsUrl(): String {
      return "https://a.4cdn.org/boards.json"
    }

    override fun siteBoardsDataSource(): IBoardDataSource<SiteKey, CatalogsData> {
      return chan4DataSource
    }
  }

  class PostImageInfo : Site.PostImageInfo {

    fun wrapParameters(boardCode: String, tim: Long, extension: String): Map<String, String> {
      return mapOf(
        "boardCode" to boardCode,
        "tim" to tim.toString(),
        "extension" to extension
      )
    }

    override fun thumbnailUrl(params: Map<String, String>): HttpUrl? {
      val boardCode = params["boardCode"] ?: return null
      val tim = params["tim"] ?: return null
      val extension = params["extension"] ?: return null

      return "https://i.4cdn.org/${boardCode}/${tim}s.${extension}".toHttpUrlOrNull()
    }

    override fun fullUrl(params: Map<String, String>): HttpUrl? {
      val boardCode = params["boardCode"] ?: return null
      val tim = params["tim"] ?: return null
      val extension = params["extension"] ?: return null

      return "https://i.4cdn.org/${boardCode}/${tim}.${extension}".toHttpUrlOrNull()
    }

  }

  class BookmarkInfo(private val chan4DataSource: Chan4DataSource) : Site.BookmarkInfo {
    override fun bookmarkUrl(boardCode: String, threadNo: Long): String {
      return "https://a.4cdn.org/${boardCode}/thread/${threadNo}.json"
    }

    override fun bookmarkDataSource(): IBookmarkDataSource<ThreadDescriptor, ThreadBookmarkData> {
      return chan4DataSource
    }
  }

  class CatalogPagesInfo(private val chan4DataSource: Chan4DataSource): Site.CatalogPagesInfo {
    override fun catalogPagesUrl(boardCode: String): String {
      return "https://a.4cdn.org/${boardCode}/threads.json"
    }

    override fun catalogPagesDataSource(): ICatalogPagesDataSource<CatalogDescriptor, CatalogPagesData?> {
      return chan4DataSource
    }
  }

  class GlobalSearchInfo(private val chan4DataSource: Chan4DataSource): Site.GlobalSearchInfo {
    override val resultsPerPage = 10

    override val supportsSiteWideSearch: Boolean = true
    override val supportsCatalogSpecificSearch: Boolean = true

    override fun globalSearchUrl(boardCode: String?, query: String, page: Int): String {
      return buildString {
        append("https://find.4chan.org/?")
        append("q=${query}")

        if (boardCode.isNotNullNorEmpty()) {
          append("&b=${boardCode}")
        }

        if (page > 0) {
          append("&o=${page * resultsPerPage}")
        }
      }
    }

    override fun globalSearchDataSource(): IGlobalSearchDataSource<SearchParams, SearchResult> {
      return chan4DataSource
    }

  }

  class Chan4PasscodeInfo(private val chan4DataSource: Chan4DataSource): Site.PasscodeInfo {

    override fun loginUrl(): String {
      return "https://sys.4chan.org/auth"
    }

    override fun loginDataSource(): ILoginDataSource<LoginDetails, LoginResult> {
      return chan4DataSource as ILoginDataSource<LoginDetails, LoginResult>
    }

    override fun logoutDataSource(): ILogoutDataSource<Unit, Unit> {
      return chan4DataSource
    }
  }

  class Chan4RequestModifier(site: Chan4, appSettings: AppSettings) : RequestModifier<Chan4>(site, appSettings) {

    override suspend fun modifyReplyRequest(requestBuilder: Request.Builder) {
      super.modifyReplyRequest(requestBuilder)

      val passcodeCookie = (site.siteSettings as Chan4SiteSettings).passcodeCookie.read()
      if (passcodeCookie.isNotEmpty()) {
        requestBuilder.appendCookieHeader("pass_id=$passcodeCookie")
      }

      addChan4CookieHeader(site, requestBuilder)
    }

    override suspend fun modifyCaptchaGetRequest(requestBuilder: Request.Builder) {
      super.modifyCaptchaGetRequest(requestBuilder)

      addChan4CookieHeader(site, requestBuilder)
    }

    suspend fun addChan4CookieHeader(site: Chan4, requestBuilder: Request.Builder) {
      val chan4SiteSettings = site.siteSettings as Chan4SiteSettings

      val rememberCaptchaCookies = chan4SiteSettings.rememberCaptchaCookies.read()
      if (!rememberCaptchaCookies) {
        logcat(TAG) { "addChan4CookieHeader(), rememberCaptchaCookies is false" }
        return
      }

      val host = requestBuilder.build().url.host

      val captchaCookie = chan4SiteSettings.chan4CaptchaCookie.read()
      if (captchaCookie.isEmpty()) {
        return
      }

      logcat(TAG) { "addChan4CookieHeader(), host=${host}, captchaCookie=${captchaCookie.asFormattedToken()}" }
      requestBuilder.appendCookieHeader("$CAPTCHA_COOKIE_KEY=${captchaCookie}")
    }

    companion object {
      const val CAPTCHA_COOKIE_KEY = "4chan_pass"
    }
  }

  companion object {
    private const val TAG = "Chan4"
    val SITE_KEY = SiteKey("4chan")

    private val siteDomains = arrayOf(
      "4chan.org"
    )

    private val mediaDomains = arrayOf(
      "4cdn.org"
    )

    private val defaultDomain: String
      get() = siteDomains.first()
  }

}