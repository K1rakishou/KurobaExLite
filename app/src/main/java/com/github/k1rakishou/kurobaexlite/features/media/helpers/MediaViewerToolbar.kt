package com.github.k1rakishou.kurobaexlite.features.media.helpers

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.media.ChildToolbar
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreenToolbarContainer
import com.github.k1rakishou.kurobaexlite.helpers.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.LeftIconInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.MiddlePartInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import java.util.Locale

@OptIn(ExperimentalPagerApi::class)
@Composable
internal fun MediaViewerToolbar(
  toolbarHeight: Dp,
  screenKey: ScreenKey,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  mediaViewerScreenState: MediaViewerScreen.MediaViewerScreenState,
  pagerState: PagerState?,
  onBackPressed: () -> Unit
) {
  if (!mediaViewerScreenState.isLoaded() || pagerState == null) {
    return
  }

  val currentImageIndex = pagerState.currentPage
  val targetImageIndex = pagerState.targetPage

  val currentToolbarKey = remember(key1 = currentImageIndex) {
    mediaViewerScreenState.requireImages().get(currentImageIndex).fullImageUrlAsString
  }

  val targetToolbarKey = remember(key1 = targetImageIndex) {
    mediaViewerScreenState.requireImages().get(targetImageIndex).fullImageUrlAsString
  }

  val childToolbars = remember(key1 = currentToolbarKey, key2 = targetToolbarKey) {
    val childToolbars = mutableListOf<ChildToolbar>()

    childToolbars += ChildToolbar(
      key = currentToolbarKey,
      indexInList = currentImageIndex,
      content = {
        MediaToolbar(
          screenKey = screenKey,
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          mediaViewerScreenState = mediaViewerScreenState,
          currentPagerPage = currentImageIndex,
          onBackPressed = onBackPressed
        )
      }
    )

    childToolbars += ChildToolbar(
      key = targetToolbarKey,
      indexInList = targetImageIndex,
      content = {
        MediaToolbar(
          screenKey = screenKey,
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          mediaViewerScreenState = mediaViewerScreenState,
          currentPagerPage = targetImageIndex,
          onBackPressed = onBackPressed
        )
      }
    )

    return@remember childToolbars
  }

  MediaViewerScreenToolbarContainer(
    toolbarHeight = toolbarHeight,
    pagerState = pagerState,
    childToolbars = childToolbars
  )
}

@Composable
private fun MediaToolbar(
  screenKey: ScreenKey,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  mediaViewerScreenState: MediaViewerScreen.MediaViewerScreenState,
  currentPagerPage: Int,
  onBackPressed: () -> Unit
) {
  val kurobaToolbarState = remember {
    return@remember KurobaToolbarState(
      leftIconInfo = LeftIconInfo(R.drawable.ic_baseline_arrow_back_24),
      middlePartInfo = MiddlePartInfo(centerContent = false),
    )
  }

  UpdateMediaViewerToolbarTitle(
    mediaViewerScreenState = mediaViewerScreenState,
    kurobaToolbarState = kurobaToolbarState,
    currentPagerPage = currentPagerPage
  )

  KurobaToolbar(
    screenKey = screenKey,
    kurobaToolbarState = kurobaToolbarState,
    canProcessBackEvent = { true },
    onLeftIconClicked = { onBackPressed() },
    onMiddleMenuClicked = {
      // no-op
    },
    onSearchQueryUpdated = null,
    onToolbarSortClicked = null,
    onToolbarOverflowMenuClicked = {
      // no-op
    }
  )
}

@Composable
private fun UpdateMediaViewerToolbarTitle(
  mediaViewerScreenState: MediaViewerScreen.MediaViewerScreenState,
  kurobaToolbarState: KurobaToolbarState,
  currentPagerPage: Int
) {
  val isLoaded = mediaViewerScreenState.isLoaded()
  if (!isLoaded) {
    return
  }

  val currentImageData = remember(key1 = currentPagerPage) {
    val images = mediaViewerScreenState.images
      ?: return@remember null

    return@remember images.getOrNull(currentPagerPage)?.postImage
  }

  LaunchedEffect(
    key1 = currentImageData,
    block = {
      if (currentImageData == null) {
        return@LaunchedEffect
      }

      Snapshot.withMutableSnapshot {
        val imagesCount = mediaViewerScreenState.images?.size

        kurobaToolbarState.toolbarTitleState.value = HtmlUnescape.unescape(currentImageData.originalFileNameEscaped)
        kurobaToolbarState.toolbarSubtitleState.value = formatImageInfo(
          currentPagerPage = currentPagerPage,
          imagesCount = imagesCount,
          currentImageData = currentImageData
        )
      }
    })
}

private fun formatImageInfo(
  currentPagerPage: Int,
  imagesCount: Int?,
  currentImageData: IPostImage
): String {
  return buildString {
    append(currentPagerPage + 1)
    append("/")
    append(imagesCount?.toString() ?: "?")

    append(", ")
    append(currentImageData.ext.uppercase(Locale.ENGLISH))

    append(", ")
    append(currentImageData.width)
    append("x")
    append(currentImageData.height)

    if (currentImageData.fileSize > 0) {
      append(", ")
      append(currentImageData.fileSize.asReadableFileSize())
    }
  }
}