package com.github.k1rakishou.kurobaexlite.features.reply

import android.os.Bundle
import android.os.Parcelable
import androidx.compose.runtime.Immutable
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
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.data.local.ReplyData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.sites.FormattingButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
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
import java.io.File

@Stable
interface IReplyLayoutState {
  val replyLayoutVisibilityState: State<ReplyLayoutVisibility>

  fun onBackPressed(): Boolean
  fun collapseReplyLayout()
  fun openReplyLayout()
  fun expandReplyLayout()
  fun contractReplyLayout()
  fun detachMedia(attachedMedia: AttachedMedia)
}

@Stable
class FakeReplyLayoutState : IReplyLayoutState {
  private val _replyLayoutVisibilityState = mutableStateOf(ReplyLayoutVisibility.Collapsed)
  override val replyLayoutVisibilityState: State<ReplyLayoutVisibility>
    get() = _replyLayoutVisibilityState

  override fun onBackPressed(): Boolean {
    return false
  }

  override fun openReplyLayout() {

  }

  override fun expandReplyLayout() {

  }

  override fun collapseReplyLayout() {

  }

  override fun contractReplyLayout() {

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

  val isCatalogMode: Boolean
    get() = chanDescriptor is CatalogDescriptor

  private val _replyLayoutVisibilityState = mutableStateOf(ReplyLayoutVisibility.Collapsed)
  override val replyLayoutVisibilityState: State<ReplyLayoutVisibility>
    get() = _replyLayoutVisibilityState

  private val _successfullyPostedEventsFlow = MutableSharedFlow<PostDescriptor>(extraBufferCapacity = Channel.UNLIMITED)
  val successfullyPostedEventsFlow: SharedFlow<PostDescriptor>
    get() = _successfullyPostedEventsFlow.asSharedFlow()

  private val _replyText = mutableStateOf(TextFieldValue())
  val replyText: State<TextFieldValue>
    get() = _replyText

  private val _subject = mutableStateOf(TextFieldValue())
  val subject: State<TextFieldValue>
    get() = _subject

  private val _name = mutableStateOf(TextFieldValue())
  val name: State<TextFieldValue>
    get() = _name

  private val _options = mutableStateOf(TextFieldValue())
  val options: State<TextFieldValue>
    get() = _options

  private val _flag = mutableStateOf<BoardFlag?>(null)
  val flag: State<BoardFlag?>
    get() = _flag

  private val _maxCommentLength = mutableStateOf(0)
  val maxCommentLength: State<Int>
    get() = _maxCommentLength

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
    bundle.putParcelable(chanDescriptorKey, chanDescriptor)
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
    bundle.getString(replyTextKey).let { replyTextFromBundle ->
      val actualReplyText = replyTextFromBundle ?: ""

      val textFieldValue = TextFieldValue(
        text = actualReplyText,
        selection = TextRange(actualReplyText.length)
      )

      onReplyTextChanged(textFieldValue)
    }

    bundle.getString(subjectTextKey).let { subjectTextFromBundle ->
      val actualSubjectText = subjectTextFromBundle ?: ""

      val textFieldValue = TextFieldValue(
        text = actualSubjectText,
        selection = TextRange(actualSubjectText.length)
      )

      onSubjectChanged(textFieldValue)
    }

    bundle.getString(nameTextKey).let { nameTextFromBundle ->
      val actualNameText = nameTextFromBundle ?: ""

      val textFieldValue = TextFieldValue(
        text = actualNameText,
        selection = TextRange(actualNameText.length)
      )

      onNameChanged(textFieldValue)
    }

    bundle.getString(optionsTextKey).let { optionsTextFromBundle ->
      val actualOptionsText = optionsTextFromBundle ?: ""

      val textFieldValue = TextFieldValue(
        text = actualOptionsText,
        selection = TextRange(actualOptionsText.length)
      )

      onOptionsChanged(textFieldValue)
    }

    bundle.getParcelable<BoardFlag>(flagKey).let { flagFromBundle ->
      onFlagChanged(flagFromBundle)
    }

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
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Collapsed
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

  fun onSubjectChanged(newTextFieldValue: TextFieldValue) {
    _subject.value = newTextFieldValue
    bundle.putString(subjectTextKey, newTextFieldValue.text)
  }

  fun onNameChanged(newTextFieldValue: TextFieldValue) {
    _name.value = newTextFieldValue
    bundle.putString(nameTextKey, newTextFieldValue.text)
  }

  fun onOptionsChanged(newTextFieldValue: TextFieldValue) {
    _options.value = newTextFieldValue
    bundle.putString(optionsTextKey, newTextFieldValue.text)
  }

  fun onFlagChanged(boardFlag: BoardFlag?) {
    _flag.value = boardFlag
    bundle.putParcelable(flagKey, boardFlag)
  }

  override fun collapseReplyLayout() {
    if (_replyLayoutVisibilityState.value != ReplyLayoutVisibility.Collapsed) {
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Collapsed
      onReplyLayoutVisibilityStateChanged()
    }
  }

  override fun openReplyLayout() {
    if (_replyLayoutVisibilityState.value != ReplyLayoutVisibility.Opened) {
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Opened
      onReplyLayoutVisibilityStateChanged()
    }
  }

  override fun expandReplyLayout() {
    if (_replyLayoutVisibilityState.value != ReplyLayoutVisibility.Expanded) {
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Expanded
      onReplyLayoutVisibilityStateChanged()
    }
  }

  override fun contractReplyLayout() {
    if (_replyLayoutVisibilityState.value == ReplyLayoutVisibility.Expanded) {
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Opened
      onReplyLayoutVisibilityStateChanged()
    }
  }

  override fun onBackPressed(): Boolean {
    val currentState = replyLayoutVisibilityState.value
    if (currentState == ReplyLayoutVisibility.Collapsed) {
      return false
    }

    if (currentState == ReplyLayoutVisibility.Expanded) {
      _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Opened
      onReplyLayoutVisibilityStateChanged()
      return true
    }

    _replyLayoutVisibilityState.value = ReplyLayoutVisibility.Collapsed
    onReplyLayoutVisibilityStateChanged()
    return true
  }

  private fun onAttachedImagesUpdated() {
    bundle.putParcelableArrayList(attachedMediaListKey, ArrayList(_attachedMediaList))
  }

  private fun onReplyLayoutVisibilityStateChanged() {
    val replyLayoutVisibilityStateValue = _replyLayoutVisibilityState.value
    globalUiInfoManager.replyLayoutVisibilityStateChanged(screenKey, replyLayoutVisibilityStateValue)
    bundle.putInt(replyLayoutVisibilityKey, replyLayoutVisibilityStateValue.order)
  }

  fun getReplyData(chanDescriptor: ChanDescriptor, captchaSolution: CaptchaSolution): ReplyData {
    return ReplyData(
      chanDescriptor = chanDescriptor,
      message = replyText.value.text,
      subject = subject.value.text.takeIf { it.isNotEmpty() },
      name = name.value.text.takeIf { it.isNotEmpty() },
      flag = flag.value,
      options = options.value.text.takeIf { it.isNotEmpty() },
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ReplyLayoutState

    if (screenKey != other.screenKey) return false
    if (chanDescriptor != other.chanDescriptor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = screenKey.hashCode()
    result = 31 * result + chanDescriptor.hashCode()
    return result
  }

  data class ToastMessage(
    val message: String,
    val toastId: String?
  )

  companion object {
    private const val TAG = "ReplyLayoutState"

    private const val replyTextKey = "replyText"
    private const val subjectTextKey = "subjectText"
    private const val nameTextKey = "nameText"
    private const val optionsTextKey = "optionsText"
    private const val flagKey = "flag"
    private const val attachedMediaListKey = "attachedMediaList"
    private const val replyLayoutVisibilityKey = "replyLayoutVisibility"
    const val chanDescriptorKey = "chanDescriptor"
  }

}

@Immutable
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

@Immutable
enum class ReplyLayoutVisibility(val order: Int) {
  Collapsed(0),
  Opened(1),
  Expanded(2);

  companion object {
    fun fromRawValue(value: Int?): ReplyLayoutVisibility {
      if (value == null) {
        return Collapsed
      }

      return values()
        .firstOrNull { rlv -> rlv.order == value }
        ?: Collapsed
    }
  }
}