package com.github.k1rakishou.kurobaexlite.sites

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.parser.AbstractSitePostParser
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogPagesData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogsData
import com.github.k1rakishou.kurobaexlite.model.data.local.Chan4LoginDetails
import com.github.k1rakishou.kurobaexlite.model.data.local.Chan4LoginResult
import com.github.k1rakishou.kurobaexlite.model.data.local.ReplyData
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchParams
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchResult
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
import com.github.k1rakishou.kurobaexlite.model.source.IGlobalSearchDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ILoginDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ILogoutDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IThreadDataSource
import com.github.k1rakishou.kurobaexlite.sites.settings.SiteSettings
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl

interface Site {
  val siteKey: SiteKey
  val readableName: String
  val firewallChallengeEndpoint: HttpUrl?

  val siteCaptcha: SiteCaptcha
  val siteSettings: SiteSettings

  fun catalogInfo(): CatalogInfo?
  fun threadInfo(): ThreadInfo?
  fun boardsInfo(): BoardsInfo?
  fun postImageInfo(): PostImageInfo?
  fun replyInfo(): ReplyInfo?
  fun bookmarkInfo(): BookmarkInfo?
  fun catalogPagesInfo(): CatalogPagesInfo?
  fun globalSearchInfo(): GlobalSearchInfo?
  fun passcodeInfo(): PasscodeInfo?

  fun matchesUrl(httpUrl: HttpUrl): Boolean

  fun parser(): AbstractSitePostParser
  fun icon(): HttpUrl?
  fun resolveDescriptorFromUrl(url: HttpUrl): ResolvedDescriptor?
  fun requestModifier(): RequestModifier<Site>
  fun desktopUrl(threadDescriptor: ThreadDescriptor, postNo: Long?, postSubNo: Long?): String?
  fun iconUrl(iconId: String, params: Map<String, String>): String?

  fun commentFormattingButtons(catalogDescriptor: CatalogDescriptor): List<FormattingButton>

  interface CatalogInfo  {
    fun catalogUrl(boardCode: String): String
    fun catalogDataSource(): ICatalogDataSource<CatalogDescriptor, CatalogData>
  }

  interface ThreadInfo  {
    // Some image boards support incremental thread updates, like you give the endpoint the last postNo that you have and
    // it returns 0 or more posts after that post.
    fun fullThreadUrl(boardCode: String, threadNo: Long): String

    // Full thread update endpoint
    fun partialThreadUrl(boardCode: String, threadNo: Long, afterPost: PostDescriptor): String
    fun threadDataSource(): IThreadDataSource<ThreadDescriptor, ThreadData>
  }

  interface PostImageInfo {
    fun thumbnailUrl(params: Map<String, String>): HttpUrl?
    fun fullUrl(params: Map<String, String>): HttpUrl?
  }

  interface BoardsInfo {
    fun boardsUrl(): String
    fun siteBoardsDataSource(): IBoardDataSource<SiteKey, CatalogsData>
  }

  interface ReplyInfo {
    suspend fun replyUrl(chanDescriptor: ChanDescriptor): String
    fun sendReply(replyData: ReplyData): Flow<ReplyEvent>
  }

  interface BookmarkInfo {
    fun bookmarkUrl(boardCode: String, threadNo: Long): String
    fun bookmarkDataSource(): IBookmarkDataSource<ThreadDescriptor, ThreadBookmarkData>
  }

  interface CatalogPagesInfo {
    fun catalogPagesUrl(boardCode: String): String
    fun catalogPagesDataSource(): ICatalogPagesDataSource<CatalogDescriptor, CatalogPagesData?>
  }

  interface GlobalSearchInfo {
    val resultsPerPage: Int
    val supportsSiteWideSearch: Boolean
    val supportsCatalogSpecificSearch: Boolean

    fun globalSearchUrl(boardCode: String, query: String, page: Int): String
    fun globalSearchUrl(query: String, page: Int): String

    fun globalSearchDataSource(): IGlobalSearchDataSource<SearchParams, SearchResult>
  }

  interface PasscodeInfo {
    fun loginUrl(): String

    fun loginDataSource(): ILoginDataSource<Chan4LoginDetails, Chan4LoginResult>
    fun logoutDataSource(): ILogoutDataSource<Unit, Unit>
  }

}

sealed class ReplyEvent {
  object Start : ReplyEvent()
  class Progress(val progress: Float) : ReplyEvent()
  data class Success(val replyResponse: ReplyResponse) : ReplyEvent()
  class Error(val error: Throwable) : ReplyEvent()
}

sealed class ReplyResponse {
  data class Error(
    val errorMessage: String
  ) : ReplyResponse()

  data class NotAllowedToPost(val errorMessage: String) : ReplyResponse()

  data class RateLimited(val timeToWaitMs: Long) : ReplyResponse()

  data class AuthenticationRequired(
    val forgotCaptcha: Boolean,
    val mistypedCaptcha: Boolean
  ) : ReplyResponse()

  data class Success(
    val postDescriptor: PostDescriptor
  ) : ReplyResponse()
}

data class FormattingButton(
  val title: AnnotatedString,
  val openTag: String,
  val closeTag: String
)