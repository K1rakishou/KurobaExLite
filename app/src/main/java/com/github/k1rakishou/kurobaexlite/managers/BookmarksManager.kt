package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BookmarksManager {
  private val mutex = Mutex()
  private val threadBookmarks = mutableMapOf<ThreadDescriptor, ThreadBookmark>()

  private val _bookmarkEventsFlow = MutableSharedFlow<Event>(extraBufferCapacity = Channel.UNLIMITED)
  val bookmarkEventsFlow: SharedFlow<Event>
    get() = _bookmarkEventsFlow.asSharedFlow()

  suspend fun init(threadBookmarksFromDatabase: List<ThreadBookmark>) {
    mutex.withLock {
      threadBookmarks.clear()

      threadBookmarksFromDatabase.forEach { threadBookmark ->
        threadBookmarks[threadBookmark.threadDescriptor] = threadBookmark
      }
    }

    _bookmarkEventsFlow.emit(Event.Loaded)
  }

  suspend fun contains(threadDescriptor: ThreadDescriptor): Boolean {
    return mutex.withLock { threadBookmarks.contains(threadDescriptor) }
  }

  suspend fun activeBookmarksCount(): Int {
    return mutex.withLock { threadBookmarks.values.count { threadBookmark -> threadBookmark.isActive() } }
  }

  suspend fun hasActiveBookmarks(): Boolean {
    return mutex.withLock {
      return@withLock threadBookmarks.any { (_, threadBookmark) -> threadBookmark.isActive() }
    }
  }

  suspend fun getBookmark(threadDescriptor: ThreadDescriptor): ThreadBookmark? {
    return mutex.withLock { threadBookmarks[threadDescriptor]?.deepCopy() }
  }

  suspend fun getBookmarks(threadDescriptors: List<ThreadDescriptor>): List<ThreadBookmark> {
    return mutex.withLock {
      return@withLock threadDescriptors.mapNotNull { threadDescriptor ->
        threadBookmarks[threadDescriptor]?.deepCopy()
      }
    }
  }

  suspend fun getActiveBookmarkDescriptors(): List<ThreadDescriptor> {
    return mutex.withLock {
      threadBookmarks.values
        .filter { threadBookmark -> threadBookmark.isActive() }
        .map { threadBookmark -> threadBookmark.threadDescriptor }
    }
  }

  suspend fun putBookmark(threadBookmark: ThreadBookmark) {
    val created = mutex.withLock {
      val created = !threadBookmarks.containsKey(threadBookmark.threadDescriptor)

      threadBookmarks[threadBookmark.threadDescriptor] = threadBookmark
      return@withLock created
    }

    if (created) {
      _bookmarkEventsFlow.emit(Event.Created(listOf(threadBookmark.threadDescriptor)))
    } else {
      _bookmarkEventsFlow.emit(Event.Updated(listOf(threadBookmark.threadDescriptor)))
    }
  }

  suspend fun removeBookmark(threadDescriptor: ThreadDescriptor) {
    val deleted = mutex.withLock { threadBookmarks.remove(threadDescriptor) } != null
    if (deleted) {
      _bookmarkEventsFlow.emit(Event.Deleted(listOf(threadDescriptor)))
    }
  }

  suspend fun onPostViewed(postDescriptor: PostDescriptor, unseenPostsCount: Int): Boolean {
    val updated = mutex.withLock {
      val threadBookmark = threadBookmarks[postDescriptor.threadDescriptor]
        ?: return@withLock false

      val lastViewedPostPostDescriptor = threadBookmark.lastViewedPostPostDescriptor

      if (lastViewedPostPostDescriptor != null && postDescriptor < lastViewedPostPostDescriptor) {
        return@withLock false
      }

      threadBookmark.updateSeenPostsCount(unseenPostsCount)
      threadBookmark.updateLastViewedPostDescriptor(postDescriptor)
      threadBookmark.readRepliesUpTo(postDescriptor)

      return true
    }

    if (updated) {
      _bookmarkEventsFlow.emit(Event.Updated(listOf(postDescriptor.threadDescriptor)))
    }

    return updated
  }

  sealed class Event(val threadDescriptors: List<ThreadDescriptor>) {
    object Loaded : Event(emptyList())
    class Created(threadDescriptors: List<ThreadDescriptor>): Event(threadDescriptors)
    class Updated(threadDescriptors: List<ThreadDescriptor>): Event(threadDescriptors)
    class Deleted(threadDescriptors: List<ThreadDescriptor>): Event(threadDescriptors)
  }

}