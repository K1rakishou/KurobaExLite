package com.github.k1rakishou.kurobaexlite.helpers.kpnc

import com.github.k1rakishou.kpnc.domain.ClientAppNotifier
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.ignore
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatDebug
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.FetchThreadBookmarkInfo
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.LoadBookmarks
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ClientAppNotifierImpl(
  private val siteManager: SiteManager,
  private val bookmarksManager: BookmarksManager,
  private val loadBookmarks: LoadBookmarks,
  private val fetchThreadBookmarkInfo: FetchThreadBookmarkInfo,
) : ClientAppNotifier {

  override fun onRepliesReceived(postUrls: List<String>): Result<Unit> {
    return Result.Try {
      logcatDebug(TAG) { "onRepliesReceived() postUrls: ${postUrls.size}" }

      if (postUrls.isEmpty()) {
        return@Try
      }

      postUrls.forEach { postUrl ->
        logcatDebug(TAG) { "onRepliesReceived() postUrl: ${postUrl}" }
      }

      logcatDebug(TAG) { "onRepliesReceivedInternal() start" }
      runBlocking(Dispatchers.IO) { onRepliesReceivedInternal(postUrls) }
      logcatDebug(TAG) { "onRepliesReceivedInternal() end" }
    }
  }

  private suspend fun onRepliesReceivedInternal(postUrls: List<String>) {
    postUrls.forEach { postUrl -> logcatDebug(TAG) { "postUrl: ${postUrl}" } }
    logcatDebug(TAG) { "fetchThreadBookmarkInfo.await()..." }

    val loadBookmarksResult = loadBookmarks.executeSuspend()
      .onFailure { error ->
        logcatError(TAG) { "loadBookmarks.executeSuspend() -> error: ${error.asLogIfImportantOrErrorMessage()}" }
      }

    if (loadBookmarksResult.isSuccess) {
      val bookmarkDescriptorsToCheck = getBookmarkDescriptorsToCheck(postUrls)
      if (bookmarkDescriptorsToCheck.isNotEmpty()) {
        fetchThreadBookmarkInfo.await(
          bookmarkDescriptorsToCheck = bookmarkDescriptorsToCheck,
          updateCurrentlyOpenedThread = false
        )
          .onFailure { error ->
            logcatError(TAG) { "fetchThreadBookmarkInfo.await() error: ${error.asLogIfImportantOrErrorMessage()}" }
          }
          .ignore()
      } else {
        logcatDebug(TAG) { "bookmarkDescriptorsToCheck is empty" }
      }
    }

    logcatDebug(TAG) { "fetchThreadBookmarkInfo.await()... done" }
  }

  private suspend fun getBookmarkDescriptorsToCheck(postUrlsToCheck: List<String>): List<ThreadDescriptor> {
    if (postUrlsToCheck.isEmpty()) {
      return emptyList()
    }

    return postUrlsToCheck
      .asSequence()
      .mapNotNull { postUrlToCheck ->
        val resolvedDescriptor = siteManager.resolveDescriptorFromRawIdentifier(postUrlToCheck)
          ?: return@mapNotNull null

        return@mapNotNull when (resolvedDescriptor) {
          is ResolvedDescriptor.CatalogOrThread -> null
          is ResolvedDescriptor.Post -> resolvedDescriptor.postDescriptor.threadDescriptor
        }
      }
      .toHashSet()
      .filter { threadDescriptor -> bookmarksManager.contains(threadDescriptor) }
      .toList()
  }

  companion object {
    private const val TAG = "ClientAppNotifierImpl"
  }
}