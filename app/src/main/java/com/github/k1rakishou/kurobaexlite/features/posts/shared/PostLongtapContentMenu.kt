package com.github.k1rakishou.kurobaexlite.features.posts.shared

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen

class PostLongtapContentMenu(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter,
) {

  suspend fun showMenu(
    postListOptions: PostListOptions,
    postCellData: PostCellData
  ) {
    val floatingMenuItems = listOf(
      FloatingMenuItem.Text(
        menuItemKey = MARK_UNMARK_POST_AS_OWN,
        text = FloatingMenuItem.MenuItemText.Id(R.string.post_longtap_menu_mark_post_as_own)
      )
    )

    navigationRouter.presentScreen(
      FloatingMenuScreen(
        floatingMenuKey = FloatingMenuScreen.CATALOG_OVERFLOW,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        menuItems = floatingMenuItems,
        onMenuItemClicked = { menuItem -> processClickedToolbarMenuItem(menuItem) }
      )
    )
  }

  private fun processClickedToolbarMenuItem(
    menuItem: FloatingMenuItem
  ) {
    when (menuItem.menuItemKey as Int) {
      MARK_UNMARK_POST_AS_OWN -> {
        // TODO(KurobaEx):
      }
    }
  }

  companion object {
    private const val MARK_UNMARK_POST_AS_OWN = 0
  }

}