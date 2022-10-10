package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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
    content = { targetHeight, draggableState, onDragStarted, onDragStopped ->
      if (targetHeight > 0.dp) {
        KurobaComposeDivider(modifier = Modifier.fillMaxWidth())
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .height(targetHeight)
      ) {
        ReplyLayoutContent(
          replyLayoutState = replyLayoutState,
          draggableStateProvider = { draggableState },
          onDragStarted = onDragStarted,
          onDragStopped = onDragStopped,
          onCancelReplySendClicked = { replyLayoutViewModel.cancelSendReply(replyLayoutState) },
          onSendReplyClicked = { replyLayoutViewModel.sendReply(chanDescriptor, replyLayoutState) },
          onAttachedMediaClicked = onAttachedMediaClicked,
          onRemoveAttachedMediaClicked = { attachedMedia -> replyLayoutState.detachMedia(attachedMedia) }
        )
      }
    }
  )
}