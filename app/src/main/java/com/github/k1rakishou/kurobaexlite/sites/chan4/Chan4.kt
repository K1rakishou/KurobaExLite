package com.github.k1rakishou.kurobaexlite.sites.chan4

import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.appendCookieHeader
import com.github.k1rakishou.kurobaexlite.helpers.asFormattedToken
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.parser.AbstractSitePostParser
import com.github.k1rakishou.kurobaexlite.helpers.parser.Chan4PostParser
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogPagesData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogsData
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchParams
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchResult
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadBookmarkData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.IBoardDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IBookmarkDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogPagesDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IGlobalSearchDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IThreadDataSource
import com.github.k1rakishou.kurobaexlite.model.source.chan4.Chan4DataSource
import com.github.k1rakishou.kurobaexlite.sites.RequestModifier
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.github.k1rakishou.kurobaexlite.sites.SiteCaptcha
import com.github.k1rakishou.kurobaexlite.sites.settings.Chan4SiteSettings
import com.github.k1rakishou.kurobaexlite.sites.settings.SiteSettings
import com.squareup.moshi.Moshi
import java.util.Locale
import logcat.logcat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.koin.java.KoinJavaComponent.inject

class Chan4(
  private val appContext: Context
) : Site {
  private val chan4DataSource by inject<Chan4DataSource>(Chan4DataSource::class.java)
  private val appSettings by inject<AppSettings>(AppSettings::class.java)
  private val moshi by inject<Moshi>(Moshi::class.java)
  private val proxiedOkHttpClient by inject<ProxiedOkHttpClient>(ProxiedOkHttpClient::class.java)

  private val chan4CatalogInfo by lazy { CatalogInfo(chan4DataSource) }
  private val chan4ThreadInfo by lazy { ThreadInfo(chan4DataSource) }
  private val chan4BoardsInfo by lazy { BoardsInfo(chan4DataSource) }
  private val chan4PostImageInfo by lazy { PostImageInfo() }
  private val chan4RequestModifier by lazy { Chan4RequestModifier(this, appSettings) }
  private val chan4SiteSettings by lazy { Chan4SiteSettings(appContext, moshi) }
  private val chan4ReplyInfo by lazy { Chan4ReplyInfo(this, proxiedOkHttpClient) }
  private val chan4BookmarkInfo by lazy { BookmarkInfo(chan4DataSource) }
  private val chan4CatalogPagesInfo by lazy { CatalogPagesInfo(chan4DataSource) }
  private val chan4GlobalSearchInfo by lazy { GlobalSearchInfo(chan4DataSource) }

  private val chan4PostParser by lazy { Chan4PostParser() }
  private val icon by lazy { "https://s.4cdn.org/image/favicon.ico".toHttpUrl() }

  override val siteKey: SiteKey = SITE_KEY
  override val readableName: String = "4chan"
  override val siteCaptcha: SiteCaptcha = SiteCaptcha.Chan4Captcha
  override val siteSettings: SiteSettings = chan4SiteSettings

  override fun catalogInfo(): Site.CatalogInfo = chan4CatalogInfo
  override fun threadInfo(): Site.ThreadInfo = chan4ThreadInfo
  override fun boardsInfo(): Site.BoardsInfo = chan4BoardsInfo
  override fun postImageInfo(): Site.PostImageInfo = chan4PostImageInfo
  override fun replyInfo(): Site.ReplyInfo = chan4ReplyInfo
  override fun bookmarkInfo(): Site.BookmarkInfo = chan4BookmarkInfo
  override fun catalogPagesInfo(): Site.CatalogPagesInfo = chan4CatalogPagesInfo
  override fun globalSearchInfo(): Site.GlobalSearchInfo? = chan4GlobalSearchInfo

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
      append("https://boards.4channel.org/")
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

  class CatalogInfo(private val chan4DataSource: Chan4DataSource) : Site.CatalogInfo {

    override fun catalogUrl(boardCode: String): String {
      return "https://a.4cdn.org/${boardCode}/catalog.json"
    }

    override fun catalogDataSource(): ICatalogDataSource<CatalogDescriptor, CatalogData> {
      return chan4DataSource
    }

  }

  class ThreadInfo(private val chan4DataSource: Chan4DataSource) : Site.ThreadInfo {

    override fun threadUrl(boardCode: String, threadNo: Long): String {
      return "https://a.4cdn.org/${boardCode}/thread/${threadNo}.json"
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
    override fun thumbnailUrl(boardCode: String, tim: Long, extension: String): String {
      return "https://i.4cdn.org/${boardCode}/${tim}s.${extension}"
    }

    override fun fullUrl(boardCode: String, tim: Long, extension: String): String {
      return "https://i.4cdn.org/${boardCode}/${tim}.${extension}"
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

    override fun globalSearchUrl(boardCode: String, query: String, page: Int): String {
      return buildString {
        append("https://find.4chan.org/?")
        append("q=${query}")
        append("&b=${boardCode}")

        if (page > 0) {
          append("&o=${page * resultsPerPage}")
        }
      }
    }

    override fun globalSearchUrl(query: String, page: Int): String {
      return buildString {
        append("https://find.4chan.org/?")
        append("q=${query}")

        if (page > 0) {
          append("&o=${page * resultsPerPage}")
        }
      }
    }

    override fun globalSearchDataSource(): IGlobalSearchDataSource<SearchParams, SearchResult> {
      return chan4DataSource
    }

  }

  class Chan4RequestModifier(site: Chan4, appSettings: AppSettings) : RequestModifier<Chan4>(site, appSettings) {

    override suspend fun modifyReplyRequest(site: Chan4, requestBuilder: Request.Builder) {
      super.modifyReplyRequest(site, requestBuilder)

      addChan4CookieHeader(site, requestBuilder)
    }

    override suspend fun modifyCaptchaGetRequest(site: Chan4, requestBuilder: Request.Builder) {
      super.modifyCaptchaGetRequest(site, requestBuilder)

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

      val captchaCookie = when {
        host.contains("4channel") -> chan4SiteSettings.channel4CaptchaCookie.read()
        host.contains("4chan") -> chan4SiteSettings.chan4CaptchaCookie.read()
        else -> {
          logcatError(TAG) { "Unexpected host: '$host'" }
          return
        }
      }

      if (captchaCookie.isEmpty()) {
        return
      }

      logcat(TAG) { "addChan4CookieHeader(), host=${host}, captchaCookie=${captchaCookie.asFormattedToken()}" }
      requestBuilder.appendCookieHeader("$CAPTCHA_COOKIE_KEY=${captchaCookie}")
    }

    companion object {
      private const val CAPTCHA_COOKIE_KEY = "4chan_pass"
    }
  }

  companion object {
    private const val TAG = "Chan4"
    val SITE_KEY = SiteKey("4chan")
  }

}