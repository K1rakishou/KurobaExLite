package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks

@Composable
fun ReplyLayoutContainer(
  chanDescriptor: ChanDescriptor?,
  replyLayoutState: IReplyLayoutState,
  replyLayoutViewModel: ReplyLayoutViewModel,
  onReplayLayoutHeightChanged: (Dp) -> Unit,
  onPostedSuccessfully: (PostDescriptor) -> Unit,
  onAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  if (chanDescriptor == null || replyLayoutState !is ReplyLayoutState) {
    return
  }

  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current
  val windowInsets = LocalWindowInsets.current
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  val attachedMediaList = replyLayoutState.attachedMediaList

  val replyLayoutContainerOpenedHeightDefault = if (attachedMediaList.isEmpty()) {
    dimensionResource(id = R.dimen.reply_layout_container_opened_height_no_attachments)
  } else {
    dimensionResource(id = R.dimen.reply_layout_container_opened_height_with_attachments)
  }

  LaunchedEffect(
    key1 = replyLayoutContainerOpenedHeightDefault,
    block = { onReplayLayoutHeightChanged(replyLayoutContainerOpenedHeightDefault) }
  )

  var maxAvailableHeight by remember { mutableStateOf<Int>(0) }

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

  Box(
    modifier = Modifier
      .fillMaxSize()
      .onSizeChanged { size -> maxAvailableHeight = size.height },
    contentAlignment = Alignment.BottomCenter
  ) {
    val replyLayoutVisibility by replyLayoutState.replyLayoutVisibilityState

    val replyLayoutContainerTransition = updateTransition(
      targetState = replyLayoutVisibility,
      label = "Reply layout container transition animation"
    )

    val heightAnimated by replyLayoutContainerTransition.animateDp(
      label = "Reply layout container height animation",
      transitionSpec = { spring() }
    ) { rlv ->
      when (rlv) {
        ReplyLayoutVisibility.Closed,
        ReplyLayoutVisibility.Opened -> replyLayoutContainerOpenedHeightDefault
        ReplyLayoutVisibility.Expanded -> with(density) { maxAvailableHeight.toDp() }
      }
    }

    val offsetYAnimated by replyLayoutContainerTransition.animateDp(
      label = "Reply layout container offset Y animation",
      transitionSpec = { spring() }
    ) { rlv ->
      when (rlv) {
        ReplyLayoutVisibility.Closed -> replyLayoutContainerOpenedHeightDefault
        ReplyLayoutVisibility.Opened,
        ReplyLayoutVisibility.Expanded -> 0.dp
      }
    }

    Column(
      modifier = Modifier
        .offset(y = offsetYAnimated)
        .consumeClicks(enabled = true)
        .background(chanTheme.backColor)
    ) {
      if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
        Spacer(modifier = Modifier.height(windowInsets.top + toolbarHeight))
      } else if (replyLayoutVisibility == ReplyLayoutVisibility.Opened) {
        KurobaComposeDivider(modifier = Modifier.fillMaxWidth())
      }

      val replyLayoutHeightClampedDp = if (replyLayoutVisibility == ReplyLayoutVisibility.Closed) {
        heightAnimated
      } else {
        with(density) {
          val heightAnimatedPx = heightAnimated.toPx()
          val maxAllowedHeightPx = maxAvailableHeight - (windowInsets.bottom + windowInsets.top + toolbarHeight).toPx()

          return@with heightAnimatedPx.coerceAtMost(maxAllowedHeightPx).toDp()
        }
      }

      ReplyLayout(
        replyLayoutState = replyLayoutState,
        replyLayoutHeight = replyLayoutHeightClampedDp,
        replyLayoutVisibility = replyLayoutVisibility,
        attachedMediaList = attachedMediaList,
        onExpandReplyLayoutClicked = { replyLayoutState.expandReplyLayout() },
        onCollapseReplyLayoutClicked = { replyLayoutState.collapseReplyLayout() },
        onCancelReplySendClicked = { replyLayoutViewModel.cancelSendReply(replyLayoutState) },
        onSendReplyClicked = { replyLayoutViewModel.sendReply(chanDescriptor, replyLayoutState) },
        onAttachedMediaClicked = onAttachedMediaClicked,
        onRemoveAttachedMediaClicked = { attachedMedia -> replyLayoutState.detachMedia(attachedMedia) }
      )

      if (replyLayoutVisibility != ReplyLayoutVisibility.Closed) {
        Spacer(modifier = Modifier.height(windowInsets.bottom))
      }
    }
  }
}

@Composable
private fun ReplyLayout(
  replyLayoutState: ReplyLayoutState,
  replyLayoutHeight: Dp,
  replyLayoutVisibility: ReplyLayoutVisibility,
  attachedMediaList: List<AttachedMedia>,
  onExpandReplyLayoutClicked: () -> Unit,
  onCollapseReplyLayoutClicked: () -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit,
  onAttachedMediaClicked: (AttachedMedia) -> Unit,
  onRemoveAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  if (replyLayoutHeight < 0.dp) {
    return
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(replyLayoutHeight)
  ) {
    val density = LocalDensity.current
    val spacerHeight = 8.dp

    val replyInputHeightPercentage = if (replyLayoutHeight >= 160.dp) {
      when (replyLayoutVisibility) {
        ReplyLayoutVisibility.Closed -> 100f
        ReplyLayoutVisibility.Opened -> if (attachedMediaList.isEmpty()) 100f else 70f
        ReplyLayoutVisibility.Expanded -> 60f
      }
    } else {
      100f
    }

    val replyLayoutHeightExcludingSpacer = replyLayoutHeight - spacerHeight
    val replyInputHeight = with(density) {
      ((replyLayoutHeightExcludingSpacer.toPx() / 100f) * replyInputHeightPercentage).toDp()
    }

    ReplyInputWithButtons(
      height = replyInputHeight,
      replyLayoutState = replyLayoutState,
      onExpandReplyLayoutClicked = onExpandReplyLayoutClicked,
      onCollapseReplyLayoutClicked = onCollapseReplyLayoutClicked,
      onCancelReplySendClicked = onCancelReplySendClicked,
      onSendReplyClicked = onSendReplyClicked
    )

    val replyAttachmentsHeight = replyLayoutHeightExcludingSpacer - replyInputHeight
    if (attachedMediaList.isNotEmpty() && replyAttachmentsHeight > 32.dp) {
      Spacer(modifier = Modifier.height(spacerHeight))

      ReplyAttachments(
        height = replyAttachmentsHeight,
        replyLayoutVisibility = replyLayoutVisibility,
        attachedMediaList = attachedMediaList,
        onAttachedMediaClicked = onAttachedMediaClicked,
        onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
      )
    }
  }
}
