package com.github.k1rakishou.kurobaexlite.helpers.kpnc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.FetchThreadBookmarkInfo
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.LoadBookmarks
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.RestartBookmarkBackgroundWatcher
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import logcat.logcat
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class KPNCBroadcastReceiver : BroadcastReceiver(), KoinComponent {
  private val fetchThreadBookmarkInfo: FetchThreadBookmarkInfo by inject()
  private val siteManager: SiteManager by inject()
  private val bookmarksManager: BookmarksManager by inject()
  private val loadBookmarks: LoadBookmarks by inject()
  private val restartBookmarkBackgroundWatcher: RestartBookmarkBackgroundWatcher by inject()

  private val mainScope = MainScope()

  init {
    logcat(TAG) { "Initialized" }
  }

  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent == null) {
      setResultExtras(bundleOf())
      return
    }

    val action = intent.action
    if (action.isNullOrEmpty()) {
      setResultExtras(bundleOf())
      return
    }

    if (action != ACTION_ON_NEW_REPLIES_RECEIVED) {
      setResultExtras(bundleOf())
      return
    }

    logcat(TAG) { "Got action: ${action}" }

    val postUrls = intent.getStringArrayExtra(POST_URLS_PARAM)?.toList() ?: emptyList()
    if (postUrls.isEmpty()) {
      logcat(TAG) { "postUrls is empty" }
      setResultExtras(bundleOf())
      return
    }

    val pendingResult = goAsync()

    mainScope.launch {
      try {
        logcat(TAG) { "Got ${postUrls.size} post urls from KPNC" }
        postUrls.forEach { postUrl -> logcat(TAG) { "postUrl: ${postUrl}" } }

        // TODO: This is not good because this won't wake up the phone from deep doze mode. The only way to wake up the
        //  phone from doze mode is to actually get the FCM. Right now this task is delegated to a different app so it
        //  won't work here. I need to merge that app into this one to fix it. This is bad because this app will have a
        //  Google dependency which many people won't like. For now leave it like this.
        restartBookmarkBackgroundWatcher.restartSuspend(
          addInitialDelay = false,
          postUrlsToCheck = postUrls
        )

        logcat(TAG) { "fetchThreadBookmarkInfo.await()... done" }
      } finally {
        pendingResult.setResultExtras(bundleOf())
        pendingResult.finish()
      }
    }
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
    private const val TAG = "KPNCBroadcastReceiver"
    private const val PACKAGE = "com.github.k1rakishou.kurobaexlite"

    private const val ACTION_ON_NEW_REPLIES_RECEIVED = "$PACKAGE.on_new_replies_received"
    private const val POST_URLS_PARAM = "post_urls"
  }

}