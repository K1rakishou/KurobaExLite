package com.github.k1rakishou.kurobaexlite.features.reply

import android.os.Bundle
import android.os.Parcelable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.text.input.getTextAfterSelection
import androidx.compose.ui.text.input.getTextBeforeSelection
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.ReplyData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.FormattingButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
  private val globalUiInfoManager: GlobalUiInfoManager by lazy { GlobalContext.get().get() }
  private val siteManager: SiteManager by lazy { GlobalContext.get().get() }
  private val appResources: AppResources by lazy { GlobalContext.get().get() }

  private val _replyLayoutVisibilityState = mutableStateOf(ReplyLayoutVisibility.Closed)
  override val replyLayoutVisibilityState: State<ReplyLayoutVisibility>
    get() = _replyLayoutVisibilityState

  private val _successfullyPostedEventsFlow = MutableSharedFlow<PostDescriptor>(extraBufferCapacity = Channel.UNLIMITED)
  val successfullyPostedEventsFlow: SharedFlow<PostDescriptor>
    get() = _successfullyPostedEventsFlow.asSharedFlow()

  private val _replyText = mutableStateOf(TextFieldValue())
  val replyText: State<TextFieldValue>
    get() = _replyText

  private val _attachedMediaList = mutableStateListOf<AttachedMedia>()
  val attachedMediaList: List<AttachedMedia>
    get() = _attachedMediaList

  private val _sendReplyState = mutableStateOf<SendReplyState>(SendReplyState.Finished)
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

  private val _replyMessageFlow = MutableSharedFlow<ToastMessage>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )
  val replyMessageFlow: SharedFlow<ToastMessage>
    get() = _replyMessageFlow.asSharedFlow()

  private val _replyFormattingButtons = MutableStateFlow<List<FormattingButton>>(emptyList())
  val replyFormattingButtons: StateFlow<List<FormattingButton>>
    get() = _replyFormattingButtons.asStateFlow()

  init {
    when (chanDescriptor) {
      is CatalogDescriptor -> bundle.putParcelable(chanDescriptorKey, chanDescriptor)
      is ThreadDescriptor -> bundle.putParcelable(chanDescriptorKey, chanDescriptor)
    }

    restoreFromBundle()
    initReplyFormattingButtons()
  }

  private fun initReplyFormattingButtons() {
    val newFormattingButtons = siteManager.bySiteKey(chanDescriptor.siteKey)
      ?.commentFormattingButtons(chanDescriptor.catalogDescriptor())
      ?: emptyList()

    _replyFormattingButtons.value = newFormattingButtons
  }

  private fun restoreFromBundle() {
    val replyTextFromBundle = bundle.getString(replyTextKey) ?: ""

    val textFieldValue = TextFieldValue(
      text = replyTextFromBundle,
      selection = TextRange(replyTextFromBundle.length)
    )

    onReplyTextChanged(textFieldValue)

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
        "replyLayoutVisibilityState=${_replyLayoutVisibilityState.value}, " +
        "replyText=\'${replyTextFromBundle.take(32)}\', " +
        "attachedImages=\'${_attachedMediaList.joinToString(transform = { it.path })}\'"
    }
  }

  fun replyShowErrorToast(errorMessage: String) {
    _replyErrorMessageFlow.tryEmit(errorMessage)
  }

  fun replyShowInfoToast(message: String, toastId: String? = null) {
    _replyMessageFlow.tryEmit(ToastMessage(message, toastId))
  }

  fun onReplySendStarted() {
    _sendReplyState.value = SendReplyState.Started
  }

  fun onReplySendEnded() {
    _sendReplyState.value = SendReplyState.ReplySent
  }

  fun onReplyProgressChanged(progress: Float?) {
    _replySendProgressState.value = progress
  }

  fun onReplySendFinishedSuccessfully(postDescriptor: PostDescriptor) {
    Snapshot.withMutableSnapshot {
      _attachedMediaList.forEach { attachedMedia -> attachedMedia.deleteFile() }

      _attachedMediaList.clear()
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Closed
      _replyText.value = TextFieldValue()

      onAttachedImagesUpdated()
      onReplyLayoutVisibilityStateChanged()

      _sendReplyState.value = SendReplyState.Finished
      _successfullyPostedEventsFlow.tryEmit(postDescriptor)
    }
  }

  fun onReplySendFinishedUnsuccesfully() {
    _sendReplyState.value = SendReplyState.Finished
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
    replyShowInfoToast(message, toastId = "attached_media_removed")
  }

  fun onReplyTextChanged(text: String) {
    val textFieldValue = TextFieldValue(
      text = text,
      selection = TextRange(index = text.length)
    )

    onReplyTextChanged(textFieldValue)
  }

  fun onReplyTextChanged(newTextFieldValue: TextFieldValue) {
    _replyText.value = newTextFieldValue
    bundle.putString(replyTextKey, newTextFieldValue.text)
  }

  override fun openReplyLayout() {
    if (_replyLayoutVisibilityState.value == ReplyLayoutVisibility.Closed) {
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Opened
      onReplyLayoutVisibilityStateChanged()
    }
  }

  fun expandReplyLayout() {
    if (_replyLayoutVisibilityState.value == ReplyLayoutVisibility.Opened) {
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Expanded
      onReplyLayoutVisibilityStateChanged()
    }
  }

  fun collapseReplyLayout() {
    if (_replyLayoutVisibilityState.value == ReplyLayoutVisibility.Expanded) {
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Opened
      onReplyLayoutVisibilityStateChanged()
    }
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
    globalUiInfoManager.replyLayoutVisibilityStateChanged(screenKey, replyLayoutVisibilityStateValue)
    bundle.putInt(replyLayoutVisibilityKey, replyLayoutVisibilityStateValue.value)
  }

  fun getReplyData(chanDescriptor: ChanDescriptor, captchaSolution: CaptchaSolution): ReplyData {
    return ReplyData(
      chanDescriptor = chanDescriptor,
      message = replyText.value.text,
      attachedMediaList = attachedMediaList,
      captchaSolution = captchaSolution
    )
  }

  fun appendPostQuote(postDescriptor: PostDescriptor) {
    val newReplyText = buildString {
      if (_replyText.value.text.isNotBlank()) {
        append(_replyText.value.text)
        appendLine()
      }

      append(">>")
      append(postDescriptor.postNo)
      appendLine()
    }

    onReplyTextChanged(newReplyText)
    openReplyLayout()
  }

  fun appendPostQuoteWithComment(postDescriptor: PostDescriptor, comment: String) {
    val newReplyText = buildString {
      if (_replyText.value.text.isNotBlank()) {
        append(_replyText.value.text)
        appendLine()
      }

      append(">>")
      append(postDescriptor.postNo)
      append("\n")

      comment.lines().forEach { commentLine ->
        if (commentLine.isEmpty()) {
          appendLine()
          return@forEach
        }

        appendLine(">${commentLine}")
      }
    }

    onReplyTextChanged(newReplyText)
    openReplyLayout()
  }

  fun insertTags(formattingButton: FormattingButton) {
    Snapshot.withMutableSnapshot {
      val replyText = replyText.value
      var cursorPosition = 0

      val replyTextWithNewTags = buildAnnotatedString {
        if (replyText.selection.collapsed) {
          append(replyText.getTextBeforeSelection(replyText.text.length))
          append(formattingButton.openTag)
          cursorPosition = this.length
          append(formattingButton.closeTag)
          append(replyText.getTextAfterSelection(replyText.text.length))
        } else {
          append(replyText.getTextBeforeSelection(replyText.text.length))
          append(formattingButton.openTag)
          append(replyText.getSelectedText())
          append(formattingButton.closeTag)
          cursorPosition = this.length
          append(replyText.getTextAfterSelection(replyText.text.length))
        }
      }

      val textFieldValue = TextFieldValue(
        annotatedString = replyTextWithNewTags,
        selection = TextRange(cursorPosition),
        composition = replyText.composition
      )

      onReplyTextChanged(textFieldValue)
    }
  }

  data class ToastMessage(
    val message: String,
    val toastId: String?
  )

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
        is ReplySent,
        is Finished -> false
        Started -> true
      }
    }

  object Started : SendReplyState()
  object ReplySent : SendReplyState()
  object Finished : SendReplyState()
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