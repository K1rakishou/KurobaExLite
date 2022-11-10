package com.github.k1rakishou.kurobaexlite.helpers.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import coil.transform.CircleCropTransformation
import coil.transform.Transformation
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.processDataCollectionConcurrently
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.PersistBookmarks
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.ChanThreadManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmarkReply
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.HttpUrl
import org.joda.time.DateTime
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

class ReplyNotificationsHelper(
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val appSettings: AppSettings,
  private val androidHelpers: AndroidHelpers,
  private val imageLoader: ImageLoader,
  private val notificationManagerCompat: NotificationManagerCompat,
  private val notificationManager: NotificationManager,
  private val chanThreadManager: ChanThreadManager,
  private val bookmarksManager: BookmarksManager,
  private val themeEngine: ThemeEngine,
  private val postCommentParser: PostCommentParser,
  private val persistBookmarks: PersistBookmarks
) {
  private val debouncer = DebouncingCoroutineExecutor(appScope)
  private val working = AtomicBoolean(false)

  fun init() {
    appScope.launch {
      bookmarksManager.bookmarkEventsFlow
        .filter { bookmarkEvent ->
          return@filter when (bookmarkEvent) {
            is BookmarksManager.Event.Created -> false
            is BookmarksManager.Event.Loaded,
            is BookmarksManager.Event.Deleted,
            is BookmarksManager.Event.Updated -> true
          }
        }
        .collect { showOrUpdateNotifications() }
    }
  }

  fun showOrUpdateNotifications() {
    debouncer.post(NOTIFICATIONS_UPDATE_DEBOUNCE_TIME) {
      if (!working.compareAndSet(false, true)) {
        return@post
      }

      try {
        showOrUpdateNotificationsInternal()
      } finally {
        working.set(false)
      }
    }
  }

  private suspend fun showOrUpdateNotificationsInternal() {
    if (!appSettings.replyNotifications.read()) {
      logcat(TAG) { "showOrUpdateNotificationsInternal() appSettings.replyNotifications == false" }
      return
    }

    bookmarksManager.awaitUntilInitialized()

    val currentlyOpenedThread = chanThreadManager.currentlyOpenedThread
    val unreadNotificationsGrouped = bookmarksManager.getActiveThreadBookmarkReplies()

    logcat(TAG, LogPriority.VERBOSE) {
      "showOrUpdateNotificationsInternal() " +
        "currentlyOpenedThread=${currentlyOpenedThread}, " +
        "unreadNotificationsGrouped=${unreadNotificationsGrouped.size}"
    }

    val shownNotifications = showNotificationForReplies(unreadNotificationsGrouped, currentlyOpenedThread)
    if (shownNotifications.isEmpty()) {
      return
    }

    val threadDescriptors = shownNotifications.keys

    // Mark all shown notifications as notified so we won't show them again
    val updatedBookmarkDescriptors = bookmarksManager.updateBookmarks(
      threadDescriptors = threadDescriptors,
      updater = { threadBookmark ->
        val threadBookmarkReplies = shownNotifications[threadBookmark.threadDescriptor]
        if (threadBookmarkReplies.isNullOrEmpty()) {
          return@updateBookmarks false
        }

        var updated = false

        threadBookmarkReplies.forEach { threadBookmarkReplyView ->
          val threadBookmarkReply = threadBookmark.threadBookmarkReplies[threadBookmarkReplyView.postDescriptor]
            ?: return@forEach

          threadBookmarkReply.alreadyNotified = true
          updated = true
        }

        return@updateBookmarks updated
      }
    )

    logcat(TAG, LogPriority.VERBOSE) {
      "showOrUpdateNotificationsInternal() updatedBookmarkDescriptors=${updatedBookmarkDescriptors.size}"
    }

    persistBookmarks.await(updatedBookmarkDescriptors)
  }

  private suspend fun showNotificationForReplies(
    unreadNotificationsGrouped: Map<ThreadDescriptor, Set<ThreadBookmarkReply>>,
    currentlyOpenedThread: ThreadDescriptor?
  ): Map<ThreadDescriptor, Set<ThreadBookmarkReply>> {
    logcat(TAG) {
      "showNotificationForReplies(unreadNotificationsGroupedCount=${unreadNotificationsGrouped.size}, " +
        "currentlyOpenedThread=${currentlyOpenedThread})"
    }

    if (unreadNotificationsGrouped.isEmpty()) {
      logcat(TAG) { "showNotificationForReplies() unreadNotificationsGrouped are empty" }

      closeAllNotifications()
      return emptyMap()
    }

    if (!androidHelpers.isAndroidO()) {
      return showNotificationsForAndroidNougatAndBelow(
        unreadNotificationsGrouped = unreadNotificationsGrouped,
        currentlyOpenedThread = currentlyOpenedThread
      )
    }

    setupChannels()
    restoreNotificationIdMap(unreadNotificationsGrouped)

    val sortedUnreadNotificationsGrouped = sortNotifications(unreadNotificationsGrouped)
    val notificationTime = sortedUnreadNotificationsGrouped.values.flatten()
      .maxByOrNull { threadBookmarkReply -> threadBookmarkReply.time }
      ?.time
      ?: DateTime.now()

    val hasUnseenReplies = showSummaryNotification(
      notificationTime = notificationTime,
      unreadNotificationsGrouped = sortedUnreadNotificationsGrouped,
      currentlyOpenedThread = currentlyOpenedThread
    )

    if (!hasUnseenReplies) {
      logcat(TAG) { "showNotificationForReplies() showSummaryNotification() hasUnseenReplies==false" }

      closeAllNotifications()
      return emptyMap()
    }

    val shownNotifications = showNotificationsForAndroidOreoAndAbove(
      notificationTime = notificationTime,
      unreadNotificationsGrouped = sortedUnreadNotificationsGrouped
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      notificationManager.activeNotifications.forEach { notification ->
        logcat(TAG, LogPriority.VERBOSE) {
          "active notification, id: ${notification.id}, " +
          "isGroup=${notification.isGroup}, " +
          "group=${notification.notification.group}, " +
          "groupAlertBehavior=${notification.notification.groupAlertBehavior}"
        }
      }
    }

    return shownNotifications
  }

  private fun sortNotifications(
    unreadNotificationsGrouped: Map<ThreadDescriptor, Set<ThreadBookmarkReply>>
  ): Map<ThreadDescriptor, List<ThreadBookmarkReply>> {
    val sortedNotifications = mutableMapOf<ThreadDescriptor, MutableList<ThreadBookmarkReply>>()

    unreadNotificationsGrouped.forEach { (threadDescriptor, replies) ->
      if (replies.isEmpty()) {
        return@forEach
      }

      val listOrReplies = sortedNotifications.getOrPut(
        key = threadDescriptor,
        defaultValue = { ArrayList(replies.size) }
      )

      listOrReplies.addAll(replies.sortedWith(REPLIES_COMPARATOR))
    }

    return sortedNotifications
  }

  private suspend fun showNotificationsForAndroidNougatAndBelow(
    unreadNotificationsGrouped: Map<ThreadDescriptor, Set<ThreadBookmarkReply>>,
    currentlyOpenedThread: ThreadDescriptor?
  ): Map<ThreadDescriptor, Set<ThreadBookmarkReply>> {
    val threadsWithUnseenRepliesCount = unreadNotificationsGrouped.size
    val totalUnseenRepliesCount = unreadNotificationsGrouped.values.sumOf { replies -> replies.size }

    val titleText = appContext.resources.getString(
      R.string.reply_notifications_new_replies_total_stats,
      totalUnseenRepliesCount,
      threadsWithUnseenRepliesCount
    )

    val unseenRepliesCount = unreadNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadySeen }
    val hasUnseenReplies = unseenRepliesCount > 0

    val newRepliesCount = unreadNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadyNotified }
    val hasNewReplies = newRepliesCount > 0

    val onlyHasNewRepliesFromCurrentThread = checkOnlyHasRepliesFromCurrentThread(
      unreadNotificationsGrouped = unreadNotificationsGrouped,
      currentlyOpenedThread = currentlyOpenedThread
    )

    logcat(TAG) {
      "showNotificationsForAndroidNougatAndBelow() " +
        "onlyHasNewRepliesFromCurrentThread: $onlyHasNewRepliesFromCurrentThread, " +
        "unreadNotificationsGrouped: ${unreadNotificationsGrouped.size}, " +
        "unseenRepliesCount: $unseenRepliesCount, " +
        "newRepliesCount: $newRepliesCount"
    }

    val iconId = if (hasUnseenReplies) {
      logcat(TAG) {"showNotificationsForAndroidNougatAndBelow() Using R.drawable.ic_stat_notify_alert icon" }
      R.drawable.ic_stat_notify_alert
    } else {
      logcat(TAG) {"showNotificationsForAndroidNougatAndBelow() Using R.drawable.ic_stat_notify icon" }
      R.drawable.ic_stat_notify
    }

    val notificationPriority = if (hasNewReplies && !onlyHasNewRepliesFromCurrentThread) {
      logcat(TAG) { "showNotificationsForAndroidNougatAndBelow() Using NotificationCompat.PRIORITY_MAX" }
      NotificationCompat.PRIORITY_MAX
    } else {
      logcat(TAG) { "showNotificationsForAndroidNougatAndBelow() Using NotificationCompat.PRIORITY_LOW" }
      NotificationCompat.PRIORITY_LOW
    }

    val unseenThreadBookmarkReplies = unreadNotificationsGrouped
      .flatMap { (_, replies) -> replies }
      .filter { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadySeen }
      .sortedWith(REPLIES_COMPARATOR)
      .take(AppConstants.Notifications.MAX_LINES_IN_NOTIFICATION)

    if (unseenThreadBookmarkReplies.isEmpty()) {
      notificationManagerCompat.cancel(
        AppConstants.Notifications.Reply.REPLIES_PRE_OREO_NOTIFICATION_TAG,
        AppConstants.Notifications.REPLIES_PRE_OREO_NOTIFICATION_ID
      )

      logcat(TAG) {
        "showNotificationsForAndroidNougatAndBelow() " +
          "unseenThreadBookmarkReplies is empty, notification closed"
      }

      return unreadNotificationsGrouped
    }

    val notificationTime = unseenThreadBookmarkReplies
      .maxByOrNull { threadBookmarkReply -> threadBookmarkReply.time }
      ?.time
      ?: DateTime.now()

    val preOreoNotificationBuilder = NotificationCompat.Builder(appContext)
      .setWhen(notificationTime.millis)
      .setShowWhen(true)
      .setContentTitle(androidHelpers.getApplicationLabel())
      .setContentText(titleText)
      .setSmallIcon(iconId)
      .setupClickOnNotificationIntent(
        requestCode = AppConstants.RequestCodes.nextRequestCode(),
        threadDescriptors = unreadNotificationsGrouped.keys,
        postDescriptors = unreadNotificationsGrouped.values.flatMap { threadBookmarkViewSet ->
          threadBookmarkViewSet.map { threadBookmarkReplyView -> threadBookmarkReplyView.postDescriptor }
        }
      )
      .setupDeleteNotificationIntent(unreadNotificationsGrouped.keys)
      .setAutoCancel(true)
      .setAllowSystemGeneratedContextualActions(false)
      .setPriority(notificationPriority)
      .setupSoundAndVibration(
        hasNewReplies = hasNewReplies,
        onlyHasNewRepliesFromCurrentThread = onlyHasNewRepliesFromCurrentThread
      )
      .setupReplyNotificationsStyle(titleText, unseenThreadBookmarkReplies)
      .setGroup(notificationsGroup)
      .setGroupSummary(true)

    notificationManagerCompat.notify(
      AppConstants.Notifications.Reply.REPLIES_PRE_OREO_NOTIFICATION_TAG,
      AppConstants.Notifications.REPLIES_PRE_OREO_NOTIFICATION_ID,
      preOreoNotificationBuilder.build()
    )

    logcat(TAG) { "showNotificationsForAndroidNougatAndBelow() notificationManagerCompat.notify() called" }
    return unreadNotificationsGrouped
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private suspend fun showSummaryNotification(
    notificationTime: DateTime,
    unreadNotificationsGrouped: Map<ThreadDescriptor, List<ThreadBookmarkReply>>,
    currentlyOpenedThread: ThreadDescriptor?
  ): Boolean {
    val unseenRepliesCount = unreadNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadySeen }
    val hasUnseenReplies = unseenRepliesCount > 0

    if (!hasUnseenReplies) {
      logcat(TAG) { "showSummaryNotification() no unseen replies left after filtering" }
      return false
    }

    val newRepliesCount = unreadNotificationsGrouped.values
      .flatten()
      .count { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadyNotified }

    val hasNewReplies = newRepliesCount > 0
    val onlyHasNewRepliesFromCurrentThread = checkOnlyHasRepliesFromCurrentThread(
      unreadNotificationsGrouped = unreadNotificationsGrouped,
      currentlyOpenedThread = currentlyOpenedThread
    )

    logcat(TAG) {
      "showSummaryNotification() " +
        "onlyHasNewRepliesFromCurrentThread=$onlyHasNewRepliesFromCurrentThread, " +
        "unreadNotificationsGrouped = ${unreadNotificationsGrouped.size}, " +
        "unseenRepliesCount=$unseenRepliesCount, newRepliesCount=$newRepliesCount"
    }

    val iconId = if (hasUnseenReplies) {
      logcat(TAG) { "showSummaryNotification() Using R.drawable.ic_stat_notify_alert icon" }
      R.drawable.ic_stat_notify_alert
    } else {
      logcat(TAG) { "showSummaryNotification() Using R.drawable.ic_stat_notify icon" }
      R.drawable.ic_stat_notify
    }

    val summaryNotificationBuilder = if (hasNewReplies && !onlyHasNewRepliesFromCurrentThread) {
      logcat(TAG) { "showSummaryNotification() Using REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID" }

      NotificationCompat.Builder(
        appContext,
        AppConstants.Notifications.Reply.REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID
      )
    } else {
      logcat(TAG) { "showSummaryNotification() Using REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID" }

      NotificationCompat.Builder(
        appContext,
        AppConstants.Notifications.Reply.REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID
      )
    }

    val threadsWithUnseenRepliesCount = unreadNotificationsGrouped.size
    val totalUnseenRepliesCount = unreadNotificationsGrouped.values.sumOf { replies -> replies.size }

    val titleText = appContext.resources.getString(
      R.string.reply_notifications_new_replies_total_stats,
      totalUnseenRepliesCount,
      threadsWithUnseenRepliesCount
    )

    check(threadsWithUnseenRepliesCount > 0) { "Bad threadsWithUnseenRepliesCount" }
    check(totalUnseenRepliesCount > 0) { "Bad totalUnseenRepliesCount" }

    summaryNotificationBuilder
      .setWhen(notificationTime.millis)
      .setShowWhen(true)
      .setContentTitle(androidHelpers.getApplicationLabel())
      .setContentText(titleText)
      .setSmallIcon(iconId)
      .setupSoundAndVibration(
        hasNewReplies = hasNewReplies,
        onlyHasNewRepliesFromCurrentThread = onlyHasNewRepliesFromCurrentThread
      )
      .setupSummaryNotificationsStyle(titleText)
      .setupClickOnNotificationIntent(
        requestCode = AppConstants.RequestCodes.nextRequestCode(),
        threadDescriptors = unreadNotificationsGrouped.keys,
        postDescriptors = unreadNotificationsGrouped.values.flatMap { threadBookmarkViewList ->
          threadBookmarkViewList.map { threadBookmarkReplyView -> threadBookmarkReplyView.postDescriptor }
        }
      )
      .setupDeleteNotificationIntent(unreadNotificationsGrouped.keys)
      .setAllowSystemGeneratedContextualActions(false)
      .setAutoCancel(true)
      .setGroup(notificationsGroup)
      .setGroupSummary(true)

    notificationManagerCompat.notify(
      AppConstants.Notifications.Reply.SUMMARY_NOTIFICATION_TAG,
      AppConstants.Notifications.REPLIES_SUMMARY_NOTIFICATION_ID,
      summaryNotificationBuilder.build()
    )

    logcat(TAG) { "showSummaryNotification() notificationManagerCompat.notify() called" }
    return true
  }

  @RequiresApi(Build.VERSION_CODES.O)
  suspend fun showNotificationsForAndroidOreoAndAbove(
    notificationTime: DateTime,
    unreadNotificationsGrouped: Map<ThreadDescriptor, List<ThreadBookmarkReply>>
  ): Map<ThreadDescriptor, Set<ThreadBookmarkReply>> {
    logcat(TAG) { "showNotificationsForAndroidOreoAndAbove() called" }

    val shownNotifications = mutableMapOf<ThreadDescriptor, HashSet<ThreadBookmarkReply>>()
    var notificationCounter = 0

    val threadBookmarkMap = bookmarksManager.getBookmarks(unreadNotificationsGrouped.keys.toList())
      .associateBy { it.threadDescriptor }
    logcat(TAG) { "Loaded ${threadBookmarkMap.size} bookmarks" }

    val thumbnailBitmaps = getThreadThumbnails(threadBookmarkMap)
    logcat(TAG) { "Loaded ${thumbnailBitmaps.size} thumbnail bitmaps" }

    for ((threadDescriptor, threadBookmarkReplies) in unreadNotificationsGrouped) {
      val notificationTag = getUniqueNotificationTag(threadDescriptor)
      val notificationId = AppConstants.Notifications.Reply.notificationId(threadDescriptor)

      val hasUnseenReplies = threadBookmarkReplies
        .any { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadySeen }

      if (!hasUnseenReplies) {
        notificationManagerCompat.cancel(notificationTag, notificationId)
        continue
      }

      val threadBookmark = threadBookmarkMap[threadDescriptor]
        ?: continue

      val threadTitle = threadBookmark.title
        ?: threadDescriptor.asReadableString()

      val titleText = appContext.resources.getString(
        R.string.reply_notifications_new_replies,
        threadBookmarkReplies.size
      )

      val notificationBuilder = NotificationCompat.Builder(
        appContext,
        AppConstants.Notifications.Reply.REPLY_NOTIFICATION_CHANNEL_ID
      )
        .setContentTitle(titleText)
        .setWhen(notificationTime.millis)
        .setShowWhen(true)
        .setupLargeIcon(thumbnailBitmaps[threadDescriptor])
        .setSmallIcon(R.drawable.ic_stat_notify_alert)
        .setAutoCancel(true)
        .setupReplyNotificationsStyle(threadTitle, threadBookmarkReplies)
        .setupClickOnNotificationIntent(
          requestCode = AppConstants.RequestCodes.nextRequestCode(),
          threadDescriptors = listOf(threadDescriptor),
          postDescriptors = threadBookmarkReplies.map { threadBookmarkReplyView ->
            threadBookmarkReplyView.postDescriptor
          }
        )
        .setupDeleteNotificationIntent(listOf(threadDescriptor))
        .setAllowSystemGeneratedContextualActions(false)
        .setCategory(Notification.CATEGORY_MESSAGE)
        .setGroup(notificationsGroup)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

      notificationManagerCompat.notify(
        notificationTag,
        notificationId,
        notificationBuilder.build()
      )

      logcat(TAG) {
        "showNotificationsForAndroidOreoAndAbove() notificationManagerCompat.notify() " +
          "called, tag=${notificationTag}, counter=${notificationCounter}"
      }

      ++notificationCounter

      val repliesSet = shownNotifications.getOrPut(
        key = threadDescriptor,
        defaultValue = { hashSetOf() }
      )
      repliesSet.addAll(threadBookmarkReplies)

      if (notificationCounter > AppConstants.Notifications.MAX_VISIBLE_NOTIFICATIONS) {
        logcat(TAG) {
          "showNotificationsForAndroidOreoAndAbove() " +
            "notificationCounter ($notificationCounter) exceeded MAX_VISIBLE_NOTIFICATIONS"
        }

        break
      }
    }

    return shownNotifications
  }

  private fun checkOnlyHasRepliesFromCurrentThread(
    unreadNotificationsGrouped: Map<ThreadDescriptor, Collection<ThreadBookmarkReply>>,
    currentlyOpenedThread: ThreadDescriptor?
  ): Boolean {
    if (currentlyOpenedThread == null) {
      return false
    }

    for ((threadDescriptor, threadBookmarkReplies) in unreadNotificationsGrouped.entries) {
      for (threadBookmarkReply in threadBookmarkReplies) {
        if (!threadBookmarkReply.alreadyNotified && threadDescriptor != currentlyOpenedThread) {
          return false
        }
      }
    }

    return true
  }

  private suspend fun getThreadThumbnails(
    bookmarks: Map<ThreadDescriptor, ThreadBookmark>
  ): Map<ThreadDescriptor, BitmapDrawable> {
    val resultMap = ConcurrentHashMap<ThreadDescriptor, BitmapDrawable>()

    processDataCollectionConcurrently(
      dataList = bookmarks.entries,
      batchCount = MAX_THUMBNAIL_REQUESTS_PER_BATCH,
      dispatcher = Dispatchers.IO
    ) { (threadDescriptor, threadBookmark) ->
      val thumbnailUrl = threadBookmark.thumbnailUrl
        ?: return@processDataCollectionConcurrently null

      val bitmapDrawable = downloadThumbnailForNotification(thumbnailUrl)
        ?: return@processDataCollectionConcurrently null

      resultMap[threadDescriptor] = bitmapDrawable
    }

    return resultMap
  }

  private suspend fun downloadThumbnailForNotification(
    thumbnailUrl: HttpUrl,
  ): BitmapDrawable? {
    val thumbnailSize = Size(
      AppConstants.Notifications.NOTIFICATION_THUMBNAIL_SIZE,
      AppConstants.Notifications.NOTIFICATION_THUMBNAIL_SIZE
    )

    val request = ImageRequest.Builder(appContext)
      .data(thumbnailUrl)
      .size(thumbnailSize)
      .transformations(CIRCLE_CROP)
      .build()

    when (val imageResult = imageLoader.execute(request)) {
      is SuccessResult -> {
        return imageResult.drawable as BitmapDrawable
      }
      is ErrorResult -> {
        logcatError(TAG) {
          "Error while trying to load thumbnail for notification image, " +
            "error: ${imageResult.throwable.errorMessageOrClassName()}"
        }

        return null
      }
    }
  }

  private fun NotificationCompat.Builder.setupClickOnNotificationIntent(
    requestCode: Int,
    threadDescriptors: Collection<ThreadDescriptor>,
    postDescriptors: Collection<PostDescriptor>
  ): NotificationCompat.Builder {
    val intent = Intent(appContext, MainActivity::class.java)

    intent
      .setAction(AppConstants.Actions.REPLY_NOTIFICATION_CLICKED_ACTION)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setFlags(
        Intent.FLAG_ACTIVITY_CLEAR_TOP
          or Intent.FLAG_ACTIVITY_SINGLE_TOP
          or Intent.FLAG_ACTIVITY_NEW_TASK
          or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
      )
      .putParcelableArrayListExtra(
        AppConstants.Notifications.Reply.R_NOTIFICATION_CLICK_THREAD_DESCRIPTORS_KEY,
        ArrayList(threadDescriptors)
      )
      .putParcelableArrayListExtra(
        AppConstants.Notifications.Reply.R_NOTIFICATION_CLICK_POST_DESCRIPTORS_KEY,
        ArrayList(postDescriptors)
      )

    val pendingIntent = PendingIntent.getActivity(
      appContext,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    setContentIntent(pendingIntent)
    return this
  }

  private fun NotificationCompat.Builder.setupDeleteNotificationIntent(
    threadDescriptors: Collection<ThreadDescriptor>
  ): NotificationCompat.Builder {
    val intent = Intent(appContext, ReplyNotificationDeleteIntentBroadcastReceiver::class.java)

    intent
      .setAction(AppConstants.Actions.REPLY_NOTIFICATION_DELETED_ACTION)
      .putParcelableArrayListExtra(
        AppConstants.Notifications.Reply.R_NOTIFICATION_DELETE_THREAD_DESCRIPTORS_KEY,
        ArrayList(threadDescriptors)
      )

    val pendingIntent = PendingIntent.getBroadcast(
      appContext,
      AppConstants.RequestCodes.nextRequestCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    setDeleteIntent(pendingIntent)
    return this
  }

  private fun NotificationCompat.Builder.setupLargeIcon(
    bitmapDrawable: BitmapDrawable?
  ): NotificationCompat.Builder {
    if (bitmapDrawable != null) {
      setLargeIcon(bitmapDrawable.bitmap)
    }

    return this
  }

  private fun NotificationCompat.Builder.setupSoundAndVibration(
    hasNewReplies: Boolean,
    onlyHasNewRepliesFromCurrentThread: Boolean
  ): NotificationCompat.Builder {
    if (hasNewReplies) {
      if (!onlyHasNewRepliesFromCurrentThread) {
        logcat(TAG) { "Using vibration" }
        setDefaults(Notification.DEFAULT_VIBRATE)
      } else {
        logcat(TAG) { "Not using vibration" }
      }

      setLights(themeEngine.chanTheme.accentColor.toArgb(), 1000, 1000)
    }

    return this
  }

  private fun NotificationCompat.Builder.setupSummaryNotificationsStyle(
    summaryText: String
  ): NotificationCompat.Builder {
    setStyle(
      NotificationCompat.InboxStyle(this)
        .setSummaryText(summaryText)
    )

    return this
  }

  private suspend fun NotificationCompat.Builder.setupReplyNotificationsStyle(
    titleText: String,
    threadBookmarkReplySet: Collection<ThreadBookmarkReply>
  ): NotificationCompat.Builder {
    val repliesSorted = threadBookmarkReplySet
      .filter { threadBookmarkReplyView -> !threadBookmarkReplyView.alreadySeen }
      .sortedWith(REPLIES_COMPARATOR)
      .take(AppConstants.Notifications.MAX_LINES_IN_NOTIFICATION)

    val parsedReplyComments = withContext(Dispatchers.IO) {
      return@withContext repliesSorted.map { threadBookmarkReply ->
        val commentRaw = threadBookmarkReply.commentRaw
        val yourPostNo = threadBookmarkReply.repliesTo.postNo

        if (commentRaw != null) {
          // Convert to string to get rid of spans
          val parsedComment = postCommentParser.parsePostCommentAsText(
            postCommentUnparsed = commentRaw,
            postDescriptor = threadBookmarkReply.postDescriptor
          )

          if (parsedComment.isNotNullNorEmpty()) {
            return@map postProcessComment(parsedComment, yourPostNo)
          }

          // fallthrough
        }

        // Default reply in case we failed to parse the reply comment
        return@map appContext.resources.getString(
          R.string.reply_notifications_reply_format,
          threadBookmarkReply.postDescriptor.postNo,
          yourPostNo
        )
      }
    }

    if (parsedReplyComments.size > 1) {
      // If there are more than one notification to show - use InboxStyle
      val notificationStyle = NotificationCompat.InboxStyle(this)
        .setSummaryText(titleText)

      parsedReplyComments.forEach { replyComment ->
        notificationStyle.addLine(replyComment.take(MAX_NOTIFICATION_LINE_LENGTH))
      }

      setStyle(notificationStyle)
    } else {
      // If there is only one notification to show - use BigTextStyle
      check(parsedReplyComments.isNotEmpty()) { "parsedReplyComments is empty!" }
      val replyComment = parsedReplyComments.first()

      val notificationStyle = NotificationCompat.BigTextStyle(this)
        .setSummaryText(titleText)
        .bigText(replyComment)

      setStyle(notificationStyle)
    }

    return this
  }

  private fun postProcessComment(
    parsedComment: String,
    yourPostNo: Long
  ): String {
    val postCommentBuilder = StringBuilder(parsedComment)

    val quoteMatcher = QUOTE_REGEX.matcher(postCommentBuilder)
    while (quoteMatcher.find()) {
      val postNo = quoteMatcher.group(1)?.toLongOrNull()
        ?: continue

      val startIndex = quoteMatcher.start(1)
      val endIndex = quoteMatcher.end(1)

      if (startIndex >= endIndex || startIndex >= postCommentBuilder.length || endIndex >= postCommentBuilder.length) {
        continue
      }

      if (postNo == yourPostNo) {
        postCommentBuilder.replace(startIndex, endIndex, "(You)")
      } else {
        val shortPostNo = postNo % 10000
        postCommentBuilder.replace(startIndex, endIndex, "$shortPostNo")
      }
    }

    val links = LINK_EXTRACTOR.extractLinks(postCommentBuilder)
    for (link in links) {
      postCommentBuilder.replace(link.beginIndex, link.endIndex, "*Link*")
    }

    return postCommentBuilder.toString()
  }

  private fun setupChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    logcat(TAG) { "setupChannels() called" }

    AppConstants.Notifications.Reply.REPLY_SUMMARY_NOTIFICATION_CHANNEL_ID.let { channelId ->
      if (notificationManagerCompat.getNotificationChannel(channelId) == null) {
        logcat(TAG) { "setupChannels() creating ${channelId} channel" }

        // notification channel for replies summary
        val summaryChannel = NotificationChannel(
          channelId,
          AppConstants.Notifications.Reply.REPLY_SUMMARY_NOTIFICATION_NAME,
          NotificationManager.IMPORTANCE_HIGH
        )

        summaryChannel.setSound(null, null)
        summaryChannel.enableLights(true)
        summaryChannel.lightColor = themeEngine.chanTheme.accentColor.toArgb()
        summaryChannel.enableVibration(true)

        notificationManagerCompat.createNotificationChannel(summaryChannel)
      }
    }

    AppConstants.Notifications.Reply.REPLY_SUMMARY_SILENT_NOTIFICATION_CHANNEL_ID.let { channelId ->
      if (notificationManagerCompat.getNotificationChannel(channelId) == null) {
        logcat(TAG) { "setupChannels() creating ${channelId} channel" }

        // notification channel for replies summary
        val summaryChannel = NotificationChannel(
          channelId,
          AppConstants.Notifications.Reply.REPLY_SUMMARY_SILENT_NOTIFICATION_NAME,
          NotificationManager.IMPORTANCE_LOW
        )

        notificationManagerCompat.createNotificationChannel(summaryChannel)
      }
    }

    AppConstants.Notifications.Reply.REPLY_NOTIFICATION_CHANNEL_ID.let { channelId ->
      if (notificationManagerCompat.getNotificationChannel(channelId) == null) {
        logcat(TAG) { "setupChannels() creating ${channelId} channel" }

        // notification channel for replies
        val replyChannel = NotificationChannel(
          channelId,
          AppConstants.Notifications.Reply.REPLY_NOTIFICATION_CHANNEL_NAME,
          NotificationManager.IMPORTANCE_LOW
        )

        notificationManagerCompat.createNotificationChannel(replyChannel)
      }
    }
  }

  private fun getUniqueNotificationTag(threadDescriptor: ThreadDescriptor): String {
    return AppConstants.Notifications.Reply.NOTIFICATION_TAG_PREFIX + threadDescriptor.asReadableString()
  }

  @RequiresApi(Build.VERSION_CODES.M)
  private fun restoreNotificationIdMap(
    unreadNotificationsGrouped: Map<ThreadDescriptor, Set<ThreadBookmarkReply>>
  ) {
    if (!androidHelpers.isAndroidO()) {
      return
    }

    val visibleNotifications = notificationManager.activeNotifications
      .filter { statusBarNotification -> statusBarNotification.notification.group == notificationsGroup }

    if (visibleNotifications.isEmpty()) {
      return
    }

    val maxNotificationId = visibleNotifications.maxByOrNull { notification -> notification.id }?.id ?: 0
    if (maxNotificationId > AppConstants.Notifications.Reply.notificationIdCounter.get()) {
      AppConstants.Notifications.Reply.notificationIdCounter.set(maxNotificationId)
    }

    val descriptorToTagMap = mutableMapOf<ThreadDescriptor, String>()
    val unknownNotifications = mutableListOf<StatusBarNotification>()

    unreadNotificationsGrouped.keys.forEach { threadDescriptor ->
      descriptorToTagMap[threadDescriptor] = getUniqueNotificationTag(threadDescriptor)
    }

    for (visibleNotification in visibleNotifications) {
      val notificationTag = visibleNotification.tag
      var found = false

      for ((threadDescriptor, tag) in descriptorToTagMap.entries) {
        if (tag == notificationTag) {
          found = true
          AppConstants.Notifications.Reply.notificationIdMap[threadDescriptor] = visibleNotification.id
          break
        }
      }

      if (found) {
        continue
      }

      if (notificationTag == AppConstants.Notifications.Reply.SUMMARY_NOTIFICATION_TAG) {
        continue
      }

      unknownNotifications += visibleNotifications
    }

    if (unknownNotifications.isNotEmpty()) {
      unknownNotifications.forEach { notification ->
        notificationManagerCompat.cancel(notification.tag, notification.id)
      }
    }
  }

  private fun closeAllNotifications() {
    if (!androidHelpers.isAndroidO()) {
      notificationManagerCompat.cancel(
        AppConstants.Notifications.Reply.REPLIES_PRE_OREO_NOTIFICATION_TAG,
        AppConstants.Notifications.REPLIES_PRE_OREO_NOTIFICATION_ID
      )

      logcat(TAG) { "closeAllNotifications() closed REPLIES_PRE_OREO_NOTIFICATION_ID notification" }
      return
    }

    val visibleNotifications = notificationManager.activeNotifications
      .filter { statusBarNotification -> statusBarNotification.notification.group == notificationsGroup }

    if (visibleNotifications.isEmpty()) {
      logcat(TAG) { "closeAllNotifications() visibleNotifications are empty" }
      return
    }

    visibleNotifications.forEach { notification ->
      notificationManagerCompat.cancel(notification.tag, notification.id)
    }

    logcat(TAG) { "closeAllNotifications() closed ${visibleNotifications.size} notifications" }
  }

  companion object {
    private const val TAG = "ReplyNotificationsHelper"

    private const val NOTIFICATIONS_UPDATE_DEBOUNCE_TIME = 1000L
    private const val MAX_THUMBNAIL_REQUESTS_PER_BATCH = 8
    private const val MAX_NOTIFICATION_LINE_LENGTH = 128

    // For Android O and above
    private val notificationsGroup by lazy { "${TAG}_${BuildConfig.APPLICATION_ID}" }

    private val REPLIES_COMPARATOR = Comparator<ThreadBookmarkReply> { o1, o2 ->
      o1.postDescriptor.compareTo(o2.postDescriptor)
    }

    private val CIRCLE_CROP = listOf<Transformation>(CircleCropTransformation())

    private val QUOTE_REGEX = Pattern.compile(">>(\\d+)")
    private val LINK_EXTRACTOR = LinkExtractor.builder()
      .linkTypes(EnumSet.of(LinkType.URL))
      .build()
  }
}
