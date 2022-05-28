package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmarkReply
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.CompletableDeferred
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

  private val _backgroundWatcherEventsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.UNLIMITED)
  val backgroundWatcherEventsFlow: SharedFlow<Unit>
    get() = _backgroundWatcherEventsFlow.asSharedFlow()

  private val initializationFlag = CompletableDeferred<Unit>()

  suspend fun awaitUntilInitialized() {
    initializationFlag.await()
  }

  suspend fun init(threadBookmarksFromDatabase: List<ThreadBookmark>) {
    try {
      mutex.withLock {
        threadBookmarks.clear()

        threadBookmarksFromDatabase.forEach { threadBookmark ->
          threadBookmarks[threadBookmark.threadDescriptor] = threadBookmark
        }
      }
    } finally {
      _bookmarkEventsFlow.emit(Event.Loaded)
      initializationFlag.complete(Unit)
    }
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

  suspend fun getAllBookmarks(): List<ThreadBookmark> {
   return mutex.withLock { threadBookmarks.values.map { threadBookmark -> threadBookmark.deepCopy() } }
  }

  suspend fun getActiveBookmarkDescriptors(): List<ThreadDescriptor> {
    return mutex.withLock {
      threadBookmarks.values
        .filter { threadBookmark -> threadBookmark.isActive() }
        .map { threadBookmark -> threadBookmark.threadDescriptor }
    }
  }

  suspend fun getActiveThreadBookmarkReplies(): Map<ThreadDescriptor, Set<ThreadBookmarkReply>> {
    return mutex.withLock {
      val resultMap = mutableMapOf<ThreadDescriptor, Set<ThreadBookmarkReply>>()

      threadBookmarks.entries.forEach { (threadDescriptor, threadBookmark) ->
        val unreadThreadBookmarkReplySet = threadBookmark.threadBookmarkReplies.values
          .filter { threadBookmarkReply -> !threadBookmarkReply.alreadyRead }
          .toSet()

        if (unreadThreadBookmarkReplySet.isEmpty()) {
          return@forEach
        }

        resultMap[threadDescriptor] = unreadThreadBookmarkReplySet
      }

      return@withLock resultMap
    }
  }

  suspend fun putBookmark(threadBookmark: ThreadBookmark, index: Int? = null) {
    val created = mutex.withLock {
      val created = !threadBookmarks.containsKey(threadBookmark.threadDescriptor)

      threadBookmarks[threadBookmark.threadDescriptor] = threadBookmark
      return@withLock created
    }

    if (created) {
      _bookmarkEventsFlow.emit(Event.Created(index, listOf(threadBookmark.threadDescriptor)))
    } else {
      _bookmarkEventsFlow.emit(Event.Updated(listOf(threadBookmark.threadDescriptor)))
    }
  }

  suspend fun updateBookmark(
    threadDescriptor: ThreadDescriptor,
    updater: (ThreadBookmark) -> Boolean
  ): Boolean {
    val updated = mutex.withLock {
      val threadBookmark = threadBookmarks[threadDescriptor]
        ?: return@withLock false

      if (!updater(threadBookmark)) {
        return@withLock false
      }

      threadBookmarks[threadDescriptor] = threadBookmark
      return@withLock true
    }

    if (updated) {
      _bookmarkEventsFlow.emit(Event.Updated(listOf(threadDescriptor)))
    }

    return updated
  }

  suspend fun updateBookmarks(
    threadDescriptors: Collection<ThreadDescriptor>,
    updater: (ThreadBookmark) -> Boolean
  ): List<ThreadDescriptor> {
    val updated = mutex.withLock {
      val updated = mutableListOf<ThreadDescriptor>()

      threadDescriptors.forEach { threadDescriptor ->
        val threadBookmark = threadBookmarks[threadDescriptor]
          ?: return@forEach

        if (updater(threadBookmark)) {
          threadBookmarks[threadDescriptor] = threadBookmark
          updated += threadDescriptor
        }
      }

      return@withLock updated
    }

    if (updated.isNotEmpty()) {
      _bookmarkEventsFlow.emit(Event.Updated(updated))
    }

    return updated
  }

  suspend fun getInactiveBookmarkDescriptors(): List<ThreadDescriptor> {
    return mutex.withLock {
      threadBookmarks.values
        .filter { threadBookmark -> !threadBookmark.isActive() }
        .map { threadBookmark -> threadBookmark.threadDescriptor }
    }
  }

  suspend fun removeBookmark(threadDescriptor: ThreadDescriptor): ThreadBookmark? {
    val deletedBookmark = mutex.withLock { threadBookmarks.remove(threadDescriptor) }
    if (deletedBookmark != null) {
      _bookmarkEventsFlow.emit(Event.Deleted(listOf(threadDescriptor)))
    }

    return deletedBookmark
  }

  suspend fun removeBookmarks(threadDescriptors: List<ThreadDescriptor>): List<ThreadBookmark> {
    if (threadDescriptors.isEmpty()) {
      return emptyList()
    }

    val actuallyDeleted = mutex.withLock {
      val actuallyDeleted = mutableListOf<ThreadBookmark>()

      threadDescriptors.forEach { threadDescriptor ->
        val deletedBookmark = threadBookmarks.remove(threadDescriptor)
        if (deletedBookmark != null) {
          actuallyDeleted += deletedBookmark
        }
      }

      return@withLock actuallyDeleted
    }

    if (actuallyDeleted.isNotEmpty()) {
      val descriptors = actuallyDeleted.map { it.threadDescriptor }
      _bookmarkEventsFlow.emit(Event.Deleted(descriptors))
    }

    return actuallyDeleted
  }

  suspend fun onBackgroundWatcherWorkFinished() {
    _backgroundWatcherEventsFlow.emit(Unit)
  }

  sealed class Event(val threadDescriptors: List<ThreadDescriptor>) {
    object Loaded : Event(emptyList())
    class Created(val index: Int?, threadDescriptors: List<ThreadDescriptor>): Event(threadDescriptors)
    class Updated(threadDescriptors: List<ThreadDescriptor>): Event(threadDescriptors)
    class Deleted(threadDescriptors: List<ThreadDescriptor>): Event(threadDescriptors)
  }

}