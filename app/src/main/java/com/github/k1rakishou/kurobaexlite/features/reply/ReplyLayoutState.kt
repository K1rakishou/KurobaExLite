package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import java.io.File
import logcat.logcat
import org.koin.core.context.GlobalContext

@Stable
class ReplyLayoutState(
  val screenKey: ScreenKey,
  private val savedStateHandle: SavedStateHandle
) {
  private val uiInfoManager: UiInfoManager by lazy { GlobalContext.get().get() }

  private val _replyLayoutVisibilityState = mutableStateOf(ReplyLayoutVisibility.Closed)
  val replyLayoutVisibilityState: State<ReplyLayoutVisibility>
    get() = _replyLayoutVisibilityState

  private val _replyText = mutableStateOf("")
  val replyText: State<String>
    get() = _replyText

  private val _attachedImages = mutableStateListOf<File>()
  val attachedImages: List<File>
    get() = _attachedImages

  private val _sendReplyState = mutableStateOf<SendReplyState>(SendReplyState.Finished(null))
  val sendReplyState: State<SendReplyState>
    get() = _sendReplyState

  private val _lastErrorMessageState = mutableStateOf<String?>(null)
  val lastErrorMessageState: State<String?>
    get() = _lastErrorMessageState

  init {
    restoreFromSavedState()
  }

  private fun restoreFromSavedState() {
    Snapshot.withMutableSnapshot {
      _replyText.value = savedStateHandle.get<String>(replyTextKey) ?: ""
      _lastErrorMessageState.value = savedStateHandle.get<String>(lastErrorMessageKey)
      _replyLayoutVisibilityState.value = savedStateHandle.get<Int>(replyLayoutVisibilityKey)
        .let { ReplyLayoutVisibility.fromRawValue(it) }

      savedStateHandle.get<List<String>>(attachedImagesKey)?.let { prevAttachedImagePathList ->
        val prevAttachedImages = prevAttachedImagePathList
          .map { attachedImagePath -> File(attachedImagePath) }
          .filter { attachedImageFile -> attachedImageFile.exists() }

        _attachedImages.clear()
        _attachedImages.addAll(prevAttachedImages)
      }

      onReplyLayoutVisibilityStateChanged()

      logcat(TAG) {
        "restoreFromSavedState() " +
          "replyText=\'${_replyText.value.take(32)}\', " +
          "lastErrorMessage=\'${lastErrorMessageState.value?.take(32)}\', " +
          "attachedImages=\'${_attachedImages.joinToString(transform = { it.path })}\'"
      }
    }
  }

  fun onReplySendStarted() {
    _sendReplyState.value = SendReplyState.Started
  }

  fun onReplySendEndedSuccessfully() {
    Snapshot.withMutableSnapshot {
      _attachedImages.clear()
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Closed
      _replyText.value = ""
      _lastErrorMessageState.value = null

      onReplySendStarted()
      onAttachedImagesUpdated()
      onReplyLayoutVisibilityStateChanged()
      onLastErrorMessageUpdated()

      _sendReplyState.value = SendReplyState.Finished(error = null)
    }
  }

  fun onReplySendCanceled() {
    Snapshot.withMutableSnapshot {
      onReplySendStarted()

      _sendReplyState.value = SendReplyState.Finished(error = null)
    }
  }

  fun onReplySendEndedWithError(error: Throwable) {
    Snapshot.withMutableSnapshot {
      _lastErrorMessageState.value = error.errorMessageOrClassName()

      onReplySendStarted()
      onAttachedImagesUpdated()
      onReplyLayoutVisibilityStateChanged()
      onLastErrorMessageUpdated()

      _sendReplyState.value = SendReplyState.Finished(error = error)
    }
  }

  fun attachImage(image: File) {
    _attachedImages += image
    onAttachedImagesUpdated()
  }

  fun detachImage(image: File) {
    _attachedImages -= image
    onAttachedImagesUpdated()
  }

  fun onReplyTextChanged(newTextFieldValue: String) {
    _replyText.value = newTextFieldValue
    savedStateHandle.set(replyTextKey, newTextFieldValue)
  }

  fun openReplyLayout() {
    _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Opened
    onReplyLayoutVisibilityStateChanged()
  }

  fun expandReplyLayout() {
    _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Expanded
    onReplyLayoutVisibilityStateChanged()
  }

  fun collapseReplyLayout() {
    _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Opened
    onReplyLayoutVisibilityStateChanged()
  }

  fun onBackPressed(): Boolean {
    val currentState = replyLayoutVisibilityState.value
    if (currentState == ReplyLayoutVisibility.Closed) {
      return false
    }

    if (currentState == ReplyLayoutVisibility.Expanded) {
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Opened
      onReplyLayoutVisibilityStateChanged()
      return true
    }

    _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Closed
    onReplyLayoutVisibilityStateChanged()
    return true
  }

  private fun onAttachedImagesUpdated() {
    val attachedImagePathList = _attachedImages
      .map { attachedImageFile -> attachedImageFile.absolutePath }

    savedStateHandle.set(attachedImagesKey, attachedImagePathList)
  }

  private fun onReplyLayoutVisibilityStateChanged() {
    val replyLayoutVisibilityStateValue = _replyLayoutVisibilityState.value
    uiInfoManager.replyLayoutVisibilityStateChanged(screenKey, replyLayoutVisibilityStateValue)
    savedStateHandle.set<Int>(replyLayoutVisibilityKey, replyLayoutVisibilityStateValue.value)
  }

  private fun onLastErrorMessageUpdated() {
    savedStateHandle.set(lastErrorMessageKey, _lastErrorMessageState.value)
  }

  companion object {
    private const val TAG = "ReplyLayoutState"

    private const val replyTextKey = "replyText"
    private const val attachedImagesKey = "attachedImages"
    private const val lastErrorMessageKey = "lastErrorMessage"
    private const val replyLayoutVisibilityKey = "replyLayoutVisibility"
  }

}

sealed class SendReplyState {
  val canCancel: Boolean
    get() {
      return when (this) {
        is Finished -> false
        Started -> true
      }
    }

  object Started : SendReplyState()
  data class Finished(val error: Throwable?) : SendReplyState()
}

enum class ReplyLayoutVisibility(val value: Int) {
  Closed(0),
  Opened(1),
  Expanded(2);

  companion object {
    fun fromRawValue(value: Int?): ReplyLayoutVisibility {
      if (value == null) {
        return Closed
      }

      return values()
        .firstOrNull { rlv -> rlv.value == value }
        ?: Closed
    }
  }
}