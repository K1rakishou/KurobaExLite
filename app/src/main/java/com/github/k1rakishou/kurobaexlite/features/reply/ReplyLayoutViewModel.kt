package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrShort
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.ModifyMarkedPosts
import com.github.k1rakishou.kurobaexlite.managers.CaptchaManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.ReplyEvent
import com.github.k1rakishou.kurobaexlite.sites.ReplyResponse
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.logcat

class ReplyLayoutViewModel(
  private val captchaManager: CaptchaManager,
  private val siteManager: SiteManager,
  private val snackbarManager: SnackbarManager,
  private val modifyMarkedPosts: ModifyMarkedPosts
) : BaseViewModel() {
  private val sendReplyJobMap = mutableMapOf<ScreenKey, Job>()
  private val manuallyCanceled = mutableSetOf<ScreenKey>()

  fun showErrorToast(chanDescriptor: ChanDescriptor, errorMessage: String) {
    val screenKey = when (chanDescriptor) {
      is CatalogDescriptor -> CatalogScreen.SCREEN_KEY
      is ThreadDescriptor -> ThreadScreen.SCREEN_KEY
    }

    snackbarManager.errorToast(
      message = errorMessage,
      screenKey = screenKey
    )
  }

  fun showToast(chanDescriptor: ChanDescriptor, message: String) {
    val screenKey = when (chanDescriptor) {
      is CatalogDescriptor -> CatalogScreen.SCREEN_KEY
      is ThreadDescriptor -> ThreadScreen.SCREEN_KEY
    }

    snackbarManager.toast(
      message = message,
      screenKey = screenKey
    )
  }

  fun sendReply(chanDescriptor: ChanDescriptor, replyLayoutState: ReplyLayoutState) {
    val screenKey = replyLayoutState.screenKey

    sendReplyJobMap.remove(screenKey)?.cancel()
    manuallyCanceled.remove(screenKey)

    sendReplyJobMap[screenKey] = viewModelScope.launch {
      val site = siteManager.bySiteKey(chanDescriptor.siteKey)
      if (site == null) {
        replyLayoutState.replyError("Site for key \'${chanDescriptor.siteKey.key}\' not found")
        return@launch
      }

      val replyInfo = site.replyInfo()
      if (replyInfo == null) {
        replyLayoutState.replyError("Site \'${chanDescriptor.siteKey.key}\' does not support posting")
        return@launch
      }

      replyLayoutState.onReplySendStarted()
      logcat(TAG) { "sendReply($screenKey) started" }

      try {
        logcat(TAG) { "sendReply($screenKey) getCaptchaSolutionOrRequestNewOne() start" }

        val captcha = try {
          captchaManager.getOrRequestCaptcha(chanDescriptor)
        } catch (error: Throwable) {
          logcat(TAG) { "sendReply($screenKey) getCaptchaSolutionOrRequestNewOne() error" }
          throw error
        }

        val replyData = replyLayoutState.getReplyData(chanDescriptor, captcha.solution)
        if (!replyData.isValid()) {
          replyLayoutState.replyError("Empty reply (no message and no images)")
          return@launch
        }

        logcat(TAG) { "sendReply($screenKey) getCaptchaSolutionOrRequestNewOne() end, captcha=${captcha}" }

        replyInfo.sendReply(replyData).collect { replyEvent ->
          processReplyEvents(
            replyEvent = replyEvent,
            replyLayoutState = replyLayoutState,
            screenKey = screenKey,
            chanDescriptor = chanDescriptor
          )
        }
      } catch (error: Throwable) {
        replyLayoutState.onReplyProgressChanged(null)

        if (manuallyCanceled.contains(screenKey) || error is CancellationException) {
          logcatError(TAG) { "sendReply($screenKey) canceled" }

          showToast(chanDescriptor, "Reply send canceled by user")
          replyLayoutState.onReplySendCanceled()
        } else {
          logcatError(TAG) { "sendReply($screenKey) error: ${error.asLogIfImportantOrShort()}" }

          showErrorToast(chanDescriptor, "Reply send error: \'${error.errorMessageOrClassName()}\'")
          replyLayoutState.onReplySendEndedWithError(error)
        }
      } finally {
        sendReplyJobMap.remove(screenKey)
        manuallyCanceled.remove(screenKey)
      }
    }
  }

  private suspend fun processReplyEvents(
    replyEvent: ReplyEvent,
    replyLayoutState: ReplyLayoutState,
    screenKey: ScreenKey,
    chanDescriptor: ChanDescriptor
  ) {
    when (replyEvent) {
      ReplyEvent.Start -> {
        replyLayoutState.onReplyProgressChanged(0f)
      }
      is ReplyEvent.Progress -> {
        replyLayoutState.onReplyProgressChanged(replyEvent.progress)
      }
      is ReplyEvent.Error -> {
        throw replyEvent.error
      }
      is ReplyEvent.Success -> {
        when (val response = replyEvent.replyResponse) {
          is ReplyResponse.AuthenticationRequired -> {
            when {
              response.forgotCaptcha == true -> {
                replyLayoutState.replyError("You forgot to solve the CAPTCHA")
              }
              response.mistypedCaptcha == true -> {
                replyLayoutState.replyError("You seem to have mistyped the CAPTCHA")
              }
              else -> {
                replyLayoutState.replyError("Authentication required")
              }
            }
          }
          is ReplyResponse.Banned -> {
            replyLayoutState.replyError("You are banned. Ban message: \'${response.banMessage}\'")
          }
          is ReplyResponse.Error -> {
            replyLayoutState.replyError("Posting error: ${response.errorMessage}")
          }
          is ReplyResponse.RateLimited -> {
            replyLayoutState.replyError(
              "You have to wait \'${response.timeToWaitMs / 1000L}\' seconds before you can post again"
            )
          }
          is ReplyResponse.Success -> {
            val postDescriptor = response.postDescriptor
            logcat(TAG) { "sendReply($screenKey) success postDescriptor: ${postDescriptor}" }

            if (chanDescriptor is CatalogDescriptor) {
              // TODO(KurobaEx): switch to thread screen and load this thread
            }

            modifyMarkedPosts.markPostAsMine(postDescriptor)

            showToast(chanDescriptor, "Reply sent successfully")
            replyLayoutState.onReplySendEndedSuccessfully()
            replyLayoutState.onReplyProgressChanged(null)
          }
        }
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

