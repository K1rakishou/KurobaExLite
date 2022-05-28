package com.github.k1rakishou.kurobaexlite.features.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.InsetsAwareBox
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppSettingsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen(componentActivity, navigationRouter) {

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
      InsetsAwareBox(modifier = Modifier.fillMaxSize()) {
        KurobaComposeText(text = "AppSettingsScreen")
      }
    }
  }

  enum class ToolbarIcon {
    Back,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("AppSettingsScreen")
  }

}