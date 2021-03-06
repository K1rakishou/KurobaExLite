package com.github.k1rakishou.kurobaexlite.features.reply

import android.os.Bundle
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.picker.LocalFilePicker
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.AddOrRemoveBookmark
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.ModifyMarkedPosts
import com.github.k1rakishou.kurobaexlite.managers.CaptchaManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.ReplyEvent
import com.github.k1rakishou.kurobaexlite.sites.ReplyResponse
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

class ReplyLayoutViewModel(
  private val captchaManager: CaptchaManager,
  private val siteManager: SiteManager,
  private val snackbarManager: SnackbarManager,
  private val modifyMarkedPosts: ModifyMarkedPosts,
  private val addOrRemoveBookmark: AddOrRemoveBookmark,
  private val localFilePicker: LocalFilePicker,
  private val appResources: AppResources,
  private val savedStateHandle: SavedStateHandle
) : BaseViewModel() {
  private val sendReplyJobMap = mutableMapOf<ScreenKey, Job>()
  private val manuallyCanceled = mutableSetOf<ScreenKey>()
  private val replyLayoutStateMap = mutableMapOf<ChanDescriptor, ReplyLayoutState>()

  private val _pickFileResultFlow = MutableSharedFlow<PickFileResult>(extraBufferCapacity = Channel.UNLIMITED)
  val pickFileResultFlow: SharedFlow<PickFileResult>
    get() = _pickFileResultFlow.asSharedFlow()

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    processReplyLayoutStates()
  }

  private suspend fun processReplyLayoutStates() {
    val replyLayoutStateKeys = savedStateHandle.keys()
      .filter { key -> key.startsWith("reply_layout_state_") }

    if (replyLayoutStateKeys.isEmpty()) {
      logcat(TAG) { "processReplyLayoutStates() replyLayoutStateKeys is empty, doing cleanup()" }
      localFilePicker.cleanup()
      return
    }

    logcat(TAG) { "processReplyLayoutStates() found ${replyLayoutStateKeys.size} keys in savedStateHandle" }

    val restoredReplyLayoutStates = replyLayoutStateKeys
      .mapNotNull { replyLayoutStateKey -> savedStateHandle.get<Bundle>(replyLayoutStateKey) }
      .mapNotNull { bundle ->
        val chanDescriptor = bundle.getParcelable<Parcelable>(ReplyLayoutState.chanDescriptorKey) as? ChanDescriptor
            ?: return@mapNotNull null

        val screenKey = when (chanDescriptor) {
          is CatalogDescriptor -> CatalogScreen.SCREEN_KEY
          is ThreadDescriptor -> ThreadScreen.SCREEN_KEY
        }

        return@mapNotNull ReplyLayoutState(
          screenKey = screenKey,
          chanDescriptor = chanDescriptor,
          bundle = bundle
        )
      }

    restoredReplyLayoutStates.forEach { replyLayoutState ->
      replyLayoutStateMap[replyLayoutState.chanDescriptor] = replyLayoutState
    }

    logcat(TAG) { "processReplyLayoutStates() restored ${restoredReplyLayoutStates.size} replyLayoutStates" }
  }

  fun getOrCreateReplyLayoutState(chanDescriptor: ChanDescriptor?): IReplyLayoutState {
    if (chanDescriptor == null) {
      return FakeReplyLayoutState()
    }

    val savedStateHandleKey = "reply_layout_state_${chanDescriptor.asKey()}"

    val replyLayoutState = replyLayoutStateMap.getOrPut(
      key = chanDescriptor,
      defaultValue = {
        return@getOrPut when (chanDescriptor) {
          is CatalogDescriptor -> {
            ReplyLayoutState(
              screenKey = CatalogScreen.SCREEN_KEY,
              chanDescriptor = chanDescriptor,
              bundle = savedStateHandle.get<Bundle>(savedStateHandleKey) ?: Bundle()
            )
          }
          is ThreadDescriptor -> {
            ReplyLayoutState(
              screenKey = ThreadScreen.SCREEN_KEY,
              chanDescriptor = chanDescriptor,
              bundle = savedStateHandle.get<Bundle>(savedStateHandleKey) ?: Bundle()
            )
          }
        }
      }
    )
    savedStateHandle.set(savedStateHandleKey, replyLayoutState.bundle)

    return replyLayoutState
  }

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
        val message = appResources.string(
          R.string.reply_view_model_site_not_found,
          chanDescriptor.siteKey.key
        )

        replyLayoutState.replyShowErrorToast(message)
        return@launch
      }

      val replyInfo = site.replyInfo()
      if (replyInfo == null) {
        val message = appResources.string(
          R.string.reply_view_model_site_does_not_support_posting,
          chanDescriptor.siteKey.key
        )

        replyLayoutState.replyShowErrorToast(message)
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
          val message = appResources.string(R.string.reply_view_model_empty_reply_error)
          replyLayoutState.replyShowErrorToast(message)
          return@launch
        }

        logcat(TAG) { "sendReply($screenKey) getCaptchaSolutionOrRequestNewOne() end, captcha=${captcha}" }

        replyInfo.sendReply(replyData)
          .catch { error -> emit(ReplyEvent.Error(error)) }
          .collect { replyEvent ->
            processReplyEvents(
              replyEvent = replyEvent,
              replyLayoutState = replyLayoutState,
              screenKey = screenKey,
              chanDescriptor = chanDescriptor
            )
          }
      } catch (error: Throwable) {
        if (manuallyCanceled.contains(screenKey) || error is CancellationException) {
          logcatError(TAG) { "sendReply($screenKey) canceled" }

          val message = appResources.string(R.string.reply_view_model_reply_send_canceled_by_user)
          replyLayoutState.replyShowErrorToast(message)
          replyLayoutState.onReplySendCanceled()
        } else {
          logcatError(TAG) { "sendReply($screenKey) error: ${error.asLogIfImportantOrErrorMessage()}" }

          val message = appResources.string(
            R.string.reply_view_model_reply_send_error,
            error.errorMessageOrClassName()
          )

          replyLayoutState.replyShowErrorToast(message)
          replyLayoutState.onReplySendEndedWithError()
        }
      } finally {
        replyLayoutState.onReplyProgressChanged(null)

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

  fun onPickFileRequested(chanDescriptor: ChanDescriptor) {
    viewModelScope.launch {
      val pickResult = localFilePicker.pickFile(
        chanDescriptor = chanDescriptor,
        allowMultiSelection = false
      )

      val pickFileResult = PickFileResult(
        chanDescriptor = chanDescriptor,
        pickResult = pickResult
      )

      _pickFileResultFlow.emit(pickFileResult)
    }
  }

  fun quotePost(threadDescriptor: ThreadDescriptor, postCellData: PostCellData) {
    val replyLayoutState = getOrCreateReplyLayoutState(threadDescriptor)
    if (replyLayoutState !is ReplyLayoutState) {
      return
    }

    replyLayoutState.appendPostQuote(postCellData.postDescriptor)
  }

  fun quotePostWithComment(threadDescriptor: ThreadDescriptor, postCellData: PostCellData) {
    val parsedPostComment = postCellData.parsedPostData?.parsedPostComment
    if (parsedPostComment.isNullOrEmpty()) {
      quotePost(threadDescriptor, postCellData)
      return
    }

    val replyLayoutState = getOrCreateReplyLayoutState(threadDescriptor)
    if (replyLayoutState !is ReplyLayoutState) {
      return
    }

    replyLayoutState.appendPostQuoteWithComment(
      postDescriptor = postCellData.postDescriptor,
      comment = parsedPostComment
    )
  }

  private suspend fun processReplyEvents(
    replyEvent: ReplyEvent,
    replyLayoutState: ReplyLayoutState,
    screenKey: ScreenKey,
    chanDescriptor: ChanDescriptor
  ) {
    if (replyEvent is ReplyEvent.Start) {
      replyLayoutState.onReplyProgressChanged(0f)
      return
    }

    if (replyEvent is ReplyEvent.Progress) {
      replyLayoutState.onReplyProgressChanged(replyEvent.progress)
      return
    }

    if (replyEvent is ReplyEvent.Error) {
      replyLayoutState.onReplySendEnded()
      replyLayoutState.onReplyProgressChanged(null)
      throw replyEvent.error
    }

    withContext(NonCancellable) {
      replyLayoutState.onReplySendEnded()
      replyLayoutState.onReplyProgressChanged(null)

      when (val replyResponse = (replyEvent as ReplyEvent.Success).replyResponse) {
        is ReplyResponse.AuthenticationRequired -> {
          when {
            replyResponse.forgotCaptcha -> {
              replyLayoutState.replyShowErrorToast(appResources.string(R.string.reply_view_model_you_forgot_captcha_error))
            }
            replyResponse.mistypedCaptcha -> {
              replyLayoutState.replyShowErrorToast(appResources.string(R.string.reply_view_model_you_mistyped_captcha_error))
            }
            else -> {
              replyLayoutState.replyShowErrorToast(appResources.string(R.string.reply_view_model_generic_authentication_error))
            }
          }

          replyLayoutState.onReplySendEndedWithError()
        }
        is ReplyResponse.Banned -> {
          val message = appResources.string(R.string.reply_view_model_you_are_banned_error, replyResponse.banMessage)
          replyLayoutState.replyShowErrorToast(message)
          replyLayoutState.onReplySendEndedWithError()
        }
        is ReplyResponse.Error -> {
          val message = appResources.string(R.string.reply_view_model_unknown_posting_error, replyResponse.errorMessage)
          replyLayoutState.replyShowErrorToast(message)
          replyLayoutState.onReplySendEndedWithError()
        }
        is ReplyResponse.RateLimited -> {
          val message = appResources.string(
            R.string.reply_view_model_posting_rate_limit_error,
            (replyResponse.timeToWaitMs / 1000L).toString()
          )

          replyLayoutState.replyShowErrorToast(message)
          replyLayoutState.onReplySendEndedWithError()
        }
        is ReplyResponse.Success -> {
          val postDescriptor = replyResponse.postDescriptor
          modifyMarkedPosts.markPostAsMine(postDescriptor)

          addOrRemoveBookmark.addBookmarkIfNotExists(
            threadDescriptor = postDescriptor.threadDescriptor,
            bookmarkTitle = null,
            bookmarkThumbnail = null
          )

          showToast(chanDescriptor, appResources.string(R.string.reply_view_model_reply_sent_successfully))
          replyLayoutState.onReplySendEndedSuccessfully(postDescriptor)
          logcat(TAG) { "sendReply($screenKey) success postDescriptor: ${postDescriptor}" }
        }
      }
    }
  }

  data class PickFileResult(
    val chanDescriptor: ChanDescriptor,
    val pickResult: Result<List<AttachedMedia>>
  )

  companion object {
    private const val TAG = "ReplyLayoutViewModel"
  }

}

