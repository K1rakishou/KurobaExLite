package com.github.k1rakishou.kurobaexlite.sites.dvach

import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.parser.AbstractSitePostParser
import com.github.k1rakishou.kurobaexlite.helpers.parser.DvachPostParser
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.domain
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogsData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.IBoardDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogDataSource
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

class Dvach(
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val dvachDataSource: DvachDataSource,
  private val appSettings: AppSettings,
  private val moshi: Moshi,
) : Site {
  override val siteKey: SiteKey = SITE_KEY
  override val readableName: String = "Dvach"
  override val firewallChallengeEndpoint: HttpUrl? = null
  override val siteCaptcha: SiteCaptcha = SiteCaptcha.DvachCaptcha
  override val siteSettings: SiteSettings by lazy { DvachSiteSettings(appContext, moshi, defaultDomain) }

  private val dvachSiteSettings: DvachSiteSettings
    get() = siteSettings as DvachSiteSettings

  private val icon: HttpUrl
    get() = "https://${getCurrentDomain()}/favicon.ico".toHttpUrl()

  private val dvachPostParser by lazy { DvachPostParser() }
  private val chan4RequestModifier by lazy { DvachRequestModifier(this, appSettings) }
  private val dvachBoardsInfo by lazy {
    BoardsInfo(
      dvachDataSource = dvachDataSource,
      getCurrentDomain = { getCurrentDomain() }
    )
  }
  private val catalogInfo by lazy { CatalogInfo(dvachDataSource = dvachDataSource, getCurrentDomain = { getCurrentDomain() }) }
  private val threadInfo by lazy { ThreadInfo(dvachDataSource = dvachDataSource, getCurrentDomain = { getCurrentDomain() }) }
  private val postImageInfo by lazy { PostImageInfo(getCurrentDomain = { getCurrentDomain() }) }

  @Volatile
  private var currentDomain = defaultDomain

  init {
    appScope.launch(Dispatchers.Main.immediate) {
      currentDomain = dvachSiteSettings.currentDomain.read()
      dvachSiteSettings.currentDomain.listen().collect { domain -> currentDomain = domain }
    }
  }

  private fun getCurrentDomain(): String {
    return "https://${currentDomain}".toHttpUrlOrNull()?.domain()?.removeSuffix("/") ?: defaultDomain
  }

  override fun catalogInfo(): Site.CatalogInfo = catalogInfo
  override fun threadInfo(): Site.ThreadInfo = threadInfo
  override fun boardsInfo(): Site.BoardsInfo = dvachBoardsInfo
  override fun postImageInfo(): Site.PostImageInfo = postImageInfo

  override fun replyInfo(): Site.ReplyInfo? {
    // TODO: Dvach support
    return null
  }

  override fun bookmarkInfo(): Site.BookmarkInfo? {
    // TODO: Dvach support
    return null
  }

  override fun catalogPagesInfo(): Site.CatalogPagesInfo? {
    // TODO: Dvach support
    return null
  }

  override fun globalSearchInfo(): Site.GlobalSearchInfo? {
    // TODO: Dvach support
    return null
  }

  override fun passcodeInfo(): Site.PasscodeInfo? {
    // TODO: Dvach support
    return null
  }

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
    // TODO: Dvach support
    return null
  }

  override fun requestModifier(): RequestModifier<Site> = chan4RequestModifier as RequestModifier<Site>

  override fun desktopUrl(threadDescriptor: ThreadDescriptor, postNo: Long?, postSubNo: Long?): String? {
    // TODO: Dvach support
    return null
  }

  override fun iconUrl(iconId: String, params: Map<String, String>): String? {
    // TODO: Dvach support
    return null
  }

  override fun commentFormattingButtons(catalogDescriptor: CatalogDescriptor): List<FormattingButton> {
    // TODO: Dvach support
    return emptyList()
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

  private class DvachRequestModifier(
    site: Dvach,
    appSettings: AppSettings
  ) : RequestModifier<Dvach>(site, appSettings) {

  }

  companion object {
    val SITE_KEY = SiteKey("Dvach")

    private val siteDomains = arrayOf(
      "2ch.hk",
      "2ch.life",
    )

    private val defaultDomain: String
      get() = siteDomains.first()
  }

}