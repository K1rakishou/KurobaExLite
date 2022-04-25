package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrShort
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
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
  private val modifyMarkedPosts: ModifyMarkedPosts,
  private val appResources: AppResources
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
        val message = appResources.string(R.string.reply_view_model_site_not_found, chanDescriptor.siteKey.key)
        replyLayoutState.replyError(message)
        return@launch
      }

      val replyInfo = site.replyInfo()
      if (replyInfo == null) {
        val message = appResources.string(R.string.reply_view_model_site_does_not_support_posting, chanDescriptor.siteKey.key)
        replyLayoutState.replyError(message)
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

          val message = appResources.string(R.string.reply_view_model_reply_send_canceled_by_user)
          showToast(chanDescriptor, message)
          replyLayoutState.onReplySendCanceled()
        } else {
          logcatError(TAG) { "sendReply($screenKey) error: ${error.asLogIfImportantOrShort()}" }

          val message = appResources.string(R.string.reply_view_model_reply_send_error, error.errorMessageOrClassName())
          showErrorToast(chanDescriptor, message)
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
                replyLayoutState.replyError(appResources.string(R.string.reply_view_model_you_forgot_captcha_error))
              }
              response.mistypedCaptcha == true -> {
                replyLayoutState.replyError(appResources.string(R.string.reply_view_model_you_mistyped_captcha_error))
              }
              else -> {
                replyLayoutState.replyError(appResources.string(R.string.reply_view_model_generic_authentication_error))
              }
            }
          }
          is ReplyResponse.Banned -> {
            val message = appResources.string(R.string.reply_view_model_you_are_banned_error, response.banMessage)
            replyLayoutState.replyError(message)
          }
          is ReplyResponse.Error -> {
            val message = appResources.string(R.string.reply_view_model_unknown_posting_error, response.errorMessage)
            replyLayoutState.replyError(message)
          }
          is ReplyResponse.RateLimited -> {
            val message = appResources.string(
              R.string.reply_view_model_posting_rate_limit_error,
              (response.timeToWaitMs / 1000L).toString()
            )

            replyLayoutState.replyError(message)
          }
          is ReplyResponse.Success -> {
            val postDescriptor = response.postDescriptor

            if (chanDescriptor is CatalogDescriptor) {
              // TODO(KurobaEx): switch to thread screen and load this thread
            }

            modifyMarkedPosts.markPostAsMine(postDescriptor)

            showToast(chanDescriptor, appResources.string(R.string.reply_view_model_reply_sent_successfully))
            replyLayoutState.onReplySendEndedSuccessfully()
            replyLayoutState.onReplyProgressChanged(null)

            logcat(TAG) { "sendReply($screenKey) success postDescriptor: ${postDescriptor}" }
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

