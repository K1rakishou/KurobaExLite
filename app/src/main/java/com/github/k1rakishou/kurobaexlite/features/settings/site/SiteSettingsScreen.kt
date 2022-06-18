package com.github.k1rakishou.kurobaexlite.features.settings.site

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingItem
import com.github.k1rakishou.kurobaexlite.helpers.resumeSafe
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.KurobaComposeFadeIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.java.KoinJavaComponent.inject

class SiteSettingsScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
) : HomeNavigationScreen<KurobaChildToolbar>(screenArgs, componentActivity, navigationRouter) {
  private val siteManager: SiteManager by inject(SiteManager::class.java)

  override val screenContentLoadedFlow: StateFlow<Boolean> = MutableStateFlow(true)
  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false

  private val siteKey: SiteKey by requireArgument(SITE_KEY_ARG)
  private val defaultToolbarKey = "${screenKey.key}_default"
  private val defaultToolbarStateKey = "${defaultToolbarKey}_state"

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .leftIcon(KurobaToolbarIcon(key = ToolbarIcons.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(defaultToolbarStateKey)
  }

  override val defaultToolbar: KurobaChildToolbar by lazy {
    SimpleToolbar(
      toolbarKey = defaultToolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<KurobaChildToolbar>(screenKey)
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
    val chanTheme = LocalChanTheme.current
    val context = LocalContext.current

    HandleBackPresses {
      if (kurobaToolbarContainerState.onBackPressed()) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses popScreen()
    }

    LaunchedEffect(
      key1 = Unit,
      block = {
        val siteName = siteManager.bySiteKey(siteKey)?.readableName
        if (siteName == null) {
          snackbarManager.errorToast(
            message = context.resources.getString(R.string.site_settings_site_not_supported, siteKey.key),
            screenKey = MainScreen.SCREEN_KEY
          )

          return@LaunchedEffect
        }

        defaultToolbarState.toolbarTitleState.value = context.resources.getString(
          R.string.site_settings_screen_toolbar_title,
          siteName
        )

        kurobaToolbarContainerState.setToolbar(defaultToolbar)
      }
    )

    val siteSettingItems = remember { mutableStateListOf<SettingItem>() }
    LaunchedEffect(
      key1 = siteKey,
      block = {
        val uiSettingItems = siteManager.bySiteKey(siteKey)
          ?.siteSettings
          ?.uiSettingItems(showDialogScreen = { params -> showDialogScreen(params) })
          ?: return@LaunchedEffect

        siteSettingItems.clear()
        siteSettingItems.addAll(uiSettingItems)
      }
    )

    if (siteSettingItems.isEmpty()) {
      return
    }

    val windowInsets = LocalWindowInsets.current
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
    val paddingValues = remember(key1 = windowInsets) {
      windowInsets.copy(
        newLeft = 0.dp,
        newRight = 0.dp,
        newTop = windowInsets.top + toolbarHeight
      ).asPaddingValues()
    }

    val lazyListState = rememberLazyListState()

    GradientBackground(
      modifier = Modifier
        .consumeClicks()
    ) {
      KurobaComposeFadeIn {
        LazyColumnWithFastScroller(
          lazyListContainerModifier = Modifier.fillMaxSize(),
          contentPadding = paddingValues,
          lazyListState = lazyListState,
          content = {
            items(
              count = siteSettingItems.size,
              key = { index -> siteSettingItems[index].key },
              contentType = { index -> siteSettingItems[index].javaClass.name },
              itemContent = { index ->
                val siteSettingItem = siteSettingItems[index]
                siteSettingItem.Content()

                if (index < siteSettingItems.lastIndex) {
                  KurobaComposeDivider(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 4.dp)
                  )
                }
              }
            )
          }
        )
      }
    }
  }

  private suspend fun showDialogScreen(params: DialogScreen.Params) {
    return suspendCancellableCoroutine<Unit> { cancellableContinuation ->
      val dialogScreen = DialogScreen(
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        params = params,
        onDismissed = { cancellableContinuation.resumeSafe(Unit) }
      )

      navigationRouter.presentScreen(dialogScreen)
    }
  }

  enum class ToolbarIcons {
    Back,
    Overflow
  }

  companion object {
    const val SITE_KEY_ARG = "site_key"

    val SCREEN_KEY = ScreenKey("SiteSettingsScreen")
  }

}