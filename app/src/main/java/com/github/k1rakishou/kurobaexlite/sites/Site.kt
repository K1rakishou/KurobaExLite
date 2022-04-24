package com.github.k1rakishou.kurobaexlite.sites

import com.github.k1rakishou.kurobaexlite.model.data.local.BoardsData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.ReplyData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.IBoardDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IThreadDataSource
import com.github.k1rakishou.kurobaexlite.sites.settings.SiteSettings
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl

interface Site {
  val siteKey: SiteKey
  val readableName: String
  val siteCaptcha: SiteCaptcha
  val siteSettings: SiteSettings

  fun catalogInfo(): CatalogInfo?
  fun threadInfo(): ThreadInfo?
  fun boardsInfo(): BoardsInfo?
  fun postImageInfo(): PostImageInfo?
  fun replyInfo(): ReplyInfo?
  fun icon(): HttpUrl?

  fun resolveDescriptorFromUrl(url: HttpUrl): ResolvedDescriptor?
  fun requestModifier(): RequestModifier<Site>
  fun desktopUrl(threadDescriptor: ThreadDescriptor, postNo: Long?, postSubNo: Long?): String?

  interface CatalogInfo  {
    fun catalogUrl(boardCode: String): String
    fun catalogDataSource(): ICatalogDataSource<CatalogDescriptor, CatalogData>
  }

  interface ThreadInfo  {
    fun threadUrl(boardCode: String, threadNo: Long): String
    fun threadDataSource(): IThreadDataSource<ThreadDescriptor, ThreadData>
  }

  interface PostImageInfo {
    fun thumbnailUrl(boardCode: String, tim: Long, extension: String): String
    fun fullUrl(boardCode: String, tim: Long, extension: String): String
  }

  interface BoardsInfo {
    fun boardsUrl(): String
    fun siteBoardsDataSource(): IBoardDataSource<SiteKey, BoardsData>
  }

  interface ReplyInfo {
    fun replyUrl(chanDescriptor: ChanDescriptor): String
    fun sendReply(replyData: ReplyData): Flow<ReplyEvent>
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

  data class Banned(val banMessage: String) : ReplyResponse()

  data class RateLimited(val timeToWaitMs: Long) : ReplyResponse()

  data class AuthenticationRequired(
    val forgotCaptcha: Boolean?,
    val mistypedCaptcha: Boolean?
  ) : ReplyResponse()

  data class Success(
    val postDescriptor: PostDescriptor
  ) : ReplyResponse()
}