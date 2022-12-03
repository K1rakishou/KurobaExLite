package com.github.k1rakishou.kurobaexlite.features.reply

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.model.data.local.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalComponentActivity
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun ReplyLayoutContainer(
  chanDescriptor: ChanDescriptor?,
  replyLayoutState: IReplyLayoutState,
  navigationRouterProvider: () -> NavigationRouter,
  onReplayLayoutHeightChanged: (Dp) -> Unit,
  onPostedSuccessfully: (PostDescriptor) -> Unit,
  onAttachedMediaClicked: (AttachedMedia) -> Unit
) {
  if (chanDescriptor == null || replyLayoutState !is ReplyLayoutState) {
    return
  }

  DisposableEffect(
    key1 = chanDescriptor,
    effect = {
      onDispose {
        // When switching from one thread to one we should collapse the reply layout of the previous state
        // explicitly to avoid situations where the app will think that the reply layout is still opened while in
        // reality it isn't (the FAB won't be shown and the catalog/thread post lists will have additional bottom
        // paddings applied)
        replyLayoutState.collapseReplyLayout()
      }
    }
  )

  val componentActivity = LocalComponentActivity.current
  val chanTheme = LocalChanTheme.current
  val attachedMediaList = replyLayoutState.attachedMediaList

  val replyLayoutViewModel = koinRememberViewModel<ReplyLayoutViewModel>()
  val loadChanCatalog = koinRemember<LoadChanCatalog>()

  val replyLayoutContainerOpenedHeightDefaultDp = if (attachedMediaList.isEmpty()) {
    dimensionResource(id = R.dimen.reply_layout_container_opened_height_no_attachments)
  } else {
    dimensionResource(id = R.dimen.reply_layout_container_opened_height_with_attachments)
  }

  val coroutineScope = rememberCoroutineScope()

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
        when (errorMessage) {
          is ReplyErrorMessage.Dialog -> {
            replyLayoutViewModel.showErrorDialog(
              title = errorMessage.title,
              errorMessage = errorMessage.message
            )
          }
          is ReplyErrorMessage.Toast -> {
            replyLayoutViewModel.showErrorToast(
              chanDescriptor = chanDescriptor,
              errorMessage = errorMessage.message
            )
          }
        }
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
          onRemoveAttachedMediaClicked = { attachedMedia -> replyLayoutState.detachMedia(attachedMedia) },
          onFlagSelectorClicked = { chanDescriptor ->
            coroutineScope.launch {
              displayFlagSelector(
                componentActivity = componentActivity,
                chanDescriptor = chanDescriptor,
                navigationRouter = navigationRouterProvider(),
                loadChanCatalog = loadChanCatalog,
                replyLayoutViewModel = replyLayoutViewModel
              )
            }
          }
        )
      }
    }
  )
}

private suspend fun displayFlagSelector(
  componentActivity: ComponentActivity,
  chanDescriptor: ChanDescriptor,
  navigationRouter: NavigationRouter,
  loadChanCatalog: LoadChanCatalog,
  replyLayoutViewModel: ReplyLayoutViewModel
) {
  val boardFlags = loadChanCatalog.await(chanDescriptor).getOrNull()
    ?.flags
    ?.takeIf { it.isNotEmpty() }
    ?: return

  val floatingMenuItems = mutableListOf<FloatingMenuItem>()

  boardFlags.forEach { boardFlag ->
    floatingMenuItems += FloatingMenuItem.Text(
      menuItemKey = boardFlag.key,
      menuItemData = boardFlag,
      text = FloatingMenuItem.MenuItemText.String("[${boardFlag.key}] ${boardFlag.name}")
    )
  }

  val selectedBoardFlag = suspendCancellableCoroutine<BoardFlag?> { continuation ->
    val floatingMenuScreen = FloatingMenuScreen(
      floatingMenuKey = FloatingMenuScreen.FLAG_SELECTOR,
      componentActivity = componentActivity,
      navigationRouter = navigationRouter,
      menuItems = floatingMenuItems,
      onMenuItemClicked = { menuItem -> continuation.resumeSafe(menuItem.data as? BoardFlag) }
    )

    navigationRouter.presentScreen(floatingMenuScreen)
  }

  if (selectedBoardFlag == null) {
    return
  }

  replyLayoutViewModel.storeLastUsedFlag(
    chanDescriptor = chanDescriptor,
    boardFlag = selectedBoardFlag
  )
}
