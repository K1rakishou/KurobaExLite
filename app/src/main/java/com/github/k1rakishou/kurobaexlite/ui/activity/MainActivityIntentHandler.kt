package com.github.k1rakishou.kurobaexlite.ui.activity

import android.content.Intent
import com.github.k1rakishou.kurobaexlite.features.bookmarks.BookmarksScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.PersistBookmarks
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.logcat

class MainActivityIntentHandler(
  private val globalUiInfoManager: GlobalUiInfoManager,
  private val bookmarksManager: BookmarksManager,
  private val persistBookmarks: PersistBookmarks
) {
  private var threadScreenViewModel: ThreadScreenViewModel? = null
  private var catalogScreenViewModel: CatalogScreenViewModel? = null
  private var bookmarkScreenViewModel: BookmarksScreenViewModel? = null

  fun onCreate(
    threadScreenViewModel: ThreadScreenViewModel,
    catalogScreenViewModel: CatalogScreenViewModel,
    bookmarkScreenViewModel: BookmarksScreenViewModel
  ) {
    this.threadScreenViewModel = threadScreenViewModel
    this.catalogScreenViewModel = catalogScreenViewModel
    this.bookmarkScreenViewModel = bookmarkScreenViewModel
  }

  fun onDestroy() {
    this.threadScreenViewModel = null
    this.catalogScreenViewModel = null
    this.bookmarkScreenViewModel = null
  }

  suspend fun onNewIntent(intent: Intent) {
    logcat(TAG) { "Got intent with action '${intent.action}'" }

    when (intent.action) {
      AppConstants.Actions.REPLY_NOTIFICATION_CLICKED_ACTION -> {
        val threadDescriptors = intent.getParcelableArrayListExtra<ThreadDescriptor>(
          AppConstants.Notifications.Reply.R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY
        ) ?: emptyList()

        val postDescriptors = intent.getParcelableArrayListExtra<PostDescriptor>(
          AppConstants.Notifications.Reply.R_NOTIFICATION_CLICK_POST_DESCRIPTORS_KEY
        ) ?: emptyList()

        onReplyNotificationClicked(threadDescriptors, postDescriptors)
        markBookmarksAsSeen(threadDescriptors)
      }
      AppConstants.Actions.REPLY_NOTIFICATION_DELETED_ACTION -> {
        // Handled in ReplyNotificationDeleteIntentBroadcastReceiver
      }
    }
  }

  private suspend fun onReplyNotificationClicked(
    threadDescriptors: List<ThreadDescriptor>,
    postDescriptors: List<PostDescriptor>
  ) {
    logcat(TAG) { "onReplyNotificationClicked()" }

    if (threadDescriptors.isEmpty()) {
      logcat(TAG) { "onReplyNotificationClicked() threadDescriptors.isEmpty()" }
      return
    }

    if (threadDescriptors.size > 1) {
      logcat(TAG) { "onReplyNotificationClicked() threadDescriptors.size > 1 openDrawer()" }

      bookmarkScreenViewModel?.markBookmarks(threadDescriptors)
      globalUiInfoManager.openDrawer(withAnimation = true)
      return
    }

    if (postDescriptors.isNotEmpty()) {
      logcat(TAG) { "onReplyNotificationClicked() postDescriptors.isNotEmpty() loadThreadAndMarkPost()" }

      val firstPostDescriptor = postDescriptors.firstOrNull()
      if (firstPostDescriptor == null) {
        logcat(TAG) { "onReplyNotificationClicked() postDescriptors.isNotEmpty(), firstPostDescriptor == null" }
        return
      }

      threadScreenViewModel?.loadThread(
        threadDescriptor = firstPostDescriptor.threadDescriptor,
        loadOptions = PostScreenViewModel.LoadOptions(
          forced = true,
          scrollToPost = firstPostDescriptor
        )
      )
    } else {
      logcat(TAG) { "onReplyNotificationClicked() postDescriptors.isEmpty() loadThread()" }

      val firstThreadDescriptors = threadDescriptors.firstOrNull()
      if (firstThreadDescriptors == null) {
        logcat(TAG) { "onReplyNotificationClicked() postDescriptors.isEmpty(), firstThreadDescriptors == null" }
        return
      }

      threadScreenViewModel?.loadThread(firstThreadDescriptors)
    }

    globalUiInfoManager.waitUntilLayoutModeIsKnown()
    globalUiInfoManager.updateCurrentPage(
      screenKey = ThreadScreen.SCREEN_KEY,
      animate = true
    )
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
    private const val TAG = "MainActivityIntentHandler"
  }

}