package com.github.k1rakishou.kurobaexlite.ui.helpers.floating

import android.os.Bundle
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
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.util.checkCanUseType
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCheckbox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeRadioButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.launch

// TODO(KurobaEx): screen parameters are not persisted across process death yet!
class FloatingMenuScreen(
  screenArgs: Bundle? = null,
  floatingMenuKey: String,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  menuItems: List<FloatingMenuItem>,
  private val onMenuItemClicked: (FloatingMenuItem) -> Unit,
  private val onNoItemsWereClicked: () -> Unit = {},
  private val onDismiss: () -> Unit = {}
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val floatingMenuScreenKey = ScreenKey("FloatingMenuScreen_${floatingMenuKey}")

  private val coroutineScope = KurobaCoroutineScope()
  private val menuItems by lazy { menuItems.filter { it.visible } }
  private var noItemsWereClicked = true

  override val screenKey: ScreenKey = floatingMenuScreenKey
  override val contentAlignment: Alignment = touchPositionDependantAlignment

  @Composable
  override fun DefaultFloatingScreenBackPressHandler() {
    // no-op, we have our own handler
  }

  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    if (screenDisposeEvent == ScreenDisposeEvent.RemoveFromNavStack) {
      if (noItemsWereClicked) {
        onNoItemsWereClicked()
      }

      onDismiss()
    }

    super.onDisposed(screenDisposeEvent)
  }

  @Composable
  override fun FloatingContent() {
    // Make sure menuItems is never empty
    LaunchedEffect(
      key1 = menuItems,
      block = { check(menuItems.isNotEmpty()) { "menuItems is empty!" } }
    )

    val listOfMenuItems = remember { mutableStateListOf(menuItems) }

    val topItems = listOfMenuItems.lastOrNull()
    if (topItems == null) {
      return
    }

    val availableWidth = with(LocalDensity.current) {
      (maxAvailableWidthPx() / 1.35f).toDp()
    }

    HandleBackPresses {
      if (listOfMenuItems.isNotEmpty()) {
        listOfMenuItems.removeLast()
      }

      if (!listOfMenuItems.isEmpty()) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses stopPresenting()
    }

    LazyColumn(
      modifier = Modifier.width(availableWidth),
      content = {
        items(
          count = topItems.size,
          key = { index -> topItems.get(index).menuItemKey }
        ) { index ->
          val menuItem = topItems.get(index)

          BuildMenuItemContainer(
            menuItem = menuItem,
            index = index,
            lastIndexInMenuList = topItems.lastIndex,
            onItemClicked = { clickedMenuItem ->
              coroutineScope.launch {
                noItemsWereClicked = false

                onMenuItemClicked(clickedMenuItem)
                stopPresenting()
              }
            },
            onOpenNestedMenu = { clickedMenuItem ->
              listOfMenuItems += clickedMenuItem.moreItems
            }
          )
        }
      }
    )
  }

  @Composable
  private fun BuildMenuItemContainer(
    menuItem: FloatingMenuItem,
    index: Int,
    lastIndexInMenuList: Int,
    onItemClicked: (FloatingMenuItem) -> Unit,
    onOpenNestedMenu: (FloatingMenuItem.NestedItems) -> Unit
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
              onItemClicked = { clickedItem -> onItemClicked(clickedItem) }
            )
          }
          is FloatingMenuItem.Icon -> {
            BuildIconMenuItem(item = menuItem, onItemClicked = onItemClicked)
          }
          is FloatingMenuItem.Group -> {
            BuildRadioButtonGroup(
              itemGroup = menuItem,
              onItemClicked = { clickedItem -> onItemClicked(clickedItem) }
            )
          }
          is FloatingMenuItem.NestedItems -> {
            Row(modifier = Modifier.fillMaxWidth()) {
              BuildTitleAndSubtitleItems(
                modifier = Modifier.weight(1f),
                text = menuItem.text,
                subText = null,
                onItemClicked = { onOpenNestedMenu(menuItem) },
              )

              KurobaComposeIcon(
                modifier = Modifier
                  .size(40.dp)
                  .padding(4.dp),
                drawableId = R.drawable.ic_baseline_chevron_right_24
              )
            }
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
          is FloatingMenuItem.TextHeader -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End
            ) {
              BuildTextHeaderMenuItem(item = menuItem)
            }
          }
        }
      }

      if (index < lastIndexInMenuList) {
        KurobaComposeDivider(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
        )
      }
    }
  }

  @Composable
  private fun BuildTextHeaderMenuItem(item: FloatingMenuItem.TextHeader) {
    val chanTheme = LocalChanTheme.current
    val density = LocalDensity.current

    val titleTextSize by globalUiInfoManager.textTitleSizeSp.collectAsState()
    val headerTextSize = with(density) {
      remember(key1 = titleTextSize) { (titleTextSize.toPx() + 4.sp.toPx()).toSp() }
    }

    val headerTitle = when (item.text) {
      is FloatingMenuItem.MenuItemText.Id -> stringResource(id = item.text.id)
      is FloatingMenuItem.MenuItemText.String -> item.text.text
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(horizontal = 8.dp, vertical = 16.dp),
      contentAlignment = Alignment.CenterStart
    ) {
      Text(
        modifier = Modifier.wrapContentSize(),
        text = headerTitle,
        color = chanTheme.textColorPrimary,
        fontSize = headerTextSize
      )
    }
  }

  @Composable
  private fun BuildCheckMenuItem(
    item: FloatingMenuItem.Check,
    onItemClicked: (FloatingMenuItem) -> Unit
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
          onItemClicked(item)
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
        onCheckChanged = {
          if (defaultChecked == null) {
            return@KurobaComposeCheckbox
          }

          onItemClicked(item)
        }
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun BuildRadioButtonGroup(
    itemGroup: FloatingMenuItem.Group,
    onItemClicked: (FloatingMenuItem) -> Unit
  ) {
    val currentlyCheckedItemKey by remember { mutableStateOf(itemGroup.checkedMenuItemKey) }

    Column {
      for ((index, groupItem) in itemGroup.groupItems.withIndex()) {
        key(groupItem.menuItemKey) {
          Row(
            modifier = Modifier
              .kurobaClickable { onItemClicked(groupItem) }
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

            KurobaComposeRadioButton(
              modifier = Modifier.wrapContentSize(),
              currentlySelected = currentlyCheckedItemKey == groupItem.menuItemKey,
              onSelectionChanged = { onItemClicked(groupItem) }
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
        .kurobaClickable(bounded = false, onClick = { onItemClicked(item) })
        .padding(4.dp),
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
    val titleTextSize by globalUiInfoManager.textTitleSizeSp.collectAsState()
    val subtitleTextSize by globalUiInfoManager.textSubTitleSizeSp.collectAsState()
    val defaultHorizPadding = globalUiInfoManager.defaultHorizPadding
    val defaultVertPadding = globalUiInfoManager.defaultVertPadding

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
          color = chanTheme.textColorPrimary,
          fontSize = titleTextSize
        )
      } else {
        Column(
          modifier = Modifier.wrapContentHeight()
        ) {
          Text(
            modifier = Modifier.wrapContentHeight(),
            text = title,
            color = chanTheme.textColorPrimary,
            fontSize = titleTextSize
          )

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            modifier = Modifier.wrapContentHeight(),
            text = subtitle,
            color = chanTheme.textColorSecondary,
            fontSize = subtitleTextSize
          )
        }
      }
    }
  }

  companion object {
    const val CATALOG_OVERFLOW = "catalog_overflow_menu"
    const val POST_SEARCH_LONGTAP_MENU = "post_search_longtap_menu"
    const val POST_LONGTAP_MENU = "post_longtap_menu"
    const val CATALOG_OVERFLOW_LAYOUT_TYPE = "catalog_overflow_layout_type_menu"
    const val THREAD_OVERFLOW = "thread_overflow_menu"
    const val BOOKMARKS_OVERFLOW = "bookmarks_overflow_menu"
    const val APP_SETTINGS_LIST_OPTION_MENU = "app_settings_list_option_menu"
    const val REMOTE_IMAGE_SEARCH_OPTIONS_MENUS = "remote_image_search_options_menu"
  }
}

sealed class FloatingMenuItem {
  abstract val menuItemKey: Any
  open val visible: Boolean = true

  val data: Any?
    get() {
      if (this is Text) {
        return menuItemData
      }

      return null
    }


  data class Header(
    override val menuItemKey: Long = HEADER,
    val items: List<Icon>
  ) : FloatingMenuItem() {
    init {
      require(checkCanUseType(menuItemKey)) { "Cannot use type: \'${menuItemKey::class.java.name}\' as the key!" }
    }
  }

  data class TextHeader(
    override val menuItemKey: Long = TEXT_HEADER,
    val text: MenuItemText
  ) : FloatingMenuItem() {
    init {
      require(checkCanUseType(menuItemKey)) { "Cannot use type: \'${menuItemKey::class.java.name}\' as the key!" }
    }
  }

  data class Text(
    override val menuItemKey: Any,
    override val visible: Boolean = true,
    val menuItemData: Any? = null,
    val text: MenuItemText,
    val subText: MenuItemText? = null
  ) : FloatingMenuItem() {
    init {
      require(checkCanUseType(menuItemKey)) { "Cannot use type: \'${menuItemKey::class.java.name}\' as the key!" }
    }
  }

  data class Check(
    override val menuItemKey: Any,
    val isChecked: suspend () -> Boolean,
    val text: MenuItemText,
    val subText: MenuItemText? = null
  ) : FloatingMenuItem() {
    init {
      require(checkCanUseType(menuItemKey)) { "Cannot use type: \'${menuItemKey::class.java.name}\' as the key!" }
    }
  }

  data class Icon(
    override val menuItemKey: Any,
    @DrawableRes val iconId: Int
  ) : FloatingMenuItem() {
    init {
      require(checkCanUseType(menuItemKey)) { "Cannot use type: \'${menuItemKey::class.java.name}\' as the key!" }
    }
  }

  data class Group(
    override val menuItemKey: Any = keyCounter.getAndDecrement(),
    val checkedMenuItemKey: Any,
    val groupItems: List<Text>
  ) : FloatingMenuItem() {
    init {
      require(checkCanUseType(menuItemKey)) { "Cannot use type: \'${menuItemKey::class.java.name}\' as the key!" }
    }
  }

  data class NestedItems(
    override val menuItemKey: Any = keyCounter.getAndDecrement(),
    val text: MenuItemText,
    val moreItems: List<FloatingMenuItem>
  ) : FloatingMenuItem() {
    init {
      require(checkCanUseType(menuItemKey)) { "Cannot use type: \'${menuItemKey::class.java.name}\' as the key!" }
    }
  }

  data class Footer(
    override val menuItemKey: Long = FOOTER,
    val items: List<Icon>
  ) : FloatingMenuItem() {
    init {
      require(checkCanUseType(menuItemKey)) { "Cannot use type: \'${menuItemKey::class.java.name}\' as the key!" }
    }
  }

  sealed class MenuItemText {
    data class Id(@StringRes val id: Int) : MenuItemText()
    data class String(val text: kotlin.String) : MenuItemText()
  }

  companion object {
    const val HEADER = -1L
    const val TEXT_HEADER = -2L
    const val FOOTER = -3L

    private val keyCounter = AtomicLong(-100)
  }

}