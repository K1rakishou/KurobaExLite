package com.github.k1rakishou.kurobaexlite.features.posts.search.image

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.firewall.BypassResult
import com.github.k1rakishou.kurobaexlite.features.firewall.SiteFirewallBypassScreen
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.helpers.util.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import com.github.k1rakishou.kurobaexlite.model.FirewallType
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeError
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyVerticalGridWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.logcat
import okhttp3.HttpUrl
import org.koin.androidx.viewmodel.ext.android.viewModel

class RemoteImageSearchScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen<SimpleToolbar<RemoteImageSearchScreen.DefaultToolbarIcon>>(
  screenArgs,
  componentActivity,
  navigationRouter
) {
  private val remoteImageSearchScreenViewModel: RemoteImageSearchScreenViewModel by componentActivity.viewModel()

  private val defaultToolbarKey by lazy { "${screenKey.key}_toolbar" }
  private val defaultToolbarStateKey by lazy { "${defaultToolbarKey}_state" }

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<DefaultToolbarIcon>(componentActivity)
      .titleId(R.string.remote_image_search_screen_title)
      .leftIcon(KurobaToolbarIcon(key = DefaultToolbarIcon.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = DefaultToolbarIcon.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(defaultToolbarStateKey)
  }

  override val screenContentLoadedFlow: StateFlow<Boolean> by lazy { MutableStateFlow(true) }
  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false

  override val defaultToolbar by lazy {
    SimpleToolbar<DefaultToolbarIcon>(
      toolbarKey = defaultToolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<SimpleToolbar<DefaultToolbarIcon>>(screenKey)
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    LaunchedEffect(
      key1 = Unit,
      block = {
        defaultToolbarState.iconClickEvents.collect { icon ->
          when (icon) {
            DefaultToolbarIcon.Back -> { onBackPressed() }
            DefaultToolbarIcon.Overflow -> {
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

    GradientBackground(
      modifier = Modifier.fillMaxSize()
    ) {
      ContentInternal()
    }
  }

  @Composable
  private fun ContentInternal() {
    val windowInsets = LocalWindowInsets.current
    val chanTheme = LocalChanTheme.current

    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val topPadding = toolbarHeight + windowInsets.top

    val lastUsedSearchInstanceMut by remoteImageSearchScreenViewModel.lastUsedSearchInstance
    val lastUsedSearchInstance = lastUsedSearchInstanceMut
    if (lastUsedSearchInstance == null) {
      return
    }

    val searchInstanceMut = remoteImageSearchScreenViewModel.searchInstances[lastUsedSearchInstance]
    val searchInstance = searchInstanceMut
    if (searchInstance == null) {
      return
    }

    var searchQuery by remoteImageSearchScreenViewModel.searchQuery

    ListenForFirewallBypassRequests()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 8.dp)
    ) {
      Spacer(modifier = Modifier.height(topPadding))

      SearchInstanceSelector(
        searchInstance = searchInstance,
        onSelectorItemClicked = {
          // TODO(KurobaEx): no-op for now
        }
      )

      Spacer(modifier = Modifier.height(8.dp))

      KurobaComposeTextField(
        value = searchQuery,
        modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth(),
        onValueChange = { newValue ->
          searchQuery = newValue
          remoteImageSearchScreenViewModel.onSearchQueryChanged(newValue)
        },
        singleLine = true,
        maxLines = 1,
        label = {
          KurobaComposeText(
            text = stringResource(id = R.string.type_to_search_hint),
            color = chanTheme.textColorHint
          )
        }
      )

      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        BuildImageSearchResults(
          lastUsedSearchInstance = lastUsedSearchInstance,
          onImageClicked = { imageSearchResult ->
            if (imageSearchResult.fullImageUrls.isEmpty()) {
              return@BuildImageSearchResults
            }

            if (imageSearchResult.fullImageUrls.size == 1) {
              ScreenCallbackStorage.invokeCallback(
                screenKey = screenKey,
                callbackKey = ON_IMAGE_SELECTED,
                p1 = imageSearchResult.fullImageUrls.first().toString()
              )

              popScreen()

              return@BuildImageSearchResults
            }

            showOptions(imageSearchResult.fullImageUrls)
          }
        )
      }
    }
  }

  @Composable
  private fun ListenForFirewallBypassRequests() {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(
      key1 = Unit,
      block = {
        coroutineScope.launch {
          remoteImageSearchScreenViewModel.solvingCaptcha.collect { urlToOpen ->
            if (urlToOpen == null) {
              return@collect
            }

            val alreadyPresenting = navigationRouter.getScreenByKey(SiteFirewallBypassScreen.SCREEN_KEY) != null
            if (alreadyPresenting) {
              return@collect
            }

            try {
              logcat(TAG) { "Launching SiteFirewallBypassScreen" }

              val bypassResult = suspendCancellableCoroutine<BypassResult> { continuation ->
                val siteFirewallBypassScreen = createScreen<SiteFirewallBypassScreen>(
                  componentActivity = componentActivity,
                  navigationRouter = navigationRouter,
                  args = {
                    putSerializable(
                      SiteFirewallBypassScreen.FIREWALL_TYPE,
                      FirewallType.YandexSmartCaptcha
                    )
                    putSerializable(SiteFirewallBypassScreen.URL_TO_OPEN, urlToOpen)
                  },
                  callbacks = {
                    callback<BypassResult>(
                      callbackKey = SiteFirewallBypassScreen.ON_RESULT,
                      func = { bypassResult -> continuation.resumeSafe(bypassResult) }
                    )
                  }
                )

                navigationRouter.presentScreen(siteFirewallBypassScreen)
              }

              logcat(TAG) { "SiteFirewallBypassScreen finished" }

              // Wait a second for the controller to get closed so that we don't end up in a loop
              delay(1000)

              if (bypassResult !is BypassResult.Cookie) {
                logcatError(TAG) { "Failed to bypass YandexSmartCaptcha, bypassResult: ${bypassResult}" }
                remoteImageSearchScreenViewModel.reloadCurrentPage()
                return@collect
              }

              logcat(TAG) { "Got YandexSmartCaptcha cookies, cookieResult: ${bypassResult}" }
              remoteImageSearchScreenViewModel.updateYandexSmartCaptchaCookies(bypassResult.cookie)
              remoteImageSearchScreenViewModel.reloadCurrentPage()
            } finally {
              remoteImageSearchScreenViewModel.finishedSolvingCaptcha()
            }
          }
        }
      })
  }

  @Composable
  private fun SearchInstanceSelector(
    searchInstance: ImageSearchInstance,
    onSelectorItemClicked: () -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    KurobaComposeText(
      text = stringResource(id = R.string.remote_image_search_screen_current_instance),
      fontSize = 12.sp,
      color = chanTheme.textColorHint
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .kurobaClickable(
          bounded = true,
          onClick = { onSelectorItemClicked() }
        )
        .padding(vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Image(
        modifier = Modifier.size(24.dp),
        painter = painterResource(id = searchInstance.icon),
        contentDescription = null
      )

      Spacer(modifier = Modifier.width(12.dp))

      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        text = searchInstance.type.name
      )
    }
  }

  @Composable
  private fun BuildImageSearchResults(
    lastUsedSearchInstance: ImageSearchInstanceType,
    onImageClicked: (ImageSearchResultUi) -> Unit
  ) {
    val windowInsets = LocalWindowInsets.current
    val paddingValues = remember(key1 = windowInsets) {
      windowInsets.copyInsets(
        newLeft = 0.dp,
        newRight = 0.dp,
        newTop = 0.dp
      ).asPaddingValues()
    }

    val searchInstance = remoteImageSearchScreenViewModel.searchInstances[lastUsedSearchInstance]
      ?: return
    val searchResults = remoteImageSearchScreenViewModel.searchResults[lastUsedSearchInstance]
      ?: return

    val imageSearchResults = when (val result = searchResults) {
      AsyncData.Uninitialized -> {
        return
      }
      AsyncData.Loading -> {
        KurobaComposeLoadingIndicator(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = windowInsets.bottom)
        )

        return
      }
      is AsyncData.Error -> {
        KurobaComposeError(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = windowInsets.bottom),
          errorMessage = result.error.errorMessageOrClassName(userReadable = true)
        )

        return
      }
      is AsyncData.Data -> result.data
    }

    val lazyGridState = rememberLazyGridState(
      initialFirstVisibleItemIndex = searchInstance.rememberedFirstVisibleItemIndex,
      initialFirstVisibleItemScrollOffset = searchInstance.rememberedFirstVisibleItemScrollOffset
    )

    DisposableEffect(
      key1 = Unit,
      effect = {
        onDispose {
          remoteImageSearchScreenViewModel.updatePrevLazyListState(
            firstVisibleItemIndex = lazyGridState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = lazyGridState.firstVisibleItemScrollOffset
          )
        }
      }
    )

    LazyVerticalGridWithFastScroller(
      modifier = Modifier.fillMaxSize(),
      lazyGridState = lazyGridState,
      columns = GridCells.Adaptive(minSize = IMAGE_SIZE),
      contentPadding = paddingValues
    ) {
      val images = imageSearchResults.results

      items(
        count = images.size,
        contentType = { "image_item" }
      ) { index ->
        val imageSearchResult = images.get(index)

        BuildImageSearchResult(
          imageSearchResult = imageSearchResult,
          onImageClicked = onImageClicked
        )
      }

      if (!imageSearchResults.endReached) {
        item(
          span = { GridItemSpan(maxLineSpan) },
          contentType = { "loading_indicator" }
        ) {
          Box(
            modifier = Modifier.size(IMAGE_SIZE)
          ) {
            KurobaComposeLoadingIndicator(
              modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .align(Alignment.Center)
            )
          }

          LaunchedEffect(key1 = images.lastIndex) {
            remoteImageSearchScreenViewModel.onNewPageRequested(page = searchInstance.currentPage + 1)
          }
        }
      } else {
        item(
          span = { GridItemSpan(maxLineSpan) },
          contentType = { "end_reached_indicator" }
        ) {
          KurobaComposeText(
            modifier = Modifier
              .wrapContentSize()
              .padding(horizontal = 32.dp, vertical = 16.dp),
            text = "End reached"
          )
        }
      }
    }
  }

  @Composable
  private fun BuildImageSearchResult(
    imageSearchResult: ImageSearchResultUi,
    onImageClicked: (ImageSearchResultUi) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val context = LocalContext.current

    val request = remember {
      ImageRequest.Builder(context).data(imageSearchResult.thumbnailUrl).build()
    }

    val imageInfo = remember(key1 = imageSearchResult) {
      if (!imageSearchResult.hasImageInfo()) {
        return@remember null
      }

      return@remember buildString {
        if (imageSearchResult.extension.isNotNullNorEmpty()) {
          append(imageSearchResult.extension.uppercase())
        }

        if (imageSearchResult.width != null && imageSearchResult.height != null) {
          if (length > 0) {
            append(" ")
          }

          append(imageSearchResult.width)
          append("x")
          append(imageSearchResult.height)
        }

        if (imageSearchResult.sizeInByte != null) {
          if (length > 0) {
            append(" ")
          }

          append(imageSearchResult.sizeInByte.asReadableFileSize())
        }
      }
    }

    val bgColor = remember { Color.Black.copy(alpha = 0.6f) }

    Box(
      modifier = Modifier
        .size(IMAGE_SIZE)
        .padding(4.dp)
        .background(chanTheme.backColorSecondary)
        .clickable { onImageClicked(imageSearchResult) }
    ) {
      // TODO(KurobaEx): extract into a separate image
      AsyncImage(
        modifier = Modifier.fillMaxSize(),
        model = request,
        contentDescription = null
      )

      if (imageInfo != null) {
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(bgColor)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .align(Alignment.BottomEnd),
          text = imageInfo,
          fontSize = 11.sp,
          color = Color.White
        )
      }
    }
  }

  private fun showOptions(fullImageUrls: List<HttpUrl>) {
    val menuItems = mutableListOf<FloatingMenuItem>()

    menuItems += FloatingMenuItem.TextHeader(
      text = FloatingMenuItem.MenuItemText.Id(R.string.remote_image_search_screen_select_source)
    )

    fullImageUrls.forEach { httpUrl ->
      val httpUrlAsString = httpUrl.toString()

      menuItems += FloatingMenuItem.Text(
        menuItemKey = httpUrlAsString,
        menuItemData = httpUrlAsString,
        text = FloatingMenuItem.MenuItemText.String(httpUrlAsString)
      )
    }

    val floatingMenuScreen = FloatingMenuScreen(
      floatingMenuKey = FloatingMenuScreen.REMOTE_IMAGE_SEARCH_OPTIONS_MENUS,
      componentActivity = componentActivity,
      navigationRouter = navigationRouter,
      menuItems = menuItems,
      onMenuItemClicked = { clickedItem ->
        val clickedItemUrl = (clickedItem.data as? String)
          ?: return@FloatingMenuScreen

        ScreenCallbackStorage.invokeCallback(
          screenKey = screenKey,
          callbackKey = ON_IMAGE_SELECTED,
          p1 = clickedItemUrl
        )

        popScreen()
      }
    )

    navigationRouter.presentScreen(floatingMenuScreen)
  }

  enum class DefaultToolbarIcon {
    Back,
    Overflow
  }

  companion object {
    private const val TAG = "RemoteImageSearchScreen"
    val SCREEN_KEY = ScreenKey("RemoteImageSearchScreen")

    const val ON_IMAGE_SELECTED = "on_image_selected"

    private val IMAGE_SIZE = 128.dp
  }

}