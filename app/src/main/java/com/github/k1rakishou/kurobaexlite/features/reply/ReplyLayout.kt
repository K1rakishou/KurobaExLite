package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme

@Composable
fun ReplyLayoutContainer(
  chanDescriptor: ChanDescriptor?,
  replyLayoutState: IReplyLayoutState,
  onReplayLayoutHeightChanged: (Dp) -> Unit,
  onPostedSuccessfully: (PostDescriptor) -> Unit,
  onAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  if (chanDescriptor == null || replyLayoutState !is ReplyLayoutState) {
    return
  }

  val chanTheme = LocalChanTheme.current
  val attachedMediaList = replyLayoutState.attachedMediaList

  val replyLayoutViewModel: ReplyLayoutViewModel = koinRememberViewModel()

  val replyLayoutContainerOpenedHeightDefaultDp = if (attachedMediaList.isEmpty()) {
    dimensionResource(id = R.dimen.reply_layout_container_opened_height_no_attachments)
  } else {
    dimensionResource(id = R.dimen.reply_layout_container_opened_height_with_attachments)
  }

  LaunchedEffect(
    key1 = replyLayoutContainerOpenedHeightDefaultDp,
    block = { onReplayLayoutHeightChanged(replyLayoutContainerOpenedHeightDefaultDp) }
  )

  LaunchedEffect(
    key1 = chanDescriptor,
    block = {
      replyLayoutState.successfullyPostedEventsFlow.collect { postDescriptor ->
        onPostedSuccessfully(postDescriptor)
      }
    }
  )

  LaunchedEffect(
    key1 = chanDescriptor,
    block = {
      replyLayoutState.replyErrorMessageFlow.collect { errorMessage ->
        replyLayoutViewModel.showErrorToast(chanDescriptor, errorMessage)
      }
    }
  )

  LaunchedEffect(
    key1 = chanDescriptor,
    block = {
      replyLayoutState.replyMessageFlow.collect { errorMessage ->
        replyLayoutViewModel.showToast(chanDescriptor, errorMessage)
      }
    }
  )

  LaunchedEffect(
    key1 = chanDescriptor,
    block = {
      replyLayoutViewModel.pickFileResultFlow.collect { pickFileResult ->
        if (pickFileResult.chanDescriptor != chanDescriptor) {
          return@collect
        }

        pickFileResult.newPickedMedias.forEach { attachedMedia ->
          replyLayoutState.attachMedia(attachedMedia)
        }
      }
    }
  )

  ReplyLayoutBottomSheet(
    replyLayoutState = replyLayoutState,
    chanTheme = chanTheme,
    content = { replyLayoutVisibility, targetHeight ->
      if (targetHeight > 0.dp) {
        KurobaComposeDivider(modifier = Modifier.fillMaxWidth())
      }

      ReplyLayout(
        replyLayoutHeight = targetHeight,
        replyLayoutState = replyLayoutState,
        replyLayoutVisibility = replyLayoutVisibility,
        onExpandReplyLayoutClicked = { replyLayoutState.expandReplyLayout() },
        onContractReplyLayoutClicked = { replyLayoutState.contractReplyLayout() },
        onCancelReplySendClicked = { replyLayoutViewModel.cancelSendReply(replyLayoutState) },
        onSendReplyClicked = { replyLayoutViewModel.sendReply(chanDescriptor, replyLayoutState) },
        onAttachedMediaClicked = onAttachedMediaClicked,
        onRemoveAttachedMediaClicked = { attachedMedia -> replyLayoutState.detachMedia(attachedMedia) }
      )
    }
  )
}

@Composable
private fun ReplyLayout(
  replyLayoutHeight: Dp,
  replyLayoutState: ReplyLayoutState,
  replyLayoutVisibility: ReplyLayoutVisibility,
  onExpandReplyLayoutClicked: () -> Unit,
  onContractReplyLayoutClicked: () -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit,
  onAttachedMediaClicked: (AttachedMedia) -> Unit,
  onRemoveAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  val attachedMediaList = replyLayoutState.attachedMediaList

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .height(replyLayoutHeight)
  ) {
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxSize()) {
      val replyInputHeightPercentage = if (replyLayoutHeight >= 160.dp) {
        when {
          replyLayoutVisibility == ReplyLayoutVisibility.Collapsed -> 100f
          attachedMediaList.isEmpty() -> 100f
          else -> 70f
        }
      } else {
        100f
      }

      val spacerHeight = 8.dp
      val replyLayoutHeightExcludingSpacer = replyLayoutHeight - spacerHeight
      val replyInputHeight = with(density) {
        ((replyLayoutHeightExcludingSpacer.toPx() / 100f) * replyInputHeightPercentage).toDp()
      }

      ReplyInputWithButtons(
        replyInputHeight = replyInputHeight,
        replyLayoutState = replyLayoutState,
        onExpandReplyLayoutClicked = onExpandReplyLayoutClicked,
        onContractReplyLayoutClicked = onContractReplyLayoutClicked,
        onCancelReplySendClicked = onCancelReplySendClicked,
        onSendReplyClicked = onSendReplyClicked
      )

      val replyAttachmentsHeight = replyLayoutHeightExcludingSpacer - replyInputHeight
      if (attachedMediaList.isNotEmpty() && replyAttachmentsHeight > 32.dp) {
        Spacer(modifier = Modifier.height(spacerHeight))

        ReplyAttachments(
          replyAttachmentsHeight = replyAttachmentsHeight,
          replyLayoutState = replyLayoutState,
          onAttachedMediaClicked = onAttachedMediaClicked,
          onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
        )
      }
    }
  }
}