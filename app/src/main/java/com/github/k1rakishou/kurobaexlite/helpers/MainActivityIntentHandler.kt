package com.github.k1rakishou.kurobaexlite.helpers

import android.content.Intent
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import logcat.logcat

class MainActivityIntentHandler(
  private val globalUiInfoManager: GlobalUiInfoManager
) {
  private var threadScreenViewModel: ThreadScreenViewModel? = null
  private var catalogScreenViewModel: CatalogScreenViewModel? = null

  fun onCreate(threadScreenViewModel: ThreadScreenViewModel, catalogScreenViewModel: CatalogScreenViewModel) {
    this.threadScreenViewModel = threadScreenViewModel
    this.catalogScreenViewModel = catalogScreenViewModel
  }

  fun onDestroy() {
    this.threadScreenViewModel = null
    this.catalogScreenViewModel = null
  }

  fun onNewIntent(intent: Intent) {
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
      }
      AppConstants.Actions.REPLY_NOTIFICATION_DELETED_ACTION -> {
        // Handled in ReplyNotificationDeleteIntentBroadcastReceiver
      }
    }
  }

  private fun onReplyNotificationClicked(
    threadDescriptors: List<ThreadDescriptor>,
    postDescriptors: List<PostDescriptor>
  ) {
    logcat(TAG) { "onReplyNotificationClicked()" }

    if (threadDescriptors.isEmpty()) {
      logcat(TAG) { "onReplyNotificationClicked() threadDescriptors.isEmpty()" }
      return
    }

    if (threadDescriptors.size > 1) {
      // TODO(KurobaEx): mark thread descriptors in the drawer
      logcat(TAG) { "onReplyNotificationClicked() threadDescriptors.size > 1 openDrawer()" }
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
  }

  companion object {
    private const val TAG = "MainActivityIntentHandler"
  }

}