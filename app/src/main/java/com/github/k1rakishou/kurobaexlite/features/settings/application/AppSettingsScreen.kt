package com.github.k1rakishou.kurobaexlite.features.settings.application

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCard
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.KurobaComposeFadeIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppSettingsScreen(
  defaultArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen<SimpleToolbar<AppSettingsScreen.ToolbarIcons>>(defaultArgs, componentActivity, navigationRouter) {
  private val appSettingsScreenViewModel by componentActivity.viewModel<AppSettingsScreenViewModel>()

  override val screenKey: ScreenKey = SCREEN_KEY
  override val hasFab: Boolean = false
  override val screenContentLoadedFlow: StateFlow<Boolean> = MutableStateFlow(true)

  private val defaultToolbarKey = "${screenKey.key}_toolbar"
  private val defaultToolbarStateKey = "${defaultToolbarKey}_state"

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .titleId(R.string.settings_screen_toolbar_title)
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

    GradientBackground(
      modifier = Modifier
        .fillMaxSize()
        .consumeClicks()
    ) {
      val currentScreen by appSettingsScreenViewModel.currentScreen.collectAsState()
      val settingScreenMut by appSettingsScreenViewModel.settingScreen(currentScreen).collectAsState()
      val settingScreen = settingScreenMut

      if (settingScreen == null) {
        KurobaComposeLoadingIndicator()
      } else {
        KurobaComposeFadeIn {
          SettingScreen(settingScreen)
        }
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

    KurobaComposeCard(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(vertical = 8.dp, horizontal = 4.dp)
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
            color = chanTheme.accentColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
          )
        }

        if (group.groupDescription != null) {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .padding(vertical = 4.dp, horizontal = 8.dp),
            text = group.groupDescription,
            color = chanTheme.textColorSecondary,
            fontSize = 14.sp
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

  enum class ToolbarIcons {
    Back,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("AppSettingsScreen")
  }

}