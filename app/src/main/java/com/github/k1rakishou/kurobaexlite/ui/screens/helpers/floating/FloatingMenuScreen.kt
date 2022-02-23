package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating

import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.*
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import logcat.logcat
import java.util.concurrent.atomic.AtomicLong

class FloatingMenuScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val menuItems: List<FloatingMenuItem>,
  private val onMenuItemClicked: (FloatingMenuItem) -> Unit
) : FloatingComposeScreen(componentActivity, navigationRouter) {

  private val lastTouchPosition = uiInfoManager.lastTouchPosition
  private val callbacksToInvokeMap = mutableMapOf<Any, FloatingMenuItem>()

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

  override fun onDestroy() {
    callbacksToInvokeMap.values.forEach { menuItem ->
      logcat(tag = "FloatingMenuScreen") { "calling onMenuItemClicked(${menuItem.menuItemKey})" }
      onMenuItemClicked(menuItem)
    }

    callbacksToInvokeMap.clear()
  }

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
          key = { index -> menuItems.get(index).menuItemKey }
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
          is FloatingMenuItem.Group -> {
            BuildCheckboxGroup(
              itemGroup = menuItem,
              onMenuItemClicked = { checked, clickedItem ->
                if (checked) {
                  callbacksToInvokeMap.put(clickedItem.menuItemKey, clickedItem)
                } else {
                  callbacksToInvokeMap.remove(clickedItem.menuItemKey)
                }
              })
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
  private fun BuildCheckboxGroup(
    itemGroup: FloatingMenuItem.Group,
    onMenuItemClicked: (Boolean, FloatingMenuItem) -> Unit
  ) {
    var currentlyCheckedItemKey by remember {
      mutableStateOf(itemGroup.checkedMenuItemKey)
    }

    Column {
      for ((index, groupItem) in itemGroup.groupItems.withIndex()) {
        key(groupItem.menuItemKey) {
          Row(
            modifier = Modifier
              .kurobaClickable {
                currentlyCheckedItemKey = groupItem.menuItemKey
                val checked = currentlyCheckedItemKey == groupItem.menuItemKey

                onMenuItemClicked(checked, groupItem)
              }
              .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            BuildTextMenuItem(
              modifier = Modifier.weight(1f).padding(0.dp),
              item = groupItem,
              onMenuItemClicked = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            // TODO(KurobaEx): change to GroupBoxItem or whatever once it's implemented
            KurobaComposeCheckbox(
              modifier = Modifier.wrapContentSize(),
              currentlyChecked = currentlyCheckedItemKey == groupItem.menuItemKey,
              onCheckChanged = { currentlyCheckedItemKey = groupItem.menuItemKey }
            )
          }

          if (index < itemGroup.groupItems.lastIndex) {
            KurobaComposeDivider(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
            )
          }
        }
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
  private fun BuildTextMenuItem(
    modifier: Modifier = Modifier,
    item: FloatingMenuItem.Text,
    onMenuItemClicked: ((item: FloatingMenuItem) -> Unit)?
  ) {
    val chanTheme = LocalChanTheme.current

    val title = when (item.text) {
      is FloatingMenuItem.MenuItemText.Id -> stringResource(id = item.text.id)
      is FloatingMenuItem.MenuItemText.String -> item.text.text
    }

    val subtitle = if (item.subText != null) {
      when (item.subText) {
        is FloatingMenuItem.MenuItemText.Id -> stringResource(id = item.subText.id)
        is FloatingMenuItem.MenuItemText.String -> item.subText.text
      }
    } else {
      null
    }

    val floatingMenuItemTitleSize by remember { uiInfoManager.floatingMenuItemTitleSize }
    val floatingMenuItemSubTitleSize by remember { uiInfoManager.floatingMenuItemSubTitleSize }

    val clickModifier = if (onMenuItemClicked == null) {
      Modifier
    } else {
      Modifier.kurobaClickable(onClick = { onMenuItemClicked(item) })
    }

    Box(
      modifier = Modifier
        .heightIn(min = 40.dp)
        .fillMaxWidth()
        .then(clickModifier)
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .then(modifier)
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
  abstract val menuItemKey: Any

  data class Text(
    override val menuItemKey: Any,
    val text: MenuItemText,
    val subText: MenuItemText? = null
  ) : FloatingMenuItem()

  data class Icon(
    override val menuItemKey: Any,
    @DrawableRes val iconId: Int
  ) : FloatingMenuItem()

  data class Group(
    override val menuItemKey: Long = keyCounter.getAndDecrement(),
    val checkedMenuItemKey: Any,
    val groupItems: List<Text>
  ) : FloatingMenuItem()

  data class Header(
    override val menuItemKey: Long = HEADER,
    val items: List<Icon>
  ) : FloatingMenuItem()

  data class Footer(
    override val menuItemKey: Long = FOOTER,
    val items: List<Icon>
  ) : FloatingMenuItem()

  sealed class MenuItemText {
    data class Id(@StringRes val id: Int) : MenuItemText()
    data class String(val text: kotlin.String) : MenuItemText()
  }

  companion object {
    const val HEADER = -1L
    const val FOOTER = -2L

    private val keyCounter = AtomicLong(-100)
  }

}