package com.github.k1rakishou.kurobaexlite.features.reply

import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.helpers.kpnc.KPNCHelper
import com.github.k1rakishou.kurobaexlite.helpers.picker.LocalFilePicker
import com.github.k1rakishou.kurobaexlite.helpers.picker.RemoteFilePicker
import com.github.k1rakishou.kurobaexlite.helpers.resource.IAppResources
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.AddOrRemoveBookmark
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.ModifyMarkedPosts
import com.github.k1rakishou.kurobaexlite.managers.Captcha
import com.github.k1rakishou.kurobaexlite.managers.CaptchaManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.data.local.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.data.local.ReplyData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.ReplyEvent
import com.github.k1rakishou.kurobaexlite.sites.ReplyResponse
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.progress.ProgressScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import java.lang.ref.WeakReference

class ReplyLayoutViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val captchaManager: CaptchaManager,
  private val siteManager: SiteManager,
  private val snackbarManager: SnackbarManager,
  private val kpncHelper: KPNCHelper,
  private val remoteFilePicker: RemoteFilePicker,
  private val modifyMarkedPosts: ModifyMarkedPosts,
  private val addOrRemoveBookmark: AddOrRemoveBookmark,
  private val loadChanCatalog: LoadChanCatalog,
  private val localFilePicker: LocalFilePicker,
  private val appResources: IAppResources
) : BaseViewModel() {
  private val sendReplyJobMap = mutableMapOf<ScreenKey, Job>()
  private val manuallyCanceled = mutableSetOf<ScreenKey>()
  private val replyLayoutStateMap = mutableMapOf<ChanDescriptor, ReplyLayoutState>()

  private val _pickFileResultFlow = MutableSharedFlow<PickFileResult>(extraBufferCapacity = Channel.UNLIMITED)
  val pickFileResultFlow: SharedFlow<PickFileResult>
    get() = _pickFileResultFlow.asSharedFlow()

  private val _lastUsedBoardFlagMap = mutableStateMapOf<CatalogDescriptor, BoardFlag>()
  val lastUsedBoardFlagMap: Map<CatalogDescriptor, BoardFlag>
    get() = _lastUsedBoardFlagMap

  private val _errorDialogMessageFlow = MutableSharedFlow<ErrorDialogMessage>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )
  val errorDialogMessageFlow: SharedFlow<ErrorDialogMessage>
    get() = _errorDialogMessageFlow.asSharedFlow()

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    processReplyLayoutStates()
  }

  override fun onCleared() {
    super.onCleared()

    replyLayoutStateMap.values.forEach { replyLayoutState -> replyLayoutState.onDestroy() }
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

  fun showErrorDialog(title: String, errorMessage: String) {
    _errorDialogMessageFlow.tryEmit(ErrorDialogMessage(title, errorMessage))
  }

  fun showToast(chanDescriptor: ChanDescriptor, message: String, toastId: String? = null) {
    showToast(chanDescriptor, ReplyLayoutState.ToastMessage(message, toastId), false)
  }

  fun showErrorToast(chanDescriptor: ChanDescriptor, message: String, toastId: String? = null) {
    showToast(chanDescriptor, ReplyLayoutState.ToastMessage(message, toastId), true)
  }

  fun showToast(chanDescriptor: ChanDescriptor, toastMessage: ReplyLayoutState.ToastMessage, errorToast: Boolean = false) {
    val screenKey = when (chanDescriptor) {
      is CatalogDescriptor -> CatalogScreen.SCREEN_KEY
      is ThreadDescriptor -> ThreadScreen.SCREEN_KEY
    }

    if (errorToast) {
      snackbarManager.errorToast(
        message = toastMessage.message,
        screenKey = screenKey,
        toastId = toastMessage.toastId ?: SnackbarManager.nextToastId()
      )
    } else {
      snackbarManager.toast(
        message = toastMessage.message,
        screenKey = screenKey,
        toastId = toastMessage.toastId ?: SnackbarManager.nextToastId()
      )
    }
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
        logcat(TAG) { "sendReply($screenKey) getOrRequestCaptcha() start" }

        val captcha = try {
          if (site.siteSettings.isLoggedIn()) {
            logcat(TAG) { "sendReply($screenKey) getOrRequestCaptcha() using passcode" }
            Captcha.newPasscodeCaptcha()
          } else {
            logcat(TAG) { "sendReply($screenKey) getOrRequestCaptcha() requesting captcha" }
            captchaManager.getOrRequestCaptcha(chanDescriptor)
          }
        } catch (error: Throwable) {
          logcat(TAG) { "sendReply($screenKey) getOrRequestCaptcha() error" }
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
              replyData = replyData,
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
          replyLayoutState.onReplySendFinishedUnsuccesfully()
        } else {
          logcatError(TAG) { "sendReply($screenKey) error: ${error.asLogIfImportantOrErrorMessage()}" }

          val message = appResources.string(
            R.string.reply_view_model_reply_send_error,
            error.errorMessageOrClassName()
          )

          replyLayoutState.replyShowErrorToast(message)
          replyLayoutState.onReplySendFinishedUnsuccesfully()
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

  fun pickLocalFile(chanDescriptor: ChanDescriptor) {
    viewModelScope.launch {
      logcat(TAG) { "pickLocalFile($chanDescriptor) start" }

      val maxAttachFilesPerPost = loadChanCatalog.await(chanDescriptor)
        .getOrElse { error ->
          logcatError(TAG) { "loadChanCatalog($chanDescriptor) error: ${error.asLogIfImportantOrErrorMessage()}" }
          return@getOrElse null
        }
        ?.maxAttachFilesPerPost
        ?: 1

      val pickResult = localFilePicker.pickFile(
        chanDescriptor = chanDescriptor,
        allowMultiSelection = maxAttachFilesPerPost > 1
      )

      logcat(TAG) { "pickLocalFile($chanDescriptor) finish, success: ${pickResult.isSuccess}" }

      if (!pickResult.isSuccess) {
        val errorMessage = pickResult.exceptionOrThrow().errorMessageOrClassName(userReadable = true)

        snackbarManager.errorToast(
          message = appResources.string(R.string.posts_screen_failed_to_pick_local_file, errorMessage),
          screenKey = snackbarManager.screenKeyFromDescriptor(chanDescriptor)
        )

        return@launch
      }

      val newPickedMedias = pickResult.getOrNull()
        ?: return@launch

      val pickFileResult = PickFileResult(
        chanDescriptor = chanDescriptor,
        newPickedMedias = newPickedMedias
      )

      _pickFileResultFlow.emit(pickFileResult)
    }
  }

  fun pickRemoteFile(
    componentActivityInput: ComponentActivity,
    navigationRouterInput: NavigationRouter,
    chanDescriptor: ChanDescriptor,
    fileUrl: String
  ) {
    val componentActivityRef = WeakReference(componentActivityInput)
    val navigationRouterRef = WeakReference(navigationRouterInput)

    fun newProgressScreen(): ProgressScreen? {
      val componentActivity = componentActivityRef.get() ?: return null
      val navigationRouter = navigationRouterRef.get() ?: return null

      return ComposeScreen.createScreen<ProgressScreen>(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        args = {
          putString(
            ProgressScreen.TITLE,
            appResources.string(R.string.downloading_file)
          )
        }
      )
    }

    viewModelScope.launch {
      logcat(TAG) { "pickRemoteFile($chanDescriptor, $fileUrl) start" }

      val pickResult = remoteFilePicker.pickFile(
        chanDescriptor = chanDescriptor,
        imageUrls = listOf(fileUrl),
        showLoadingView = {
          withContext(Dispatchers.Main) {
            val navigationRouter = navigationRouterRef.get()
              ?: return@withContext
            val progressScreen = newProgressScreen()
              ?: return@withContext

            if (navigationRouter.getScreenByKey(progressScreen.screenKey) != null) {
              return@withContext
            }

            navigationRouter.presentScreen(progressScreen)
          }
        },
        hideLoadingView = {
          withContext(Dispatchers.Main) {
            val navigationRouter = navigationRouterRef.get()
              ?: return@withContext

            navigationRouter.stopPresentingScreen(ProgressScreen.SCREEN_KEY)
          }
        }
      )

      logcat(TAG) { "pickRemoteFile($chanDescriptor, $fileUrl) finish, success: ${pickResult.isSuccess}" }

      if (!pickResult.isSuccess) {
        val errorMessage = pickResult.exceptionOrThrow().errorMessageOrClassName()

        snackbarManager.errorToast(
          message = appResources.string(R.string.posts_screen_failed_to_pick_remote_file, errorMessage),
          screenKey = snackbarManager.screenKeyFromDescriptor(chanDescriptor)
        )

        return@launch
      }

      val newPickedMedia = pickResult.getOrNull()
        ?: return@launch

      val pickFileResult = PickFileResult(
        chanDescriptor = chanDescriptor,
        newPickedMedias = listOf(newPickedMedia)
      )

      _pickFileResultFlow.emit(pickFileResult)
    }
  }

  fun quotePost(chanDescriptor: ChanDescriptor, postCellData: PostCellData) {
    val replyLayoutState = getOrCreateReplyLayoutState(chanDescriptor)
    if (replyLayoutState !is ReplyLayoutState) {
      return
    }

    replyLayoutState.appendPostQuote(postCellData.postDescriptor)
  }

  fun quotePostWithText(chanDescriptor: ChanDescriptor, postCellData: PostCellData, selectedText: String) {
    val replyLayoutState = getOrCreateReplyLayoutState(chanDescriptor)
    if (replyLayoutState !is ReplyLayoutState) {
      return
    }

    replyLayoutState.appendPostQuoteWithComment(
      postDescriptor = postCellData.postDescriptor,
      comment = selectedText
    )
  }

  suspend fun loadLastUsedFlag(chanDescriptor: ChanDescriptor) {
    val chanCatalog = loadChanCatalog.await(chanDescriptor).getOrNull()
      ?: return

    val flags = chanCatalog.flags
    if (flags.isEmpty()) {
      return
    }

    val loaded = loadLastUsedBoardFlags(
      catalogDescriptor = chanDescriptor.catalogDescriptor(),
      flags = chanCatalog.flags
    )

    val lastUsedFlag = if (loaded) {
      lastUsedBoardFlagMap[chanDescriptor.catalogDescriptor()]
    } else {
      flags.first()
    }

    replyLayoutStateMap[chanDescriptor]?.onFlagChanged(lastUsedFlag)
  }

  suspend fun storeLastUsedFlag(chanDescriptor: ChanDescriptor, boardFlag: BoardFlag) {
    if (lastUsedBoardFlagMap[chanDescriptor.catalogDescriptor()] == boardFlag) {
      return
    }

    val lastUsedBoardFlags = siteManager.bySiteKey(chanDescriptor.siteKey)
      ?.siteSettings
      ?.lastUsedBoardFlags
      ?: return

    _lastUsedBoardFlagMap[chanDescriptor.catalogDescriptor()] = boardFlag

    val resultString = _lastUsedBoardFlagMap.entries.joinToString(
      separator = ";",
      transform = { (descriptor, boardFlag) -> "${descriptor.siteKeyActual}=${descriptor.boardCode}=${boardFlag.key}" }
    )

    lastUsedBoardFlags.write(resultString)
    replyLayoutStateMap[chanDescriptor]?.onFlagChanged(boardFlag)
  }

  private suspend fun loadLastUsedBoardFlags(catalogDescriptor: CatalogDescriptor, flags: List<BoardFlag>): Boolean {
    if (lastUsedBoardFlagMap.containsKey(catalogDescriptor)) {
      return true
    }

    val lastUsedBoardFlags = siteManager.bySiteKey(catalogDescriptor.siteKey)
      ?.siteSettings
      ?.lastUsedBoardFlags
      ?.read()

    if (lastUsedBoardFlags.isNullOrEmpty()) {
      return false
    }

    for (lastUsedBoardFlag in lastUsedBoardFlags.split(';')) {
      val split = lastUsedBoardFlag.split('=')

      if (split.size != 3) {
        continue
      }

      val siteKey = split[0]
      val boardCode = split[1]
      val flagKey = split[2]

      val descriptor = CatalogDescriptor(SiteKey(siteKey), boardCode)
      if (descriptor != catalogDescriptor) {
        continue
      }

      val boardFlag = flags
        .firstOrNull { boardFlag -> boardFlag.key == flagKey }
        ?: flags.first()

      _lastUsedBoardFlagMap[descriptor] = boardFlag
      return true
    }

    return false
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

  private suspend fun processReplyEvents(
    replyData: ReplyData,
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

          replyLayoutState.onReplySendFinishedUnsuccesfully()
        }
        is ReplyResponse.NotAllowedToPost -> {
          val title = appResources.string(R.string.reply_view_model_cannot_post_title)
          val message = appResources.string(R.string.reply_view_model_cannot_post_message, replyResponse.errorMessage)

          replyLayoutState.replyShowErrorDialog(title, message)
          replyLayoutState.onReplySendFinishedUnsuccesfully()
        }
        is ReplyResponse.Error -> {
          val message = appResources.string(R.string.reply_view_model_unknown_posting_error, replyResponse.errorMessage)
          replyLayoutState.replyShowErrorToast(message)
          replyLayoutState.onReplySendFinishedUnsuccesfully()
        }
        is ReplyResponse.RateLimited -> {
          val message = appResources.string(
            R.string.reply_view_model_posting_rate_limit_error,
            (replyResponse.timeToWaitMs / 1000L).toString()
          )

          replyLayoutState.replyShowErrorToast(message)
          replyLayoutState.onReplySendFinishedUnsuccesfully()
        }
        is ReplyResponse.Success -> {
          val postDescriptor = replyResponse.postDescriptor
          modifyMarkedPosts.markPostAsMine(postDescriptor)

          addOrRemoveBookmark.addBookmarkIfNotExists(
            threadDescriptor = postDescriptor.threadDescriptor,
            bookmarkTitle = null,
            bookmarkThumbnail = null
          )

          if (kpncHelper.isKpncEnabled()) {
            launch {
              val kpncAppInfoError = kpncHelper.kpncAppInfo().errorAsReadableString()
              if (kpncAppInfoError != null) {
                showErrorToast(chanDescriptor, kpncAppInfoError)
                return@launch
              }

              kpncHelper.startWatchingPost(postDescriptor)
                .onFailure { error ->
                  showErrorToast(chanDescriptor, error.errorMessageOrClassName(userReadable = true))
                }
            }
          }

          showToast(chanDescriptor, appResources.string(R.string.reply_view_model_reply_sent_successfully))
          replyLayoutState.onReplySendFinishedSuccessfully(postDescriptor)
          logcat(TAG) { "sendReply($screenKey) success postDescriptor: ${postDescriptor}" }
        }
      }
    }
  }

  data class ErrorDialogMessage(
    val title: String,
    val message: String
  )

  data class PickFileResult(
    val chanDescriptor: ChanDescriptor,
    val newPickedMedias: List<AttachedMedia>
  )

  companion object {
    private const val TAG = "ReplyLayoutViewModel"
  }

}

