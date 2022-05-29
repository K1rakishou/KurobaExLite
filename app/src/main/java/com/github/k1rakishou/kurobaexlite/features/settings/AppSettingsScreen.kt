package com.github.k1rakishou.kurobaexlite.features.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingGroup
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingScreen
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppSettingsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen(componentActivity, navigationRouter) {
  private val appSettingsScreenViewModel by componentActivity.viewModel<AppSettingsScreenViewModel>()

  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false
  override val screenContentLoadedFlow: StateFlow<Boolean> = MutableStateFlow(true)

  private val defaultToolbarKey = "${screenKey.key}_toolbar"
  private val defaultToolbarStateKey = "${defaultToolbarKey}_state"

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcon>(componentActivity)
      .titleId(R.string.settings_screen_toolbar_title)
      .leftIcon(KurobaToolbarIcon(key = ToolbarIcon.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcon.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(defaultToolbarStateKey)
  }

  private val defaultToolbar by lazy {
    SimpleToolbar(
      toolbarKey = defaultToolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<SimpleToolbar<ToolbarIcon>>(screenKey)
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    LaunchedEffect(
      key1 = Unit,
      block = {
        defaultToolbarState.iconClickEvents.collect { icon ->
          when (icon) {
            ToolbarIcon.Back -> { onBackPressed() }
            ToolbarIcon.Overflow -> {
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

    LaunchedEffect(
      key1 = Unit,
      block = { kurobaToolbarContainerState.setToolbar(defaultToolbar) }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        appSettingsScreenViewModel.showMenuItemsFlow.collectLatest { displayedMenuOptions ->
          displayMenuOptionsAndWaitForResult(displayedMenuOptions)
        }
      }
    )

    HandleBackPresses {
      if (kurobaToolbarContainerState.onBackPressed()) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses popScreen()
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(chanTheme.backColorCompose)
        .consumeClicks()
    ) {
      val currentScreen by appSettingsScreenViewModel.currentScreen.collectAsState()
      val settingScreenMut by appSettingsScreenViewModel.settingScreen(currentScreen).collectAsState()
      val settingScreen = settingScreenMut

      if (settingScreen == null) {
        KurobaComposeLoadingIndicator()
      } else {
        SettingScreen(settingScreen)
      }
    }
  }

  @Composable
  private fun SettingScreen(settingScreen: SettingScreen) {
    val windowInsets = LocalWindowInsets.current
    val lazyListState = rememberLazyListState()
    val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

    val contentPadding = remember(key1 = windowInsets) {
      PaddingValues(
        top = windowInsets.top + toolbarHeight,
        bottom = windowInsets.bottom
      )
    }

    LazyColumnWithFastScroller(
      lazyListState = lazyListState,
      contentPadding = contentPadding,
      content = {
        val settingGroups = settingScreen.groups

        items(
          count = settingGroups.size,
          key = { index -> settingGroups[index].groupKey },
          itemContent = { index ->
            val group = settingGroups[index]

            SettingGroup(group)
          }
        )
      }
    )
  }

  @Composable
  private fun SettingGroup(group: SettingGroup) {
    val chanTheme = LocalChanTheme.current

    KurobaComposeCardView(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        if (group.groupName != null) {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .padding(vertical = 8.dp, horizontal = 8.dp),
            text = group.groupName,
            color = chanTheme.accentColorCompose,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
          )
        }

        for ((index, settingItem) in group.settingItems.withIndex()) {
          key(settingItem.key) {
            settingItem.Content()

            if (index < group.settingItems.lastIndex) {
              KurobaComposeDivider(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 4.dp)
              )
            }
          }
        }
      }
    }
  }

  private fun displayMenuOptionsAndWaitForResult(
    displayedMenuOptions: AppSettingsScreenViewModel.DisplayedMenuOptions
  ) {
    val floatingMenuScreen = FloatingMenuScreen(
      floatingMenuKey = FloatingMenuScreen.APP_SETTINGS_LIST_OPTION_MENU,
      componentActivity = componentActivity,
      navigationRouter = navigationRouter,
      menuItems = displayedMenuOptions.items,
      onMenuItemClicked = { floatingMenuItem ->
        displayedMenuOptions.result.complete(floatingMenuItem.menuItemKey as String)
      },
      onDismiss = {
        displayedMenuOptions.result.complete(null)
      }
    )

    navigationRouter.presentScreen(floatingMenuScreen)
  }

  enum class ToolbarIcon {
    Back,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("AppSettingsScreen")
  }

}