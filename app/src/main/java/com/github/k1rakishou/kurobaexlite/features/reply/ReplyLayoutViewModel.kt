package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat

class ReplyLayoutViewModel : BaseViewModel() {
  private val sendReplyJobMap = mutableMapOf<ScreenKey, Job>()
  private val manuallyCanceled = mutableSetOf<ScreenKey>()

  fun sendReply(replyLayoutState: ReplyLayoutState) {
    val screenKey = replyLayoutState.screenKey

    sendReplyJobMap.remove(screenKey)?.cancel()
    manuallyCanceled.remove(screenKey)

    sendReplyJobMap[screenKey] = viewModelScope.launch {
      replyLayoutState.onReplySendStarted()
      logcat(TAG) { "sendReply($screenKey) started" }

      try {
        // TODO(KurobaEx):
        delay(3000L)
        replyLayoutState.onReplySendEndedSuccessfully()
        logcat(TAG) { "sendReply($screenKey) success" }
      } catch (error: Throwable) {
        if (manuallyCanceled.contains(screenKey) && error is CancellationException) {
          logcatError(TAG) { "sendReply($screenKey) canceled" }
          replyLayoutState.onReplySendCanceled()
        } else {
          logcatError(TAG) { "sendReply($screenKey) error: ${error.asLog()}" }
          replyLayoutState.onReplySendEndedWithError(error)
        }
      } finally {
        sendReplyJobMap.remove(screenKey)
        manuallyCanceled.remove(screenKey)
      }
    }
  }

  fun cancelSendReply(replyLayoutState: ReplyLayoutState) {
    val screenKey = replyLayoutState.screenKey

    manuallyCanceled += screenKey
    sendReplyJobMap.remove(screenKey)?.cancel()

    logcat(TAG) { "cancelSendReply($screenKey) canceled" }
  }

  companion object {
    private const val TAG = "ReplyLayoutViewModel"
  }

}

