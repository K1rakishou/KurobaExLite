package com.github.k1rakishou.kurobaexlite.helpers

import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import java.util.concurrent.atomic.AtomicInteger

object AppConstants {
  const val TEXT_SEPARATOR = " • "
  const val deleteNavHistoryTimeoutMs = 5000L
  const val deleteBookmarkTimeoutMs = 5000L
  const val minFlingVelocityPx = 5000f

  const val CREATE_NEW_ISSUE_URL = "https://github.com/K1rakishou/KurobaExLite/issues"

  object Transition {
    val TransitionFps = 1f / 16f
  }

  object Actions {
    const val REPLY_NOTIFICATION_CLICKED_ACTION = "${BuildConfig.APPLICATION_ID}_reply_notification_clicked_action"
    const val REPLY_NOTIFICATION_DELETED_ACTION = "${BuildConfig.APPLICATION_ID}_reply_notification_deleted_action"
  }

  object RequestCodes {
    const val LOCAL_FILE_PICKER_LAST_SELECTION_REQUEST_CODE = 1

    private val requestCodeCounter = AtomicInteger(1000)
    fun nextRequestCode(): Int {
      return requestCodeCounter.incrementAndGet()
    }
  }

  object WorkerTags {
    private const val BOOKMARK_WATCHER_TAG = "com.github.k1rakishou.kurobaexlite.helpers.BookmarkWatcher"

    fun getUniqueTag(flavorType: AndroidHelpers.FlavorType): String {
      return "${BOOKMARK_WATCHER_TAG}_${flavorType.name}"
    }
  }

  object Notifications {
    const val REPLIES_SUMMARY_NOTIFICATION_ID = 0
    const val REPLIES_PRE_OREO_NOTIFICATION_ID = 1

    const val MAX_LINES_IN_NOTIFICATION = 5
    // Android limitations
    const val MAX_VISIBLE_NOTIFICATIONS = 20

    val NOTIFICATION_THUMBNAIL_SIZE = 160

    object Reply {
      val notificationIdCounter = AtomicInteger(1000000)
      val notificationIdMap = mutableMapOf<ThreadDescriptor, Int>()

      fun notificationId(threadDescriptor: ThreadDescriptor): Int {
        val prevNotificationId = notificationIdMap[threadDescriptor]
        if (prevNotificationId != null) {
          return prevNotificationId
        }

        val newNotificationId = notificationIdCounter.incrementAndGet()
        notificationIdMap[threadDescriptor] = newNotificationId

        return newNotificationId
      }

      const val NOTIFICATION_TAG_PREFIX = "reply_"

      const val REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_reply_summary_notifications_channel"
      const val REPLY_SUMMARY_NOTIFICATION_NAME = "Notification channel for new replies summary"
      const val REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_reply_summary_silent_notifications_channel"
      const val REPLY_SUMMARY_SILENT_NOTIFICATION_NAME = "Notification channel for new replies summary (silent)"
      const val REPLY_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_replies_notifications_channel"
      const val REPLY_NOTIFICATION_CHANNEL_NAME = "Notification channel for replies (Yous)"

      val SUMMARY_NOTIFICATION_TAG = "${BuildConfig.APPLICATION_ID}_REPLIES_SUMMARY_NOTIFICATION_TAG"
      val REPLIES_PRE_OREO_NOTIFICATION_TAG = "${BuildConfig.APPLICATION_ID}_REPLIES_PRE_OREO_NOTIFICATION_TAG"

      const val R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY = "reply_notification_click_thread_descriptors"
      const val R_NOTIFICATION_CLICK_POST_DESCRIPTORS_KEY = "reply_notification_click_post_descriptors"
      const val R_NOTIFICATION_DELETE_THREAD_DESCRIPTORS_KEY = "reply_notification_deleted_thread_descriptors"
    }

    object Update {
      const val UPDATE_NOTIFICATION_TAG = "${BuildConfig.APPLICATION_ID}_UPDATE_NOTIFICATION_TAG"
      const val UPDATE_NOTIFICATION_ID = 3

      const val UPDATE_NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}_update_notification_channel"
      const val UPDATE_NOTIFICATION_NAME = "Notification channel for apk updates notification"
    }
  }

}