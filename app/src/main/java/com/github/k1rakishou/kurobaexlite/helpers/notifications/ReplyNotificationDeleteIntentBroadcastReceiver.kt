package com.github.k1rakishou.kurobaexlite.helpers.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.executors.SerializedCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.LoadBookmarks
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.PersistBookmarks
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class ReplyNotificationDeleteIntentBroadcastReceiver : BroadcastReceiver() {

  private val bookmarksManager: BookmarksManager by inject(BookmarksManager::class.java)
  private val loadBookmarks: LoadBookmarks by inject(LoadBookmarks::class.java)
  private val persistBookmarks: PersistBookmarks by inject(PersistBookmarks::class.java)

  private val scope = KurobaCoroutineScope()
  private val serializedExecutor = SerializedCoroutineExecutor(scope)

  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent == null) {
      return
    }

    val extras = intent.extras
      ?: return

    if (!intent.hasExtra(AppConstants.Notifications.Reply.R_NOTIFICATION_DELETE_THREAD_DESCRIPTORS_KEY)) {
      return
    }

    val threadDescriptors = extras.getParcelableArrayList<ThreadDescriptor>(
      AppConstants.Notifications.Reply.R_NOTIFICATION_DELETE_THREAD_DESCRIPTORS_KEY
    )

    if (threadDescriptors.isNullOrEmpty()) {
      return
    }

    logcat(TAG) { "Adding new notification swipe request, threadDescriptorsCount=${threadDescriptors.size}" }
    val pendingResult = goAsync()

    serializedExecutor.post {
      try {
        loadBookmarks.executeSuspend()

        markBookmarksAsSeen(threadDescriptors)
      } finally {
        pendingResult.finish()
      }
    }
  }

  private suspend fun markBookmarksAsSeen(threadDescriptors: List<ThreadDescriptor>) {
    val updatedBookmarkDescriptors = bookmarksManager.updateBookmarks(threadDescriptors) { threadBookmark ->
      threadBookmark.markAsSeenAllReplies()
      return@updateBookmarks true
    }

    logcat(TAG) {
      "markBookmarksAsSeen() marking as seen ${threadDescriptors.size} bookmarks " +
        "(updatedCount=${updatedBookmarkDescriptors.size})"
    }

    persistBookmarks.await(updatedBookmarkDescriptors)
  }

  companion object {
    private const val TAG = "ReplyNotificationDeleteIntentBroadcastReceiver"
  }

}