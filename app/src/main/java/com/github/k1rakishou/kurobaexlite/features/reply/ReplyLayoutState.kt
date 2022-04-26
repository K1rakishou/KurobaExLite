package com.github.k1rakishou.kurobaexlite.features.reply

import android.os.Bundle
import android.os.Parcelable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ReplyData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import logcat.logcat
import org.koin.core.context.GlobalContext

@Stable
interface IReplyLayoutState {
  val replyLayoutVisibilityState: State<ReplyLayoutVisibility>

  fun onBackPressed(): Boolean
  fun openReplyLayout()
  fun detachMedia(attachedMedia: AttachedMedia)
}

@Stable
class FakeReplyLayoutState : IReplyLayoutState {
  private val _replyLayoutVisibilityState = mutableStateOf(ReplyLayoutVisibility.Closed)
  override val replyLayoutVisibilityState: State<ReplyLayoutVisibility>
    get() = _replyLayoutVisibilityState

  override fun onBackPressed(): Boolean {
    return false
  }

  override fun openReplyLayout() {

  }

  override fun detachMedia(attachedMedia: AttachedMedia) {

  }

}

@Stable
class ReplyLayoutState(
  val screenKey: ScreenKey,
  val chanDescriptor: ChanDescriptor,
  val bundle: Bundle = Bundle()
) : IReplyLayoutState {
  private val uiInfoManager: UiInfoManager by lazy { GlobalContext.get().get() }
  private val appResources: AppResources by lazy { GlobalContext.get().get() }

  private val _replyLayoutVisibilityState = mutableStateOf(ReplyLayoutVisibility.Closed)
  override val replyLayoutVisibilityState: State<ReplyLayoutVisibility>
    get() = _replyLayoutVisibilityState

  private val _replyText = mutableStateOf("")
  val replyText: State<String>
    get() = _replyText

  private val _attachedMediaList = mutableStateListOf<AttachedMedia>()
  val attachedMediaList: List<AttachedMedia>
    get() = _attachedMediaList

  private val _sendReplyState = mutableStateOf<SendReplyState>(SendReplyState.Finished(null))
  val sendReplyState: State<SendReplyState>
    get() = _sendReplyState

  private val _replySendProgressState = mutableStateOf<Float?>(null)
  val replySendProgressState: State<Float?>
    get() = _replySendProgressState

  private val _replyErrorMessageFlow = MutableSharedFlow<String>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )
  val replyErrorMessageFlow: SharedFlow<String>
    get() = _replyErrorMessageFlow.asSharedFlow()

  private val _replyMessageFlow = MutableSharedFlow<String>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )
  val replyMessageFlow: SharedFlow<String>
    get() = _replyMessageFlow.asSharedFlow()

  init {
    when (chanDescriptor) {
      is CatalogDescriptor -> bundle.putParcelable(chanDescriptorKey, chanDescriptor)
      is ThreadDescriptor -> bundle.putParcelable(chanDescriptorKey, chanDescriptor)
    }

    restoreFromBundle()
  }

  private fun restoreFromBundle() {
    Snapshot.withMutableSnapshot {
      _replyText.value = bundle.getString(replyTextKey) ?: ""
      _replyLayoutVisibilityState.value = bundle.getInt(replyLayoutVisibilityKey)
        .let { ReplyLayoutVisibility.fromRawValue(it) }

      bundle.getParcelableArrayList<AttachedMedia>(attachedMediaListKey)?.let { prevAttachedImagePathList ->
        val prevAttachedImages = prevAttachedImagePathList
          .filter { attachedMedia -> attachedMedia.exists() }

        _attachedMediaList.clear()
        _attachedMediaList.addAll(prevAttachedImages)
      }

      onReplyLayoutVisibilityStateChanged()

      logcat(TAG) {
        "restoreFromBundle() " +
          "replyLayoutVisibilityState=${_replyLayoutVisibilityState.value}, "
          "replyText=\'${_replyText.value.take(32)}\', " +
          "attachedImages=\'${_attachedMediaList.joinToString(transform = { it.path })}\'"
      }
    }
  }

  fun replyError(errorMessage: String) {
    _replyErrorMessageFlow.tryEmit(errorMessage)
  }

  fun replyMessage(message: String) {
    _replyMessageFlow.tryEmit(message)
  }

  fun onReplySendStarted() {
    _sendReplyState.value = SendReplyState.Started
  }

  fun onReplyProgressChanged(progress: Float?) {
    _replySendProgressState.value = progress
  }

  fun onReplySendEndedSuccessfully() {
    Snapshot.withMutableSnapshot {
      _attachedMediaList.forEach { attachedMedia -> attachedMedia.deleteFile() }

      _attachedMediaList.clear()
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Closed
      _replyText.value = ""

      onAttachedImagesUpdated()
      onReplyLayoutVisibilityStateChanged()

      _sendReplyState.value = SendReplyState.Finished(error = null)
    }
  }

  fun onReplySendCanceled() {
    Snapshot.withMutableSnapshot {
      _sendReplyState.value = SendReplyState.Finished(error = null)
    }
  }

  fun onReplySendEndedWithError(error: Throwable) {
    Snapshot.withMutableSnapshot {
      onAttachedImagesUpdated()
      onReplyLayoutVisibilityStateChanged()

      _sendReplyState.value = SendReplyState.Finished(error = error)
    }
  }

  fun attachMedia(attachedMedia: AttachedMedia) {
    _attachedMediaList += attachedMedia
    onAttachedImagesUpdated()
  }

  override fun detachMedia(attachedMedia: AttachedMedia) {
    _attachedMediaList -= attachedMedia
    attachedMedia.deleteFile()
    onAttachedImagesUpdated()

    val message = appResources.string(R.string.reply_attached_media_removed, attachedMedia.actualFileName)
    replyMessage(message)
  }

  fun onReplyTextChanged(newTextFieldValue: String) {
    _replyText.value = newTextFieldValue
    bundle.putString(replyTextKey, newTextFieldValue)
  }

  override fun openReplyLayout() {
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

  override fun onBackPressed(): Boolean {
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
    bundle.putParcelableArrayList(attachedMediaListKey, ArrayList(_attachedMediaList))
  }

  private fun onReplyLayoutVisibilityStateChanged() {
    val replyLayoutVisibilityStateValue = _replyLayoutVisibilityState.value
    uiInfoManager.replyLayoutVisibilityStateChanged(screenKey, replyLayoutVisibilityStateValue)
    bundle.putInt(replyLayoutVisibilityKey, replyLayoutVisibilityStateValue.value)
  }

  fun getReplyData(chanDescriptor: ChanDescriptor, captchaSolution: CaptchaSolution): ReplyData {
    return ReplyData(
      chanDescriptor = chanDescriptor,
      message = replyText.value,
      attachedMediaList = attachedMediaList,
      captchaSolution = captchaSolution
    )
  }

  companion object {
    private const val TAG = "ReplyLayoutState"

    private const val replyTextKey = "replyText"
    private const val attachedMediaListKey = "attachedMediaList"
    private const val replyLayoutVisibilityKey = "replyLayoutVisibility"
    const val chanDescriptorKey = "chanDescriptor"
  }

}

@Parcelize
data class AttachedMedia(
  val path: String,
  val fileName: String? = null
): Parcelable {

  @IgnoredOnParcel
  val asFile by lazy { File(path) }

  val actualFileName: String
    get() {
      return fileName ?: asFile.name
    }

  fun exists(): Boolean = asFile.exists()

  fun deleteFile() {
    val file = asFile
    if (file.exists()) {
      file.delete()
    }
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