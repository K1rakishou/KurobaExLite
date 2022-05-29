package com.github.k1rakishou.kurobaexlite.features.posts.shared

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.ModifyMarkedPosts
import com.github.k1rakishou.kurobaexlite.managers.MarkedPostManager
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class PostLongtapContentMenu(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter,
  private val screenCoroutineScope: CoroutineScope
) {
  private val markedPostManager: MarkedPostManager by inject(MarkedPostManager::class.java)
  private val modifyMarkedPosts: ModifyMarkedPosts by inject(ModifyMarkedPosts::class.java)
  private val postReplyChainManager: PostReplyChainManager by inject(PostReplyChainManager::class.java)

  fun showMenu(
    postListOptions: PostListOptions,
    postCellData: PostCellData,
    reparsePostsFunc: (Collection<PostDescriptor>) -> Unit
  ) {
    screenCoroutineScope.launch {
      val floatingMenuItems = mutableListOf<FloatingMenuItem>().apply {
        if (!postListOptions.isCatalogMode) {
          if (markedPostManager.isPostMarkedAsMine(postCellData.postDescriptor)) {
            this += FloatingMenuItem.Text(
              menuItemKey = MARK_UNMARK_POST_AS_OWN,
              menuItemData = postCellData.postDescriptor,
              text = FloatingMenuItem.MenuItemText.Id(R.string.post_longtap_menu_unmark_post_as_own)
            )
          } else {
            this += FloatingMenuItem.Text(
              menuItemKey = MARK_MARK_POST_AS_OWN,
              menuItemData = postCellData.postDescriptor,
              text = FloatingMenuItem.MenuItemText.Id(R.string.post_longtap_menu_mark_post_as_own)
            )
          }
        }
      }

      if (floatingMenuItems.isEmpty()) {
        return@launch
      }

      navigationRouter.presentScreen(
        FloatingMenuScreen(
          floatingMenuKey = FloatingMenuScreen.POST_LONGTAP_MENU,
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          menuItems = floatingMenuItems,
          onMenuItemClicked = { menuItem ->
            screenCoroutineScope.launch {
              processClickedToolbarMenuItem(
                menuItem = menuItem,
                reparsePostsFunc = reparsePostsFunc
              )
            }
          }
        )
      )
    }
  }

  private suspend fun processClickedToolbarMenuItem(
    menuItem: FloatingMenuItem,
    reparsePostsFunc: (Collection<PostDescriptor>) -> Unit
  ) {
    when (menuItem.menuItemKey as Int) {
      MARK_MARK_POST_AS_OWN -> {
        val postDescriptor = menuItem.data as? PostDescriptor
          ?: return

        if (!modifyMarkedPosts.markPostAsMine(postDescriptor)) {
          return
        }

        val postsToReparse = postReplyChainManager.getRepliesFrom(postDescriptor).toMutableSet()
        postsToReparse += postDescriptor

        reparsePostsFunc(postsToReparse)
      }
      MARK_UNMARK_POST_AS_OWN -> {
        val postDescriptor = menuItem.data as? PostDescriptor
          ?: return

        if (!modifyMarkedPosts.unmarkPostAsMine(postDescriptor)) {
          return
        }

        val postsToReparse = postReplyChainManager.getRepliesFrom(postDescriptor).toMutableSet()
        postsToReparse += postDescriptor

        reparsePostsFunc(postsToReparse)
      }
    }
  }

  companion object {
    private const val MARK_MARK_POST_AS_OWN = 0
    private const val MARK_UNMARK_POST_AS_OWN = 1
  }

}