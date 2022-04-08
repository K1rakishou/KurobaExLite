package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCheckbox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.launch
import logcat.logcat

class FloatingMenuScreen(
  floatingMenuKey: String,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val menuItems: List<FloatingMenuItem>,
  private val onMenuItemClicked: suspend (FloatingMenuItem) -> Unit,
  private val onDismiss: () -> Unit = {}
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  private val floatingMenuScreenKey = ScreenKey("FloatingMenuScreen_${floatingMenuKey}")

  private val callbacksToInvokeMap = mutableMapOf<Any, FloatingMenuItem>()
  private val coroutineScope = KurobaCoroutineScope()
  private var shouldCallOnDismiss = true

  override val screenKey: ScreenKey = floatingMenuScreenKey
  override val contentAlignment: Alignment = touchPositionDependantAlignment

  override fun onDestroy() {
    coroutineScope.launch {
      val callbacksWereEmpty = callbacksToInvokeMap.isEmpty()

      callbacksToInvokeMap.values.forEach { menuItem ->
        logcat(tag = "FloatingMenuScreen") { "calling onMenuItemClicked(${menuItem.menuItemKey})" }
        onMenuItemClicked(menuItem)
      }

      callbacksToInvokeMap.clear()
      coroutineScope.cancel()

      if (shouldCallOnDismiss && callbacksWereEmpty) {
        onDismiss()
      }
    }
  }

  @Composable
  override fun FloatingContent() {
    val orientationMut by uiInfoManager.currentOrientation.collectAsState()
    val orientation = orientationMut
    if (orientation == null) {
      return
    }

    val availableWidth = with(LocalDensity.current) {
      if (orientation == Configuration.ORIENTATION_PORTRAIT) {
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
            onItemClicked = { clickedMenuItem ->
              coroutineScope.launch {
                shouldCallOnDismiss = false

                onMenuItemClicked(clickedMenuItem)
                stopPresenting()
              }
            })
        }
      }
    )
  }

  @Composable
  private fun BuildMenuItemContainer(
    menuItem: FloatingMenuItem,
    index: Int,
    onItemClicked: (FloatingMenuItem) -> Unit
  ) {
    Column(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth()
    ) {
      Box {
        when (menuItem) {
          is FloatingMenuItem.Text -> {
            BuildTextMenuItem(item = menuItem, onItemClicked = onItemClicked)
          }
          is FloatingMenuItem.Check -> {
            BuildCheckMenuItem(
              item = menuItem,
              onItemClicked = { isDefaultState, clickedItem ->
                if (isDefaultState) {
                  callbacksToInvokeMap.remove(clickedItem.menuItemKey)
                } else {
                  callbacksToInvokeMap.put(clickedItem.menuItemKey, clickedItem)
                }
              })
          }
          is FloatingMenuItem.Icon -> {
            BuildIconMenuItem(item = menuItem, onItemClicked = onItemClicked)
          }
          is FloatingMenuItem.Group -> {
            BuildCheckboxGroup(
              itemGroup = menuItem,
              onItemClicked = { invokeCallback, clickedItem ->
                if (invokeCallback) {
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
                  BuildIconMenuItem(item = innerItem, onItemClicked = onItemClicked)

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
                  BuildIconMenuItem(item = innerItem, onItemClicked = onItemClicked)

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
  private fun BuildCheckMenuItem(
    item: FloatingMenuItem.Check,
    onItemClicked: (Boolean, FloatingMenuItem) -> Unit
  ) {
    var isCurrentlyChecked by remember { mutableStateOf(false) }
    var defaultChecked by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        val isChecked = item.isChecked()
        isCurrentlyChecked = isChecked
        defaultChecked = isChecked
      })

    Row(
      modifier = Modifier
        .kurobaClickable {
          if (defaultChecked == null) {
            return@kurobaClickable
          }

          isCurrentlyChecked = !isCurrentlyChecked
          onItemClicked(isCurrentlyChecked == defaultChecked, item)
        }
        .padding(horizontal = 0.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      BuildTitleAndSubtitleItems(
        modifier = Modifier
          .weight(1f)
          .padding(0.dp),
        text = item.text,
        subText = item.subText,
        onItemClicked = null
      )

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeCheckbox(
        modifier = Modifier.wrapContentSize(),
        currentlyChecked = isCurrentlyChecked,
        onCheckChanged = { nowChecked ->
          if (defaultChecked == null) {
            return@KurobaComposeCheckbox
          }

          isCurrentlyChecked = nowChecked
          onItemClicked(isCurrentlyChecked == defaultChecked, item)
        }
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun BuildCheckboxGroup(
    itemGroup: FloatingMenuItem.Group,
    onItemClicked: (Boolean, FloatingMenuItem) -> Unit
  ) {
    var currentlyCheckedItemKey by remember {
      mutableStateOf(itemGroup.checkedMenuItemKey)
    }

    fun applyChecks(clickedItem: FloatingMenuItem.Text) {
      currentlyCheckedItemKey = clickedItem.menuItemKey

      itemGroup.groupItems.fastForEach { item ->
        val checked = currentlyCheckedItemKey == item.menuItemKey
        val isInitiallyChecked = currentlyCheckedItemKey == itemGroup.checkedMenuItemKey
        val invokeCallback = checked && !isInitiallyChecked

        onItemClicked(invokeCallback, item)
      }
    }

    Column {
      for ((index, groupItem) in itemGroup.groupItems.withIndex()) {
        key(groupItem.menuItemKey) {
          Row(
            modifier = Modifier
              .kurobaClickable { applyChecks(groupItem) }
              .padding(horizontal = 0.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            BuildTextMenuItem(
              modifier = Modifier
                .weight(1f)
                .padding(0.dp),
              item = groupItem,
              onItemClicked = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            // TODO(KurobaEx): change to GroupBoxItem or whatever once it's implemented
            KurobaComposeCheckbox(
              modifier = Modifier.wrapContentSize(),
              currentlyChecked = currentlyCheckedItemKey == groupItem.menuItemKey,
              onCheckChanged = { applyChecks(groupItem) }
            )

            Spacer(modifier = Modifier.width(8.dp))
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
    onItemClicked: (item: FloatingMenuItem) -> Unit
  ) {
    KurobaComposeIcon(
      modifier = Modifier
        .size(40.dp)
        .kurobaClickable(onClick = { onItemClicked(item) })
        .padding(8.dp),
      drawableId = item.iconId
    )
  }

  @Composable
  private fun BuildTextMenuItem(
    modifier: Modifier = Modifier,
    item: FloatingMenuItem.Text,
    onItemClicked: ((item: FloatingMenuItem) -> Unit)?
  ) {
    val childOnItemClicked = remember(key1 = item) {
      if (onItemClicked != null) {
        return@remember { onItemClicked(item) }
      } else {
        return@remember null
      }
    }

    BuildTitleAndSubtitleItems(
      modifier = modifier,
      text = item.text,
      subText = item.subText,
      onItemClicked = childOnItemClicked,
    )
  }

  @Composable
  private fun BuildTitleAndSubtitleItems(
    modifier: Modifier,
    text: FloatingMenuItem.MenuItemText,
    subText: FloatingMenuItem.MenuItemText?,
    onItemClicked: (() -> Unit)?
  ) {
    val chanTheme = LocalChanTheme.current
    val titleTextSize by uiInfoManager.textTitleSizeSp.collectAsState()
    val subtitleTextSize by uiInfoManager.textSubTitleSizeSp.collectAsState()
    val defaultHorizPadding = uiInfoManager.defaultHorizPadding
    val defaultVertPadding = uiInfoManager.defaultVertPadding

    val title = when (text) {
      is FloatingMenuItem.MenuItemText.Id -> stringResource(id = text.id)
      is FloatingMenuItem.MenuItemText.String -> text.text
    }

    val subtitle = if (subText != null) {
      when (subText) {
        is FloatingMenuItem.MenuItemText.Id -> stringResource(id = subText.id)
        is FloatingMenuItem.MenuItemText.String -> subText.text
      }
    } else {
      null
    }

    val clickModifier = if (onItemClicked == null) {
      Modifier
    } else {
      Modifier.kurobaClickable(onClick = { onItemClicked() })
    }

    Box(
      modifier = Modifier
        .heightIn(min = 40.dp)
        .fillMaxWidth()
        .then(clickModifier)
        .padding(horizontal = defaultHorizPadding, vertical = defaultVertPadding)
        .then(modifier)
    ) {
      if (subtitle.isNullOrBlank()) {
        Text(
          modifier = Modifier
            .fillMaxHeight()
            .align(Alignment.CenterStart),
          text = title,
          color = chanTheme.textColorPrimaryCompose,
          fontSize = titleTextSize
        )
      } else {
        Column(
          modifier = Modifier.wrapContentHeight()
        ) {
          Text(
            modifier = Modifier.wrapContentHeight(),
            text = title,
            color = chanTheme.textColorPrimaryCompose,
            fontSize = titleTextSize
          )

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            modifier = Modifier.wrapContentHeight(),
            text = subtitle,
            color = chanTheme.textColorSecondaryCompose,
            fontSize = subtitleTextSize
          )
        }
      }
    }
  }

  companion object {
    const val CATALOG_OVERFLOW = "catalog_overflow_menu"
    const val CATALOG_OVERFLOW_LAYOUT_TYPE = "catalog_overflow_layout_type_menu"

    const val THREAD_OVERFLOW = "thread_overflow_menu"
  }
}

sealed class FloatingMenuItem {
  abstract val menuItemKey: Any

  data class Text(
    override val menuItemKey: Any,
    val text: MenuItemText,
    val subText: MenuItemText? = null
  ) : FloatingMenuItem()

  data class Check(
    override val menuItemKey: Any,
    val isChecked: suspend () -> Boolean,
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