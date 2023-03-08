package com.github.k1rakishou.kurobaexlite.features.downloads

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.ParsedPostDataRepository
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeMessage
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.KurobaComposeFadeIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DownloadsScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen<SimpleToolbar<DownloadsScreen.ToolbarIcons>>(screenArgs, componentActivity, navigationRouter) {

  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false
  override val screenContentLoadedFlow: StateFlow<Boolean> = MutableStateFlow(true)

  private val defaultToolbarKey = "${screenKey.key}_toolbar"
  private val defaultToolbarStateKey = "${defaultToolbarKey}_state"

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .titleId(R.string.downloads_screen_toolbar_title)
      .leftIcon(KurobaToolbarIcon(key = ToolbarIcons.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(defaultToolbarStateKey)
  }

  override val defaultToolbar by lazy {
    SimpleToolbar(
      toolbarKey = defaultToolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<SimpleToolbar<ToolbarIcons>>(screenKey)
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    LaunchedEffect(
      key1 = Unit,
      block = {
        defaultToolbarState.iconClickEvents.collect { icon ->
          when (icon) {
            ToolbarIcons.Back -> { onBackPressed() }
            ToolbarIcons.Overflow -> {
              // no-op
            }
          }
        }
      }
    )

    KurobaToolbarContainer(
      toolbarContainerKey = screenKey.key,
      kurobaToolbarContainerState = kurobaToolbarContainerState,
      canProcessBackEvent = { true }
    )
  }

  @Composable
  override fun HomeNavigationScreenContent() {
    HandleBackPresses {
      if (kurobaToolbarContainerState.onBackPressed()) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses popScreen()
    }

    LaunchedEffect(
      key1 = Unit,
      block = {
        snackbarManager.popSnackbar(SnackbarId.ReloadLastVisitedCatalog)
        snackbarManager.popSnackbar(SnackbarId.ReloadLastVisitedThread)
      }
    )

    ContentInternal()
  }

  @Composable
  private fun ContentInternal(
  ) {
    val downloadsScreenViewModel = koinRememberViewModel<DownloadsScreenViewModel>()

    GradientBackground(
      modifier = Modifier
        .fillMaxSize()
        .consumeClicks()
    ) {
      KurobaComposeFadeIn {
        ActiveDownloadsList(
          cancelDownload = { uuid -> cancelDownloadWithDialog(downloadsScreenViewModel, uuid) }
        )
      }
    }
  }

  private fun cancelDownloadWithDialog(
    downloadsScreenViewModel: DownloadsScreenViewModel,
    uuid: String
  ) {
    navigationRouter.presentScreen(
      DialogScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        params = DialogScreen.Params(
          title = DialogScreen.Text.Id(R.string.downloads_screen_cancel_download_title),
          negativeButton = DialogScreen.DialogButton(
            buttonText = R.string.close
          ),
          positiveButton = DialogScreen.PositiveDialogButton(
            buttonText = R.string.cancel,
            isActionDangerous = true,
            onClick = { downloadsScreenViewModel.cancelDownload(uuid) }
          )
        )
      )
    )
  }

  @Composable
  private fun ActiveDownloadsList(cancelDownload: (String) -> Unit) {
    val windowInsets = LocalWindowInsets.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

    val contentPadding = remember(key1 = windowInsets) {
      PaddingValues(
        top = windowInsets.top + toolbarHeight,
        bottom = windowInsets.bottom
      )
    }

    val downloadsScreenViewModel = koinRememberViewModel<DownloadsScreenViewModel>()
    val activeDownloads = downloadsScreenViewModel.activeDownloads

    val lazyListState = rememberLazyListState()

    LazyColumnWithFastScroller(
      lazyListState = lazyListState,
      contentPadding = contentPadding,
      content = {
        if (activeDownloads.isEmpty()) {
          item {
            KurobaComposeMessage(
              message = stringResource(id = R.string.downloads_screen_no_active_downloads)
            )
          }

          return@LazyColumnWithFastScroller
        }

        items(
          count = activeDownloads.size,
          key = { index -> activeDownloads[index].uuid },
          itemContent = { index ->
            val activeDownload = activeDownloads[index]

            ActiveDownload(activeDownload, cancelDownload)
          }
        )
      }
    )
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun LazyItemScope.ActiveDownload(
    activeDownloadUi: DownloadsScreenViewModel.ActiveDownloadUi,
    cancelDownload: (String) -> Unit
  ) {
    val chanPostCache = koinRemember<IChanPostCache>()
    val parsedPostDataRepository = koinRemember<ParsedPostDataRepository>()

    Row(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .animateItemPlacement()
    ) {
      val downloaded by activeDownloadUi.downloaded
      val failed by activeDownloadUi.failed
      val total by activeDownloadUi.total

      val progress = remember(
        downloaded,
        failed,
        total
      ) {
        return@remember (downloaded.toFloat() + failed.toFloat()) / total.toFloat()
      }

      var activeDownloadTitleMut by remember { mutableStateOf<String?>(null) }
      val activeDownloadTitle = activeDownloadTitleMut

      LaunchedEffect(
        key1 = activeDownloadUi.chanDescriptor,
        block = {
          val chanDescriptor = activeDownloadUi.chanDescriptor

          activeDownloadTitleMut = buildActiveDownloadTitle(
            chanDescriptor = chanDescriptor,
            chanPostCache = chanPostCache,
            parsedPostDataRepository = parsedPostDataRepository
          )
        }
      )

      KurobaComposeLoadingIndicator(
        modifier = Modifier
          .size(32.dp)
          .align(Alignment.CenterVertically),
        progress = progress
      )

      Spacer(modifier = Modifier.width(8.dp))

      Column(
        modifier = Modifier
          .weight(1f)
          .wrapContentHeight()
          .align(Alignment.CenterVertically)
      ) {
        if (activeDownloadTitle.isNotNullNorEmpty()) {
          KurobaComposeText(text = activeDownloadTitle)
        }

        KurobaComposeText(text = stringResource(id = R.string.downloads_screen_downloaded_and_failed, downloaded, failed))
        KurobaComposeText(text = stringResource(id = R.string.downloads_screen_total, total))
      }

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .align(Alignment.CenterVertically)
          .kurobaClickable(bounded = false, onClick = { cancelDownload(activeDownloadUi.uuid) }),
        drawableId = R.drawable.ic_baseline_clear_24
      )
    }
  }

  private suspend fun buildActiveDownloadTitle(
    chanDescriptor: ChanDescriptor,
    chanPostCache: IChanPostCache,
    parsedPostDataRepository: ParsedPostDataRepository
  ): String {
    return buildString {
      when (chanDescriptor) {
        is CatalogDescriptor -> {
          val text = appResources.string(
            R.string.downloads_screen_active_download_catalog_prefix,
            "${chanDescriptor.siteKeyActual}/${chanDescriptor.boardCode}"
          )

          append(text)
        }
        is ThreadDescriptor -> {
          var threadTitle: String? = null

          val postDescriptor = chanPostCache.getOriginalPost(chanDescriptor)?.postDescriptor
          if (postDescriptor != null) {
            threadTitle = parsedPostDataRepository.formatThreadToolbarTitle(postDescriptor)
          }

          if (threadTitle.isNullOrEmpty()) {
            val text = appResources.string(
              R.string.downloads_screen_active_download_thread_prefix,
              "${chanDescriptor.siteKeyActual}/${chanDescriptor.boardCode}/${chanDescriptor.threadNo}"
            )

            append(text)
          } else {
            append(appResources.string(R.string.downloads_screen_active_download_thread_prefix, threadTitle))
          }
        }
      }
    }
  }

  enum class ToolbarIcons {
    Back,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("DownloadsScreen")
  }

}