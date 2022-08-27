package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.notifications.ReplyNotificationsHelper
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.processDataCollectionConcurrently
import com.github.k1rakishou.kurobaexlite.interactors.thread_view.LoadChanThreadView
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.local.StickyThread
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadBookmarkData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadBookmarkInfoPostObject
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmarkReply
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlinx.coroutines.Dispatchers
import logcat.LogPriority
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.joda.time.DateTime

class FetchThreadBookmarkInfo(
  private val siteManager: SiteManager,
  private val bookmarksManager: BookmarksManager,
  private val replyNotificationsHelper: ReplyNotificationsHelper,
  private val parsedPostDataCache: ParsedPostDataCache,
  private val loadChanThreadView: LoadChanThreadView,
  private val extractRepliesToMyPosts: ExtractRepliesToMyPosts,
  private val persistBookmarks: PersistBookmarks,
  private val appSettings: AppSettings,
  private val androidHelpers: AndroidHelpers
) {

  suspend fun await(
    bookmarkDescriptors: List<ThreadDescriptor>,
    updateCurrentlyOpenedThread: Boolean,
  ): Result<Unit> {
    logcat(TAG) { "bookmarkDescriptorsCount=${bookmarkDescriptors.size}" }

    return Result
      .Try {
        val fetchResults = fetchThreadBookmarkInfoBatched(bookmarkDescriptors)
        processResults(
          fetchResults = fetchResults,
          updateCurrentlyOpenedThread = updateCurrentlyOpenedThread
        )
      }
  }

  private suspend fun fetchThreadBookmarkInfoBatched(
    watchingBookmarkDescriptors: List<ThreadDescriptor>
  ): List<ThreadBookmarkFetchResult> {
    val batchSize = (appSettings.processorsCount * BATCH_PER_CORE)
      .coerceAtLeast(MIN_BATCHES_COUNT)

    return processDataCollectionConcurrently(
      dataList = watchingBookmarkDescriptors,
      batchCount = batchSize,
      dispatcher = Dispatchers.IO
    ) { threadDescriptor ->
      val site = siteManager.bySiteKey(threadDescriptor.siteKey)
      if (site == null) {
        logcatError(TAG) { "Site with key ${threadDescriptor.siteKeyActual} not found in siteRepository!" }
        return@processDataCollectionConcurrently null
      }

      val bookmarkInfo = site.bookmarkInfo()
      if (bookmarkInfo == null) {
        logcatError(TAG) { "Site with key ${threadDescriptor.siteKeyActual} does not support bookmarks!" }
        return@processDataCollectionConcurrently null
      }

      if (!bookmarksManager.contains(threadDescriptor)) {
        return@processDataCollectionConcurrently ThreadBookmarkFetchResult.AlreadyDeleted(threadDescriptor)
      }

      return@processDataCollectionConcurrently bookmarkInfo.bookmarkDataSource()
        .loadBookmarkData(threadDescriptor)
        .map { threadBookmarkData -> ThreadBookmarkFetchResult.Success(threadBookmarkData, threadDescriptor)  }
        .getOrElse { error ->
          if (error is BadStatusResponseException) {
            if (error.isNotFoundError()) {
              return@getOrElse ThreadBookmarkFetchResult.NotFoundOnServer(threadDescriptor)
            } else {
              return@getOrElse ThreadBookmarkFetchResult.BadStatusCode(error.status, threadDescriptor)
            }
          }

          return@getOrElse ThreadBookmarkFetchResult.Error(error, threadDescriptor)
        }
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun processResults(
    fetchResults: List<ThreadBookmarkFetchResult>,
    updateCurrentlyOpenedThread: Boolean
  ) {
    printDebugLogs(fetchResults)

    if (fetchResults.isEmpty()) {
      logcat(TAG) { "fetchThreadBookmarkInfoUseCase.execute() returned no fetch results" }
      replyNotificationsHelper.showOrUpdateNotifications()
      return
    }

    val duration = measureTime { processResultsInternal(fetchResults) }
    val activeBookmarksCount = bookmarksManager.activeBookmarksCount()
    logcat(TAG) { "processResultsInternal() success, activeBookmarksCount: $activeBookmarksCount, took: ${duration}" }

    // Do not show notifications for the thread we are currently watching
    if (updateCurrentlyOpenedThread) {
      return
    }

    replyNotificationsHelper.showOrUpdateNotifications()
  }

  private suspend fun processResultsInternal(fetchResults: List<ThreadBookmarkFetchResult>) {
    val threadBookmarkDescriptorsToPersist = mutableListWithCap<ThreadDescriptor>(fetchResults.size / 2)

    val successFetchResults = fetchResults.filterIsInstance<ThreadBookmarkFetchResult.Success>()
    if (successFetchResults.isNotEmpty()) {
      val postsQuotingMe = extractRepliesToMyPosts.await(successFetchResults)
        .onFailure { error ->
          logcatError(TAG) { "extractRepliesToMyPosts() error: ${error.asLogIfImportantOrErrorMessage()}" }
        }
        .getOrNull()

      if (postsQuotingMe != null && postsQuotingMe.map.isNotEmpty()) {
        val postsQuotingMeMap = postsQuotingMe.map

        val fetchResultPairsList = successFetchResults
          .map { fetchResult -> fetchResult.threadDescriptor to fetchResult.threadBookmarkData }
          .toList()

        val updatedBookmarkDescriptors = fetchResultPairsList
          .mapNotNull { (threadDescriptor, threadBookmarkDataDto) ->
            val quotesToMeMap = postsQuotingMeMap[threadDescriptor] ?: emptyMap()

            val originalPost = threadBookmarkDataDto.postObjects.firstOrNull { postObject ->
              postObject is ThreadBookmarkInfoPostObject.OriginalPost
            } as? ThreadBookmarkInfoPostObject.OriginalPost

            checkNotNull(originalPost) { "threadBookmarkInfoObject has no OP!" }

            val threadBookmark = bookmarksManager.getBookmark(threadDescriptor)
            if (threadBookmark == null) {
              logcatError(TAG) { "threadBookmark of thread ${threadDescriptor} does not exist" }
              return@mapNotNull null
            }

            updateSingleBookmark(
              threadBookmark = threadBookmark,
              threadDescriptor = threadDescriptor,
              threadBookmarkData = threadBookmarkDataDto,
              originalPost = originalPost,
              quotesToMeMap = quotesToMeMap
            )

            logcat(TAG, LogPriority.VERBOSE) { "updateSingleBookmark() threadBookmark=${threadBookmark}" }
            bookmarksManager.putBookmark(threadBookmark)

            return@mapNotNull threadDescriptor
          }

        threadBookmarkDescriptorsToPersist.addAll(updatedBookmarkDescriptors)
      }
    }

    val unsuccessFetchResults = fetchResults
      .filter { result -> result !is ThreadBookmarkFetchResult.Success }
    if (unsuccessFetchResults.isNotEmpty()) {
      val updatedBookmarkDescriptors = unsuccessFetchResults.mapNotNull { unsuccessFetchResult ->
        val threadDescriptor = unsuccessFetchResult.threadDescriptor

        val threadBookmark = bookmarksManager.getBookmark(threadDescriptor)
        if (threadBookmark == null) {
          logcatError(TAG) { "threadBookmark of thread ${threadDescriptor} does not exist" }
          return@mapNotNull null
        }

        when (unsuccessFetchResult) {
          is ThreadBookmarkFetchResult.Error,
          is ThreadBookmarkFetchResult.BadStatusCode -> {
            threadBookmark.updateState(error = true, deleted = false)
          }
          is ThreadBookmarkFetchResult.NotFoundOnServer -> {
            threadBookmark.updateState(error = false, deleted = true)
          }
          is ThreadBookmarkFetchResult.AlreadyDeleted -> {
            // No-op. This just means that the user has deleted this bookmark while it was fetching
            // info so we have nothing to do.
          }
          is ThreadBookmarkFetchResult.Success -> {
            throw IllegalStateException("Shouldn't be handled here")
          }
        }

        // Clear first fetch flag even in case of an error
        threadBookmark.clearFirstFetchFlag()
        bookmarksManager.putBookmark(threadBookmark)

        return@mapNotNull threadDescriptor
      }

      threadBookmarkDescriptorsToPersist.addAll(updatedBookmarkDescriptors)
    }

    persistBookmarks.await(threadBookmarkDescriptorsToPersist)
  }

  private suspend fun updateSingleBookmark(
    threadBookmark: ThreadBookmark,
    threadDescriptor: ThreadDescriptor,
    threadBookmarkData: ThreadBookmarkData,
    originalPost: ThreadBookmarkInfoPostObject.OriginalPost,
    quotesToMeMap: Map<PostDescriptor, List<ExtractRepliesToMyPosts.ReplyToMyPost>>
  ) {
    // If we have just bookmarked this thread then use the last viewed post no to mark all posts
    // with postNo less than lastViewedPostNo as seen (as well as replies and notifications). We
    // need to do this to handle a case when you open a thread, then scroll to the bottom and
    // bookmark that thread. In such case, if we don't mark those posts then all posts will be
    // considered "unseen yet" so the user will see notifications and other stuff.
    val lastViewedPostDescriptor = if (threadBookmark.lastViewedPostPostDescriptor != null) {
      threadBookmark.lastViewedPostPostDescriptor
    } else {
      loadChanThreadView.execute(threadDescriptor)?.lastViewedPDForScroll
    }

    threadBookmark.updateThreadRepliesCount(threadBookmarkData.getPostsCountWithoutOP())

    // We need to handle rolling sticky (sticky threads with max posts cap) a little bit
    // differently so store this information for now (we don't need to persist it though)
    threadBookmark.stickyThread = originalPost.stickyThread

    // seenPostsCount must never be greater than totalPostsCount, but it may actually be greater
    // for couple of moments in case when we are at the very bottom of a bookmarked thread and
    // we fetch a new post. In such case we will first update seenPostsCount in BookmarksManager,
    // but we won't update totalPostsCount until we fetch bookmark info, so for that short amount
    // of time seenPostsCount will be greater than totalPostsCount so we need to correct that
    // info here. If we don't do that, then in the previous case there will be one unseen post
    // left and it will be impossible to get rid of it by scrolling to the bottom of the thread.
    if (threadBookmark.seenPostsCount > threadBookmark.totalPostsCount) {
      threadBookmark.seenPostsCount = threadBookmark.totalPostsCount
    }

    // When seenPostsCount is zero we can update it seen post information we get by calculating
    // the amount of posts which postNo is less or equals to lastViewedPostNo
    if (threadBookmark.seenPostsCount == 0) {
      threadBookmark.seenPostsCount = threadBookmarkData.countAmountOfSeenPosts(lastViewedPostDescriptor)
    }

    quotesToMeMap.forEach { (myPostPostDescriptor, replyToMyPostList) ->
      replyToMyPostList.forEach { replyToMyPost ->
        createOrUpdateReplyToMyPosts(
          threadBookmark = threadBookmark,
          replyToMyPost = replyToMyPost,
          threadDescriptor = threadDescriptor,
          myPostDescriptor = myPostPostDescriptor,
          lastViewedPostDescriptor = lastViewedPostDescriptor
        )
      }
    }

    val newPostsCount = threadBookmarkData.postObjects
      .count { threadBookmarkInfoPostObject ->
        if (lastViewedPostDescriptor == null) {
          return@count true
        }

        val postDescriptor = threadBookmarkInfoPostObject.postDescriptor()
        return@count postDescriptor > lastViewedPostDescriptor
      }

    threadBookmark.updateSeenPostCountAfterFetch(newPostsCount)
    threadBookmark.setBumpLimit(originalPost.isBumpLimit)
    threadBookmark.setImageLimit(originalPost.isImageLimit)
    threadBookmark.updateLastThreadPostDescriptor(threadBookmarkData.lastThreadPostDescriptor())

    threadBookmark.updateState(
      archived = originalPost.archived,
      closed = originalPost.closed,
      error = false,
      deleted = false,
      stickyNoCap = originalPost.stickyThread is StickyThread.StickyUnlimited
    )

    // Update bookmark title if it's not set
    if (threadBookmark.title == null) {
      val subject = threadBookmarkData.subject()
      val originalPostComment = threadBookmarkData.originalPostComment()

      val updatedTitle = parsedPostDataCache.formatBookmarkTitle(subject, originalPostComment)
      if (updatedTitle != null) {
        threadBookmark.title = updatedTitle
      }
    }

    // Update bookmark thumbnail if it's not set
    if (threadBookmark.thumbnailUrl == null) {
      val tim = threadBookmarkData.originalPostTim()
      if (tim != null) {
        val site = siteManager.bySiteKey(threadDescriptor.siteKey)
        if (site != null) {
          val updatedThumbnailUrl = site.postImageInfo()
            ?.thumbnailUrl(threadDescriptor.boardCode, tim, "jpg")
            ?.toHttpUrlOrNull()

          if (updatedThumbnailUrl != null) {
            threadBookmark.thumbnailUrl = updatedThumbnailUrl
          }
        }
      }
    }

    threadBookmark.clearFirstFetchFlag()
  }

  private fun createOrUpdateReplyToMyPosts(
    threadBookmark: ThreadBookmark,
    replyToMyPost: ExtractRepliesToMyPosts.ReplyToMyPost,
    threadDescriptor: ThreadDescriptor,
    myPostDescriptor: PostDescriptor,
    lastViewedPostDescriptor: PostDescriptor?
  ) {
    val postReplyDescriptor = replyToMyPost.postDescriptor
    val alreadyRead = lastViewedPostDescriptor != null && lastViewedPostDescriptor > postReplyDescriptor

    if (!threadBookmark.threadBookmarkReplies.containsKey(postReplyDescriptor)) {
      threadBookmark.threadBookmarkReplies[postReplyDescriptor] = ThreadBookmarkReply(
        postDescriptor = postReplyDescriptor,
        repliesTo = PostDescriptor.create(threadDescriptor, myPostDescriptor.postNo, myPostDescriptor.postSubNo),
        // If lastViewPostNo is greater or equal to reply's postNo then we have already seen/read
        // that reply and we don't need to notify the user about it. This happens when the user
        // replies to a thread then someone else replies to him and before we update the bookmarks
        // the user scroll below the reply position. In such case we don't want to show any kind
        // of notifications because the user has already seen/read the reply.
        alreadySeen = alreadyRead,
        alreadyNotified = alreadyRead,
        alreadyRead = alreadyRead,
        time = DateTime.now(),
        commentRaw = replyToMyPost.commentRaw
      )
    } else {
      val existingReply = checkNotNull(threadBookmark.threadBookmarkReplies[postReplyDescriptor])

      // Mark replies as seen and notified if necessary
      if (!existingReply.alreadySeen) {
        existingReply.alreadySeen = alreadyRead
      }

      if (!existingReply.alreadyNotified) {
        existingReply.alreadyNotified = alreadyRead
      }

      if (!existingReply.alreadyRead) {
        existingReply.alreadyRead = alreadyRead
      }
    }
  }

  private fun printDebugLogs(threadBookmarkFetchResults: List<ThreadBookmarkFetchResult>) {
    if (threadBookmarkFetchResults.isEmpty()) {
      logcat(TAG) { "printDebugLogs() no fetch results" }
      return
    }

    var errorsCount = 0
    var alreadyDeletedCount = 0
    var notFoundOnServerCount = 0
    var badStatusCount = 0
    var successCount = 0

    threadBookmarkFetchResults.forEach { fetchResult ->
      when (fetchResult) {
        is ThreadBookmarkFetchResult.Error -> {
          logcatError(TAG) {
            "FetchResult.Error: descriptor=${fetchResult.threadDescriptor}, " +
              "error: ${fetchResult.error.errorMessageOrClassName()}"
          }

          ++errorsCount
        }
        is ThreadBookmarkFetchResult.AlreadyDeleted -> {
          if (androidHelpers.isDevFlavor()) {
            logcat(TAG) { "FetchResult.AlreadyDeleted: descriptor=${fetchResult.threadDescriptor}" }
          }

          ++alreadyDeletedCount
        }
        is ThreadBookmarkFetchResult.NotFoundOnServer -> {
          if (androidHelpers.isDevFlavor()) {
            logcat(TAG) { "FetchResult.NotFoundOnServer: descriptor=${fetchResult.threadDescriptor}" }
          }

          ++notFoundOnServerCount
        }
        is ThreadBookmarkFetchResult.BadStatusCode -> {
          if (androidHelpers.isDevFlavor()) {
            logcat(TAG) {
              "FetchResult.BadStatusCode: descriptor=${fetchResult.threadDescriptor}, " +
                "status=${fetchResult.statusCode}"
            }
          }

          ++badStatusCount
        }
        is ThreadBookmarkFetchResult.Success -> {
          if (androidHelpers.isDevFlavor()) {
            val originalPost = fetchResult.threadBookmarkData.postObjects.firstOrNull { post ->
              post is ThreadBookmarkInfoPostObject.OriginalPost
            } as? ThreadBookmarkInfoPostObject.OriginalPost

            requireNotNull(originalPost) { "No OP!" }
          }

          ++successCount
        }
      }
    }

    logcat(TAG) {
      "fetchThreadBookmarkInfo stats: total results=${threadBookmarkFetchResults.size}, " +
        "errorsCount=$errorsCount, alreadyDeletedCount=$alreadyDeletedCount, " +
        "notFoundOnServerCount=$notFoundOnServerCount, badStatusCount=$badStatusCount, " +
        "successCount=$successCount"
    }
  }

  sealed class ThreadBookmarkFetchResult(val threadDescriptor: ThreadDescriptor) {
    class Error(
      val error: Throwable,
      threadDescriptor: ThreadDescriptor
    ) : ThreadBookmarkFetchResult(threadDescriptor)

    class AlreadyDeleted(
      threadDescriptor: ThreadDescriptor
    ) : ThreadBookmarkFetchResult(threadDescriptor)

    class NotFoundOnServer(
      threadDescriptor: ThreadDescriptor
    ) : ThreadBookmarkFetchResult(threadDescriptor)

    class BadStatusCode(
      val statusCode: Int,
      threadDescriptor: ThreadDescriptor
    ) : ThreadBookmarkFetchResult(threadDescriptor)

    class Success(
      val threadBookmarkData: ThreadBookmarkData,
      threadDescriptor: ThreadDescriptor
    ) : ThreadBookmarkFetchResult(threadDescriptor)
  }

  companion object {
    private const val TAG = "FetchThreadBookmarkInfo"
    private const val BATCH_PER_CORE = 4
    private const val MIN_BATCHES_COUNT = 8
  }
}