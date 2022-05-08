package com.github.k1rakishou.kurobaexlite.model.data.local.bookmark

import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadBookmarkEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadBookmarkEntityWithReplies
import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadKey
import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadLocalPostKey
import com.github.k1rakishou.kurobaexlite.model.data.local.dto.StickyThread
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import java.util.BitSet
import kotlin.math.max
import okhttp3.HttpUrl
import org.joda.time.DateTime

class ThreadBookmark private constructor(
  val threadDescriptor: ThreadDescriptor,
  var seenPostsCount: Int = 0,
  var totalPostsCount: Int = 0,
  var lastViewedPostPostDescriptor: PostDescriptor? = null,
  var lastPostDescriptorOfThread: PostDescriptor? = null,
  val threadBookmarkReplies: MutableMap<PostDescriptor, ThreadBookmarkReply> = mutableMapOf(),
  var title: String? = null,
  var thumbnailUrl: HttpUrl? = null,
  var state: BitSet,
  var stickyThread: StickyThread = StickyThread.NotSticky,
  var createdOn: DateTime
) {

  fun deepCopy(): ThreadBookmark {
    val threadBookmarkRepliesCopy = if (threadBookmarkReplies.isNotEmpty()) {
      HashMap<PostDescriptor, ThreadBookmarkReply>(threadBookmarkReplies.size)
    } else {
      HashMap<PostDescriptor, ThreadBookmarkReply>()
    }

    threadBookmarkReplies.forEach { (postDescriptor, threadBookmarkReply) ->
      threadBookmarkRepliesCopy[postDescriptor] = threadBookmarkReply.copy()
    }

    return ThreadBookmark(
      threadDescriptor = threadDescriptor,
      seenPostsCount = seenPostsCount,
      totalPostsCount = totalPostsCount,
      lastViewedPostPostDescriptor = lastViewedPostPostDescriptor,
      lastPostDescriptorOfThread = lastPostDescriptorOfThread,
      threadBookmarkReplies = threadBookmarkRepliesCopy,
      title = title,
      thumbnailUrl = thumbnailUrl,
      stickyThread = stickyThread,
      state = BitSet.valueOf(state.toLongArray()),
      createdOn = createdOn
    )
  }

  fun isActive(): Boolean = state.get(BOOKMARK_STATE_WATCHING)
  fun isStickyClosed(): Boolean = state.get(BOOKMARK_STATE_STICKY_NO_CAP) && state.get(BOOKMARK_STATE_THREAD_CLOSED)
  fun isThreadDeleted(): Boolean = state.get(BOOKMARK_STATE_THREAD_DELETED)
  fun isThreadArchived(): Boolean = state.get(BOOKMARK_STATE_THREAD_ARCHIVED)
  fun isBumpLimit(): Boolean = state.get(BOOKMARK_STATE_THREAD_BUMP_LIMIT)
  fun isImageLimit(): Boolean = state.get(BOOKMARK_STATE_THREAD_IMAGE_LIMIT)
  fun isRollingSticky(): Boolean = stickyThread is StickyThread.StickyWithCap
  fun isFirstFetch(): Boolean = state.get(BOOKMARK_STATE_FIRST_FETCH)
  fun isError(): Boolean = state.get(BOOKMARK_STATE_ERROR)
  fun isFilterWatchBookmark(): Boolean = state.get(BOOKMARK_FILTER_WATCH)

  fun clearFirstFetchFlag() {
    if (state.get(BOOKMARK_STATE_FIRST_FETCH)) {
      state.clear(BOOKMARK_STATE_FIRST_FETCH)
    }
  }

  fun hasUnreadReplies(): Boolean {
    return threadBookmarkReplies.values.any { threadBookmarkReply -> !threadBookmarkReply.alreadyRead }
  }

  fun unseenPostsCount(): Int {
    return (totalPostsCount - seenPostsCount).coerceAtLeast(0)
  }

  fun newPostsCount(): Int = max(0, totalPostsCount - seenPostsCount)
  fun newQuotesCount(): Int = threadBookmarkReplies.values.count { reply -> !reply.alreadyRead }

  fun updateLastViewedPostDescriptor(newLastViewedPostPostDescriptor: PostDescriptor?) {
    lastViewedPostPostDescriptor = PostDescriptor.maxOfPostDescriptors(
      one = lastViewedPostPostDescriptor,
      other = newLastViewedPostPostDescriptor
    )
  }

  fun updateLastThreadPostDescriptor(newLastThreadPostPostDescriptor: PostDescriptor?) {
    lastPostDescriptorOfThread = PostDescriptor.maxOfPostDescriptors(
      one = lastPostDescriptorOfThread,
      other = newLastThreadPostPostDescriptor
    )
  }

  fun updateSeenPostsCount(unseenPostsCount: Int) {
    seenPostsCount = maxOf(seenPostsCount, (totalPostsCount - unseenPostsCount).coerceAtLeast(0))
  }

  fun updateSeenPostCountAfterFetch(newPostsCount: Int) {
    seenPostsCount = maxOf(0, totalPostsCount - newPostsCount)
  }

  fun updateThreadRepliesCount(newPostsCount: Int) {
    totalPostsCount = newPostsCount
  }

  fun setBumpLimit(bumpLimit: Boolean) {
    if (bumpLimit) {
      state.set(BOOKMARK_STATE_THREAD_BUMP_LIMIT)
    } else {
      state.clear(BOOKMARK_STATE_THREAD_BUMP_LIMIT)
    }
  }

  fun setImageLimit(imageLimit: Boolean) {
    if (imageLimit) {
      state.set(BOOKMARK_STATE_THREAD_IMAGE_LIMIT)
    } else {
      state.clear(BOOKMARK_STATE_THREAD_IMAGE_LIMIT)
    }
  }

  fun toggleWatching() {
    if (state.get(BOOKMARK_STATE_WATCHING)) {
      state.clear(BOOKMARK_STATE_WATCHING)
      return
    }

    if (state.get(BOOKMARK_STATE_THREAD_DELETED)
      || state.get(BOOKMARK_STATE_THREAD_ARCHIVED)
      || isStickyClosed()) {
      return
    }

    state.set(BOOKMARK_STATE_WATCHING)
  }

  fun setFilterWatchFlag() {
    if (state.get(BOOKMARK_FILTER_WATCH)) {
      return
    }

    state.set(BOOKMARK_FILTER_WATCH)
  }

  fun removeFilterWatchFlag() {
    if (!state.get(BOOKMARK_FILTER_WATCH)) {
      return
    }

    state.clear(BOOKMARK_FILTER_WATCH)
  }

  fun readRepliesUpTo(lastSeenPostDescriptor: PostDescriptor) {
    // Mark all quotes to me as notified/seen/read which postNo is less or equals to lastSeenPostNo.
    threadBookmarkReplies.values
      .filter { threadBookmarkReply -> threadBookmarkReply.postDescriptor <= lastSeenPostDescriptor }
      .forEach { threadBookmarkReply ->
        threadBookmarkReply.alreadyNotified = true
        threadBookmarkReply.alreadySeen = true
        threadBookmarkReply.alreadyRead = true
      }
  }

  fun readAllPostsAndNotifications() {
    seenPostsCount = totalPostsCount
    lastViewedPostPostDescriptor = PostDescriptor.maxOfPostDescriptors(
      one = lastViewedPostPostDescriptor,
      other = lastPostDescriptorOfThread
    )

    threadBookmarkReplies.values.forEach { threadBookmarkReply ->
      threadBookmarkReply.alreadySeen = true
      threadBookmarkReply.alreadyNotified = true
      threadBookmarkReply.alreadyRead = true
    }
  }

  fun markAsSeenAllReplies() {
    threadBookmarkReplies.values.forEach { threadBookmarkReply ->
      threadBookmarkReply.alreadySeen = true
      threadBookmarkReply.alreadyNotified = true
    }
  }

  fun updateState(
    error: Boolean? = null,
    deleted: Boolean? = null,
    archived: Boolean? = null,
    closed: Boolean? = null,
    stickyNoCap: Boolean? = null
  ) {
    val oldStateHasTerminalFlags = state.get(BOOKMARK_STATE_THREAD_DELETED) || isStickyClosed()
    if (oldStateHasTerminalFlags) {
      state.clear(BOOKMARK_STATE_WATCHING)
      return
    }

    // We don't want to infinitely fetch information for pinned closed threads. Such threads may be
    // active for years and they usually don't get any updates (or they do but very rarely). Pinning
    // such a thread should result in us stopping watching it right after the very first successful
    // fetch.
    val stickyClosedThread = stickyNoCap == true && closed == true

    val newStateHasTerminalFlags = deleted == true || archived == true || stickyClosedThread
    if (newStateHasTerminalFlags) {
      // If any of the above - we don't watch that thread anymore
      state.clear(BOOKMARK_STATE_WATCHING)
    }

    error?.let {
      if (it) {
        state.set(BOOKMARK_STATE_ERROR)
      } else {
        state.clear(BOOKMARK_STATE_ERROR)
      }
    }

    deleted?.let {
      if (it) {
        state.set(BOOKMARK_STATE_THREAD_DELETED)
      }
    }

    archived?.let {
      if (it) {
        state.set(BOOKMARK_STATE_THREAD_ARCHIVED)
      }
    }

    closed?.let {
      if (it) {
        state.set(BOOKMARK_STATE_THREAD_CLOSED)
      } else {
        state.clear(BOOKMARK_STATE_THREAD_CLOSED)
      }
    }

    stickyNoCap?.let {
      if (it) {
        state.set(BOOKMARK_STATE_STICKY_NO_CAP)
      } else {
        state.clear(BOOKMARK_STATE_STICKY_NO_CAP)
      }
    }
  }

  fun toThreadBookmarkEntity(): ThreadBookmarkEntity {
    return ThreadBookmarkEntity(
      bookmarkKey = ThreadKey.fromThreadDescriptor(threadDescriptor),
      seenPostsCount = seenPostsCount,
      totalPostsCount = totalPostsCount,
      lastViewedPostKey = lastViewedPostPostDescriptor?.let { ThreadLocalPostKey.fromPostDescriptor(it) },
      threadLastPostKey = lastPostDescriptorOfThread?.let { ThreadLocalPostKey.fromPostDescriptor(it) },
      title = title,
      thumbnailUrl = thumbnailUrl,
      state = state,
      createdOn = createdOn
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ThreadBookmark

    if (threadDescriptor != other.threadDescriptor) return false
    if (seenPostsCount != other.seenPostsCount) return false
    if (totalPostsCount != other.totalPostsCount) return false
    if (lastViewedPostPostDescriptor != other.lastViewedPostPostDescriptor) return false
    if (lastPostDescriptorOfThread != other.lastPostDescriptorOfThread) return false
    if (title != other.title) return false
    if (thumbnailUrl != other.thumbnailUrl) return false
    if (state != other.state) return false
    if (stickyThread != other.stickyThread) return false
    if (createdOn != other.createdOn) return false

    if (!compareThreadBookmarkReplies(threadBookmarkReplies, other.threadBookmarkReplies)) {
      return false
    }

    return true
  }

  override fun hashCode(): Int {
    var result = threadDescriptor.hashCode()
    result = 31 * result + seenPostsCount
    result = 31 * result + totalPostsCount
    result = 31 * result + lastViewedPostPostDescriptor.hashCode()
    result = 31 * result + lastPostDescriptorOfThread.hashCode()
    result = 31 * result + threadBookmarkReplies.hashCode()
    result = 31 * result + (title?.hashCode() ?: 0)
    result = 31 * result + (thumbnailUrl?.hashCode() ?: 0)
    result = 31 * result + state.hashCode()
    result = 31 * result + stickyThread.hashCode()
    result = 31 * result + createdOn.hashCode()
    return result
  }

  override fun toString(): String {
    return "ThreadBookmark(threadDescriptor=$threadDescriptor, seenPostsCount=$seenPostsCount, " +
      "threadRepliesCount=$totalPostsCount, threadLastViewedPostPostDescriptor=$lastViewedPostPostDescriptor, " +
      "threadLastPostPostDescriptor=$lastPostDescriptorOfThread, threadBookmarkReplies=$threadBookmarkReplies, " +
      "title=${title?.take(20)}, thumbnailUrl=$thumbnailUrl, stickyThread=$stickyThread, " +
      "state=${stateToString()}, createdOn=${createdOn})"
  }


  private fun compareThreadBookmarkReplies(
    tbr1: MutableMap<PostDescriptor, ThreadBookmarkReply>,
    tbr2: MutableMap<PostDescriptor, ThreadBookmarkReply>
  ): Boolean {
    if (tbr1.size != tbr2.size) {
      return false
    }

    for ((postDescriptor, threadBookmarkReply1) in tbr1.entries) {
      val threadBookmarkReply2 = tbr2[postDescriptor]
        ?: return false

      if (threadBookmarkReply1 != threadBookmarkReply2) {
        return false
      }
    }

    return true
  }

  fun stateToString(): String {
    return buildString {
      append("[")

      val states = mutableListOf<String>()

      if (state.get(BOOKMARK_STATE_WATCHING)) {
        states += "WATCHING"
      }

      if (state.get(BOOKMARK_STATE_THREAD_DELETED)) {
        states += "DELETED"
      }

      if (state.get(BOOKMARK_STATE_THREAD_ARCHIVED)) {
        states += "ARCHIVED"
      }

      if (state.get(BOOKMARK_STATE_THREAD_CLOSED)) {
        states += "CLOSED"
      }

      if (state.get(BOOKMARK_STATE_ERROR)) {
        states += "ERROR"
      }

      if (state.get(BOOKMARK_STATE_THREAD_BUMP_LIMIT)) {
        states += "BUMP_LIMIT"
      }

      if (state.get(BOOKMARK_STATE_THREAD_IMAGE_LIMIT)) {
        states += "IMAGE_LIMIT"
      }

      if (state.get(BOOKMARK_STATE_FIRST_FETCH)) {
        states += "FIRST_FETCH"
      }

      if (state.get(BOOKMARK_STATE_STICKY_NO_CAP)) {
        states += "STICKY_NO_CAP"
      }

      if (state.get(BOOKMARK_FILTER_WATCH)) {
        states += "BOOKMARK_FILTER_WATCH"
      }

      append(states.joinToString())
      append("]")
    }
  }

  companion object {
    // Do not delete, nor move around any of these flags! If any of the flags is not used anymore
    // then just deprecate it. Do not reuse bit indexes!

    /**
     * A flag for threads that are being watched (not paused). Default flag when bookmarking any
     * thread.
     * */
    const val BOOKMARK_STATE_WATCHING = 0

    /**
     * A flag for threads that are probably got deleted (404ed) from the server
     * */
    const val BOOKMARK_STATE_THREAD_DELETED = 1

    /**
     * A flag for threads that got archived by first-party archives (like the archive that 4chan has)
     * */
    const val BOOKMARK_STATE_THREAD_ARCHIVED = 2

    /**
     * A flag for closed threads
     * */
    const val BOOKMARK_STATE_THREAD_CLOSED = 3

    /**
     * A flag for threads that we failed to fetch bookmark info from for any reason (no internet,
     * server is down, etc.)
     * */
    const val BOOKMARK_STATE_ERROR = 4

    /**
     * Thread has reached bump limit
     * */
    const val BOOKMARK_STATE_THREAD_BUMP_LIMIT = 5

    /**
     * Thread has reached image limit
     * */
    const val BOOKMARK_STATE_THREAD_IMAGE_LIMIT = 6

    /**
     * Default bookmark state that is getting cleared once the very first fetch is completed with
     * any result (success/error). We need this flag to show the "Loading" label for bookmarks we
     * have no info yet (before their very first fetch).
     * */
    const val BOOKMARK_STATE_FIRST_FETCH = 7

    /**
     * The thread is sticky (and it is not "sticky rolling" thread). We need this flag to handle
     * cases when a thread is sticky and closed because such threads are "alive" threads but not
     * really so if the user bookmarks such thread it will be fetching thread info infinitely.
     * */
    const val BOOKMARK_STATE_STICKY_NO_CAP = 8

    /**
     * Indicates that this bookmark is used for filter watching and should be also visible on
     * Filter Watches page of BookmarksController.
     * */
    const val BOOKMARK_FILTER_WATCH = 9

    fun create(
      threadDescriptor: ThreadDescriptor,
      createdOn: DateTime,
      title: String? = null,
      thumbnailUrl: HttpUrl? = null,
      initialFlags: BitSet? = null,
    ): ThreadBookmark {
      val bookmarkInitialState = initialFlags ?: BitSet()
      bookmarkInitialState.set(BOOKMARK_STATE_WATCHING)
      bookmarkInitialState.set(BOOKMARK_STATE_FIRST_FETCH)

      return ThreadBookmark(
        threadDescriptor = threadDescriptor,
        state = bookmarkInitialState,
        createdOn = createdOn,
        title = title,
        thumbnailUrl = thumbnailUrl
      )
    }

    fun fromThreadBookmarkEntityWithReplies(
      threadBookmarkEntityWithReplies: ThreadBookmarkEntityWithReplies
    ): ThreadBookmark {
      val threadBookmarkEntity = threadBookmarkEntityWithReplies.threadBookmarkEntity
      val threadDescriptor = threadBookmarkEntity.bookmarkKey.threadDescriptor

      val threadBookmarkReplies = ThreadBookmarkReply.fromThreadBookmarkReplyEntity(
        threadDescriptor = threadDescriptor,
        threadBookmarkReplyEntities = threadBookmarkEntityWithReplies.threadBookmarkEntityReplies
      ).toMutableMap()

      return ThreadBookmark(
        threadDescriptor = threadBookmarkEntity.bookmarkKey.threadDescriptor,
        seenPostsCount = threadBookmarkEntity.seenPostsCount ?: 0,
        totalPostsCount = threadBookmarkEntity.totalPostsCount ?: 0,
        lastViewedPostPostDescriptor = threadBookmarkEntity.lastViewedPostKey?.postDescriptor(threadDescriptor),
        lastPostDescriptorOfThread = threadBookmarkEntity.threadLastPostKey?.postDescriptor(threadDescriptor),
        threadBookmarkReplies = threadBookmarkReplies,
        title = threadBookmarkEntity.title,
        thumbnailUrl = threadBookmarkEntity.thumbnailUrl,
        state = threadBookmarkEntity.state,
        createdOn = threadBookmarkEntity.createdOn
      )
    }

  }

}