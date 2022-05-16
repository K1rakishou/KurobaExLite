package com.github.k1rakishou.kurobaexlite.features.posts.search

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.interactors.navigation.ModifyNavigationHistory
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class PostSearchLongtapContentMenu(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter,
  private val screenCoroutineScope: CoroutineScope
) {
  private val modifyNavigationHistory: ModifyNavigationHistory by inject(ModifyNavigationHistory::class.java)

  fun showMenu(
    postCellData: PostCellData,
  ) {
    screenCoroutineScope.launch {
      val floatingMenuItems = mutableListOf<FloatingMenuItem>().apply {
        this += FloatingMenuItem.Text(
          menuItemKey = ADD_TO_HISTORY,
          menuItemData = postCellData.postDescriptor,
          text = FloatingMenuItem.MenuItemText.Id(R.string.post_search_longtap_menu_add_to_history)
        )
      }

      if (floatingMenuItems.isEmpty()) {
        return@launch
      }

      navigationRouter.presentScreen(
        FloatingMenuScreen(
          floatingMenuKey = FloatingMenuScreen.CATALOG_OVERFLOW,
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          menuItems = floatingMenuItems,
          onMenuItemClicked = { menuItem ->
            screenCoroutineScope.launch {
              processClickedToolbarMenuItem(menuItem)
            }
          }
        )
      )
    }
  }

  private suspend fun processClickedToolbarMenuItem(
    menuItem: FloatingMenuItem,
  ) {
    when (menuItem.menuItemKey as Int) {
      ADD_TO_HISTORY -> {
        val postDescriptor = menuItem.data as? PostDescriptor
          ?: return

        modifyNavigationHistory.addThread(postDescriptor.threadDescriptor)
      }
    }
  }

  companion object {
    private const val ADD_TO_HISTORY = 0
  }

}