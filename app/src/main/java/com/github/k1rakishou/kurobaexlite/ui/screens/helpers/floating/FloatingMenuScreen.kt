package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey

class FloatingMenuScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val menuItems: List<ToolbarMenuItem>,
  private val onMenuItemClicked: (ToolbarMenuItem) -> Unit
) : FloatingComposeScreen(componentActivity, navigationRouter) {

  private val lastTouchPosition = uiInfoManager.lastTouchPosition
  private val customAlignment by lazy {
    return@lazy Alignment { size, space, _ ->
      val availableWidth = uiInfoManager.maxParentWidth
      val availableHeight = uiInfoManager.maxParentHeight

      val biasX = (lastTouchPosition.x.toFloat() / availableWidth.toFloat()).coerceIn(0f, 1f)
      val biasY = (lastTouchPosition.y.toFloat() / availableHeight.toFloat()).coerceIn(0f, 1f)

      val horizPadding = horizPaddingPx.toInt()
      val vertPadding = vertPaddingPx.toInt()

      val offsetX = ((space.width - size.width).toFloat() * biasX).toInt()
        .coerceIn(horizPadding, availableWidth - horizPadding)
      val offsetY = ((space.height - size.height).toFloat() * biasY).toInt()
        .coerceIn(vertPadding, availableHeight - vertPadding)

      return@Alignment IntOffset(x = offsetX, y = offsetY)
    }
  }

  override val screenKey: ScreenKey = SCREEN_KEY
  override val contentAlignment: Alignment = customAlignment

  @Composable
  override fun FloatingContent() {
    val availableWidth = with(LocalDensity.current) {
      if (uiInfoManager.isPortraitOrientation) {
        (maxAvailableWidthPx() / 1.5f).toDp()
      } else {
        (maxAvailableWidthPx() / 2f).toDp()
      }
    }

    LazyColumn(
      modifier = Modifier.width(availableWidth),
      content = {
        items(
          count = menuItems.size,
          key = { index -> menuItems.get(index).menuItemId }
        ) { index ->
          val menuItem = menuItems.get(index)

          Column(
            modifier = Modifier
              .wrapContentHeight()
              .fillMaxWidth()
              .kurobaClickable(
                onClick = {
                  onMenuItemClicked(menuItem)
                  stopPresenting()
                }
              )
          ) {
            Box(
              modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .heightIn(min = 32.dp)
            ) {
              when (menuItem) {
                is ToolbarMenuItem.TextMenu -> BuildTextMenuItem(menuItem)
              }
            }

            if (index < menuItems.lastIndex) {
              KurobaComposeDivider(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 6.dp)
              )
            }
          }
        }
      }
    )
  }

  @Composable
  private fun BoxScope.BuildTextMenuItem(menuItem: ToolbarMenuItem.TextMenu) {
    val chanTheme = LocalChanTheme.current
    val title = stringResource(id = menuItem.textId)
    val subtitle = if (menuItem.subTextId != null) {
      stringResource(id = menuItem.subTextId)
    } else {
      null
    }

    val floatingMenuItemTitleSize by remember { uiInfoManager.floatingMenuItemTitleSize }
    val floatingMenuItemSubTitleSize by remember { uiInfoManager.floatingMenuItemSubTitleSize }

    if (subtitle.isNullOrBlank()) {
      Text(
        modifier = Modifier
          .fillMaxHeight()
          .align(Alignment.CenterStart),
        text = title,
        color = chanTheme.textColorPrimaryCompose,
        fontSize = floatingMenuItemTitleSize
      )
    } else {
      Column(
        modifier = Modifier.wrapContentHeight()
      ) {
        Text(
          modifier = Modifier.wrapContentHeight(),
          text = title,
          color = chanTheme.textColorPrimaryCompose,
          fontSize = floatingMenuItemTitleSize
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          modifier = Modifier.wrapContentHeight(),
          text = subtitle,
          color = chanTheme.textColorSecondaryCompose,
          fontSize = floatingMenuItemSubTitleSize
        )
      }
    }
  }

  companion object {
    private val SCREEN_KEY = ScreenKey("FloatingMenuScreen")
  }
}