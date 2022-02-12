package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating

import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey

class FloatingMenuScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val menuItems: List<FloatingMenuItem>,
  private val onMenuItemClicked: (FloatingMenuItem) -> Unit
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
        (maxAvailableWidthPx() / 1.35f).toDp()
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

          BuildMenuItemContainer(
            menuItem = menuItem,
            index = index,
            onMenuItemClicked = { clickedMenuItem ->
              onMenuItemClicked(clickedMenuItem)
              stopPresenting()
            })
        }
      }
    )
  }

  @Composable
  private fun BuildMenuItemContainer(
    menuItem: FloatingMenuItem,
    index: Int,
    onMenuItemClicked: (FloatingMenuItem) -> Unit
  ) {
    Column(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth()
    ) {
      Box {
        when (menuItem) {
          is FloatingMenuItem.Text -> {
            BuildTextMenuItem(item = menuItem, onMenuItemClicked = onMenuItemClicked)
          }
          is FloatingMenuItem.Icon -> {
            BuildIconMenuItem(item = menuItem, onMenuItemClicked = onMenuItemClicked)
          }
          is FloatingMenuItem.Footer -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End
            ) {
              menuItem.items.forEachIndexed { index, innerItem ->
                key(innerItem.iconId) {
                  BuildIconMenuItem(item = innerItem, onMenuItemClicked = onMenuItemClicked)

                  if (index != menuItem.items.size) {
                    Spacer(modifier = Modifier.width(4.dp))
                  }
                }
              }
            }
          }
          is FloatingMenuItem.Header -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End
            ) {
              menuItem.items.forEachIndexed { index, innerItem ->
                key(innerItem.iconId) {
                  BuildIconMenuItem(item = innerItem, onMenuItemClicked = onMenuItemClicked)

                  if (index != menuItem.items.size) {
                    Spacer(modifier = Modifier.width(4.dp))
                  }
                }
              }
            }
          }
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

  @Composable
  private fun BuildIconMenuItem(
    item: FloatingMenuItem.Icon,
    onMenuItemClicked: (item: FloatingMenuItem) -> Unit
  ) {
    KurobaComposeIcon(
      modifier = Modifier
        .size(40.dp)
        .kurobaClickable(onClick = { onMenuItemClicked(item) })
        .padding(8.dp),
      drawableId = item.iconId
    )
  }

  @Composable
  private fun BoxScope.BuildTextMenuItem(
    item: FloatingMenuItem.Text,
    onMenuItemClicked: (item: FloatingMenuItem) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val title = stringResource(id = item.textId)
    val subtitle = if (item.subTextId != null) {
      stringResource(id = item.subTextId)
    } else {
      null
    }

    val floatingMenuItemTitleSize by remember { uiInfoManager.floatingMenuItemTitleSize }
    val floatingMenuItemSubTitleSize by remember { uiInfoManager.floatingMenuItemSubTitleSize }

    Box(
      modifier = Modifier
        .heightIn(min = 32.dp)
        .fillMaxWidth()
        .kurobaClickable(onClick = { onMenuItemClicked(item) })
        .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
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
  }

  companion object {
    private val SCREEN_KEY = ScreenKey("FloatingMenuScreen")
  }
}

sealed class FloatingMenuItem {
  abstract val menuItemId: Int

  data class Text(
    override val menuItemId: Int,
    @StringRes val textId: Int,
    @StringRes val subTextId: Int? = null
  ) : FloatingMenuItem()

  data class Icon(
    override val menuItemId: Int,
    @DrawableRes val iconId: Int
  ) : FloatingMenuItem()

  data class Header(
    override val menuItemId: Int = HEADER,
    val items: List<Icon>
  ) : FloatingMenuItem()

  data class Footer(
    override val menuItemId: Int = FOOTER,
    val items: List<Icon>
  ) : FloatingMenuItem()

  companion object {
    const val HEADER = -1
    const val FOOTER = -2
  }

}