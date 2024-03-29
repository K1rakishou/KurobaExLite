package com.github.k1rakishou.kurobaexlite.features.media.helpers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
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
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.helpers.util.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainerViewModel
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarState
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalComponentActivity
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaViewerToolbar(
  toolbarHeight: Dp,
  backgroundColor: Color,
  screenKey: ScreenKey,
  mediaViewerScreenState: MediaViewerScreenState,
  pagerState: PagerState?,
  onDownloadMediaClicked: (IPostImage) -> Unit,
  onBackPressed: () -> Unit
) {
  if (!mediaViewerScreenState.isLoaded() || pagerState == null) {
    return
  }

  val insets = LocalWindowInsets.current
  val kurobaToolbarContainerViewModel = koinRememberViewModel<KurobaToolbarContainerViewModel>()

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
  onBackPressed: () -> Unit,
) {
  val componentActivity = LocalComponentActivity.current

  val defaultToolbarState = remember(key1 = toolbarKey) {
    return@remember SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .leftIcon(KurobaToolbarIcon(key = ToolbarIcons.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.DownloadMedia, drawableId = R.drawable.ic_baseline_download_24))
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
            mediaViewerScreenState.mediaList
              .takeIf { it.isNotEmpty() }
              ?.let { images ->
                val postImageToDownload = images.getOrNull(currentPagerPage)?.postImage
                  ?: return@let

                defaultToolbarState.findIconByKey(ToolbarIcons.DownloadMedia)
                  ?.let { downloadMediaIcon -> downloadMediaIcon.enabled.value = false }

                onDownloadMediaClicked(postImageToDownload)
              }
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
    val images = mediaViewerScreenState.mediaList

    return@remember images.getOrNull(currentPagerPage)?.postImage
  }

  LaunchedEffect(
    key1 = currentImageData,
    block = {
      if (currentImageData == null) {
        return@LaunchedEffect
      }

      updateToolbarTitleAndSubtitle(
        mediaViewerScreenState = mediaViewerScreenState,
        toolbarState = toolbarState,
        currentImageData = currentImageData,
        currentPagerPage = currentPagerPage
      )
    }
  )

  LaunchedEffect(
    key1 = currentImageData,
    block = {
      mediaViewerScreenState.newImagesAddedFlow.collect {
        if (currentImageData == null) {
          return@collect
        }

        updateToolbarTitleAndSubtitle(
          mediaViewerScreenState = mediaViewerScreenState,
          toolbarState = toolbarState,
          currentImageData = currentImageData,
          currentPagerPage = currentPagerPage
        )
      }
    }
  )
}

private fun updateToolbarTitleAndSubtitle(
  mediaViewerScreenState: MediaViewerScreenState,
  toolbarState: SimpleToolbarState<ToolbarIcons>,
  currentImageData: IPostImage,
  currentPagerPage: Int
) {
  val imagesCount = mediaViewerScreenState.mediaList.size

  toolbarState.toolbarTitleState.value = HtmlUnescape.unescape(currentImageData.originalFileNameEscaped)
  toolbarState.toolbarSubtitleState.value = formatImageInfo(
    currentPagerPage = currentPagerPage,
    imagesCount = imagesCount,
    currentImageData = currentImageData
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

    append(AppConstants.TEXT_SEPARATOR)
    append(currentImageData.ext.uppercase(Locale.ENGLISH))

    append(AppConstants.TEXT_SEPARATOR)
    append(currentImageData.width)
    append("x")
    append(currentImageData.height)

    if (currentImageData.fileSize > 0) {
      append(AppConstants.TEXT_SEPARATOR)
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
  Overflow
}