package com.github.k1rakishou.kurobaexlite.features.media.helpers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreenState
import com.github.k1rakishou.kurobaexlite.helpers.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.helpers.rememberViewModel
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerViewModel
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalComponentActivity
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import java.util.Locale

@OptIn(ExperimentalPagerApi::class)
@Composable
internal fun MediaViewerToolbar(
  toolbarHeight: Dp,
  backgroundColor: Color,
  screenKey: ScreenKey,
  mediaViewerScreenState: MediaViewerScreenState,
  pagerState: PagerState?,
  onDownloadMediaClicked: (IPostImage) -> Unit,
  onMinimizeClicked: () -> Unit,
  onBackPressed: () -> Unit
) {
  if (!mediaViewerScreenState.isLoaded() || pagerState == null) {
    return
  }

  val insets = LocalWindowInsets.current
  val componentActivity = LocalComponentActivity.current
  val kurobaToolbarContainerViewModel = componentActivity.rememberViewModel<KurobaToolbarContainerViewModel>()

  val toolbarTotalHeight = remember(key1 = insets.top) { insets.top + toolbarHeight }
  val currentImageIndex by remember { derivedStateOf { pagerState.currentPage } }
  val targetImageIndex by remember { derivedStateOf { pagerState.targetPage } }

  val kurobaToolbarContainerState = remember {
    kurobaToolbarContainerViewModel.getOrCreate<SimpleToolbar<ToolbarIcons>>(screenKey)
  }

  val childToolbars = remember(key1 = currentImageIndex, key2 = targetImageIndex) {
    val childToolbars = mutableListOf<ChildToolbar>()

    val currentToolbarKey = "MediaViewerToolbar_${currentImageIndex}"
    childToolbars += ChildToolbar(
      key = currentToolbarKey,
      indexInList = currentImageIndex,
      content = {
        MediaToolbar(
          toolbarKey = currentToolbarKey,
          kurobaToolbarContainerState = kurobaToolbarContainerState,
          mediaViewerScreenState = mediaViewerScreenState,
          currentPagerPage = currentImageIndex,
          onDownloadMediaClicked = onDownloadMediaClicked,
          onMinimizeClicked = onMinimizeClicked,
          onBackPressed = onBackPressed
        )
      })

    if (currentImageIndex != targetImageIndex) {
      val targetToolbarKey = "MediaViewerToolbar_${targetImageIndex}"
      childToolbars += ChildToolbar(
        key = targetToolbarKey,
        indexInList = targetImageIndex,
        content = {
          MediaToolbar(
            toolbarKey = targetToolbarKey,
            kurobaToolbarContainerState = kurobaToolbarContainerState,
            mediaViewerScreenState = mediaViewerScreenState,
            currentPagerPage = targetImageIndex,
            onDownloadMediaClicked = onDownloadMediaClicked,
            onMinimizeClicked = onMinimizeClicked,
            onBackPressed = onBackPressed
          )
        }
      )
    }

    return@remember childToolbars
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(toolbarTotalHeight)
      .consumeClicks()
  ) {
    Spacer(modifier = Modifier.height(insets.top))

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(toolbarHeight)
    ) {
      KurobaToolbarContainer(
        toolbarContainerKey = screenKey.key,
        backgroundColor = backgroundColor,
        kurobaToolbarContainerState = kurobaToolbarContainerState,
        canProcessBackEvent = { true },
      )

      childToolbars.fastForEach { childToolbar ->
        key(childToolbar.key) {
          childToolbar.content(this)
        }
      }
    }
  }
}

@Composable
private fun MediaToolbar(
  toolbarKey: String,
  kurobaToolbarContainerState: KurobaToolbarContainerState<SimpleToolbar<ToolbarIcons>>,
  mediaViewerScreenState: MediaViewerScreenState,
  currentPagerPage: Int,
  onDownloadMediaClicked: (IPostImage) -> Unit,
  onMinimizeClicked: () -> Unit,
  onBackPressed: () -> Unit,
) {
  val componentActivity = LocalComponentActivity.current

  val defaultToolbarState = remember(key1 = toolbarKey) {
    return@remember SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .leftIcon(KurobaToolbarIcon(key = ToolbarIcons.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.DownloadMedia, drawableId = R.drawable.ic_baseline_download_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.Minimize, drawableId = R.drawable.ic_baseline_fullscreen_exit_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(toolbarKey)
  }

  val defaultToolbar = remember(key1 = toolbarKey) {
    SimpleToolbar(
      toolbarKey = toolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  LaunchedEffect(
    key1 = Unit,
    block = {
      defaultToolbarState.iconClickEvents.collect { key ->
        when (key) {
          ToolbarIcons.Back -> {
            onBackPressed()
          }
          ToolbarIcons.DownloadMedia -> {
            mediaViewerScreenState.images
              .takeIf { it.isNotEmpty() }
              ?.let { images ->
                val postImageToDownload = images.getOrNull(currentPagerPage)?.postImage
                  ?: return@let

                onDownloadMediaClicked(postImageToDownload)
              }
          }
          ToolbarIcons.Minimize -> {
            onMinimizeClicked()
          }
          ToolbarIcons.Overflow -> {
            // no-op
          }
        }
      }
    }
  )

  DisposableEffect(
    key1 = Unit,
    effect = {
      kurobaToolbarContainerState.setDefaultToolbar(childToolbar = defaultToolbar)

      onDispose {
        kurobaToolbarContainerState.removeToolbar(
          expectedKey = defaultToolbar.toolbarKey,
          withAnimation = false
        )
      }
    }
  )

  UpdateMediaViewerToolbarTitle(
    mediaViewerScreenState = mediaViewerScreenState,
    toolbarState = defaultToolbarState,
    currentPagerPage = currentPagerPage
  )
}

@Composable
private fun UpdateMediaViewerToolbarTitle(
  mediaViewerScreenState: MediaViewerScreenState,
  toolbarState: SimpleToolbarState<ToolbarIcons>,
  currentPagerPage: Int
) {
  val isLoaded = mediaViewerScreenState.isLoaded()
  if (!isLoaded) {
    return
  }

  val currentImageData = remember(key1 = currentPagerPage) {
    val images = mediaViewerScreenState.images

    return@remember images.getOrNull(currentPagerPage)?.postImage
  }

  LaunchedEffect(
    key1 = currentImageData,
    block = {
      if (currentImageData == null) {
        return@LaunchedEffect
      }

      val imagesCount = mediaViewerScreenState.images.size

      toolbarState.toolbarTitleState.value = HtmlUnescape.unescape(currentImageData.originalFileNameEscaped)
      toolbarState.toolbarSubtitleState.value = formatImageInfo(
        currentPagerPage = currentPagerPage,
        imagesCount = imagesCount,
        currentImageData = currentImageData
      )
    }
  )
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

private class ChildToolbar(
  val key: Any,
  val indexInList: Int,
  val content: @Composable BoxScope.() -> Unit
)

private enum class ToolbarIcons {
  Back,
  DownloadMedia,
  Minimize,
  Overflow
}