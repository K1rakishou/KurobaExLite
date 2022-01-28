package com.github.k1rakishou.kurobaexlite.ui.screens

import android.graphics.Point
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.elements.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.elements.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey

class FloatingMenuScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val menuItems: List<ToolbarMenuItem>,
  private val onMenuItemClicked: (ToolbarMenuItem) -> Unit
) : FloatingComposeScreen(componentActivity, navigationRouter) {

  override val screenKey: ScreenKey = SCREEN_KEY

  private val lastTouchPosition = uiInfoManager.lastTouchPosition

  @Composable
  override fun BoxScope.FloatingContent() {
    val isTablet = uiInfoManager.isTablet
    val maxWidthDp = with(LocalDensity.current) {
      val maxWidth = if (isTablet) {
        uiInfoManager.maxParentWidth - (uiInfoManager.maxParentWidth / 4)
      } else {
        uiInfoManager.maxParentWidth
      }

      return@with maxWidth.toDp()
    }

    val maxHeightDp = with(LocalDensity.current) {
      val maxHeight = if (isTablet) {
        uiInfoManager.maxParentHeight - (uiInfoManager.maxParentHeight / 4)
      } else {
        uiInfoManager.maxParentHeight
      }

      return@with maxHeight.toDp()
    }

    KurobaComposeCardView(
      modifier = Modifier
        .wrapContentSize()
        .widthIn(max = maxWidthDp)
        .heightIn(max = maxHeightDp)
        .align(alignment = { size, space, _ ->
          val availableWidth = uiInfoManager.maxParentWidth
          val availableHeight = uiInfoManager.maxParentHeight

          calculateAlignment(size, space, availableWidth, availableHeight, lastTouchPosition)
        })
    ) {
      LazyColumn(
        content = {
          items(
            count = menuItems.size,
            key = { index -> menuItems.get(index).menuItemId }
          ) { index ->
            val menuItem = menuItems.get(index)

            Box(
              modifier = Modifier
                .wrapContentSize()
                .defaultMinSize(minWidth = 256.dp, minHeight = 32.dp)
                .kurobaClickable(
                  onClick = {
                    onMenuItemClicked(menuItem)
                    pop()
                  }
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              when (menuItem) {
                is ToolbarMenuItem.TextMenu -> BuildTextMenuItem(menuItem)
              }
            }
          }
        }
      )
    }
  }

  private fun calculateAlignment(
    size: IntSize,
    space: IntSize,
    availableWidth: Int,
    availableHeight: Int,
    lastTouchPosition: Point
  ): IntOffset {
    val biasX = (lastTouchPosition.x.toFloat() / availableWidth.toFloat()).coerceIn(0f, 1f)
    val biasY = (lastTouchPosition.y.toFloat() / availableHeight.toFloat()).coerceIn(0f, 1f)

    val horizPadding = horizPaddingPx.toInt()
    val vertPadding = vertPaddingPx.toInt()

    val offsetX = ((space.width - size.width).toFloat() * biasX).toInt()
      .coerceIn(horizPadding, availableWidth - horizPadding)
    val offsetY = ((space.height - size.height).toFloat() * biasY).toInt()
      .coerceIn(vertPadding, availableHeight - vertPadding)

    return IntOffset(x = offsetX, y = offsetY)
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