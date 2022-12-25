package com.github.k1rakishou.kurobaexlite.features.posts.shared

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.post_bind.PostBindProcessorCoordinator
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import com.github.k1rakishou.kurobaexlite.interactors.marked_post.ModifyMarkedPosts
import com.github.k1rakishou.kurobaexlite.managers.MarkedPostManager
import com.github.k1rakishou.kurobaexlite.managers.PostReplyChainManager
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class PostLongtapContextMenu(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter,
  private val screenCoroutineScope: CoroutineScope
) {
  private val replyLayoutViewModel by componentActivity.viewModel<ReplyLayoutViewModel>()

  private val markedPostManager: MarkedPostManager by inject(MarkedPostManager::class.java)
  private val modifyMarkedPosts: ModifyMarkedPosts by inject(ModifyMarkedPosts::class.java)
  private val postReplyChainManager: PostReplyChainManager by inject(PostReplyChainManager::class.java)
  private val siteManager: SiteManager by inject(SiteManager::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val postBindProcessorCoordinator: PostBindProcessorCoordinator by inject(PostBindProcessorCoordinator::class.java)

  fun showMenu(
    postListOptions: PostListOptions,
    postCellData: PostCellData,
    viewProvider: () -> View,
    reparsePostsFunc: (Collection<PostDescriptor>) -> Unit,
    startPostSelection: ((PostDescriptor) -> Unit)? = null
  ) {
    screenCoroutineScope.launch {
      val floatingMenuItems = mutableListOf<FloatingMenuItem>().apply {
        if (!postListOptions.isCatalogMode) {
          this += FloatingMenuItem.Text(
            menuItemKey = QUOTE,
            menuItemData = postCellData.postDescriptor,
            text = FloatingMenuItem.MenuItemText.Id(R.string.quote)
          )

          this += FloatingMenuItem.Text(
            menuItemKey = QUOTE_TEXT,
            menuItemData = postCellData.postDescriptor,
            text = FloatingMenuItem.MenuItemText.Id(R.string.quote_text)
          )
        }

        if (startPostSelection != null) {
          this += FloatingMenuItem.Text(
            menuItemKey = POST_SELECTION,
            menuItemData = postCellData.postDescriptor,
            text = FloatingMenuItem.MenuItemText.Id(R.string.post_longtap_menu_post_selection)
          )
        }

        this += FloatingMenuItem.Text(
          menuItemKey = LOAD_INLINED_CONTENT,
          menuItemData = postCellData.postDescriptor,
          text = FloatingMenuItem.MenuItemText.Id(R.string.post_longtap_menu_load_inlined_content)
        )

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

        this += FloatingMenuItem.Text(
          menuItemKey = COPY_POST_URL,
          menuItemData = postCellData.postDescriptor,
          text = FloatingMenuItem.MenuItemText.Id(R.string.post_longtap_menu_copy_url)
        )
      }

      if (floatingMenuItems.isEmpty()) {
        return@launch
      }

      viewProvider().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

      val selectedMenuItem = suspendCancellableCoroutine<FloatingMenuItem?> { cancellableContinuation ->
        var selectedMenuItem: FloatingMenuItem? = null

        navigationRouter.presentScreen(
          FloatingMenuScreen(
            floatingMenuKey = FloatingMenuScreen.POST_LONGTAP_MENU,
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            menuItems = floatingMenuItems,
            onMenuItemClicked = { menuItem -> selectedMenuItem = menuItem },
            onDismiss = { cancellableContinuation.resumeSafe(selectedMenuItem) }
          )
        )
      }

      if (selectedMenuItem == null) {
        return@launch
      }

      processClickedToolbarMenuItem(
        menuItem = selectedMenuItem,
        postCellData = postCellData,
        postListOptions = postListOptions,
        reparsePostsFunc = reparsePostsFunc,
        startPostSelection = { postDescriptor -> startPostSelection?.invoke(postDescriptor) }
      )
    }
  }

  private suspend fun processClickedToolbarMenuItem(
    menuItem: FloatingMenuItem,
    postCellData: PostCellData,
    postListOptions: PostListOptions,
    reparsePostsFunc: (Collection<PostDescriptor>) -> Unit,
    startPostSelection: (PostDescriptor) -> Unit
  ) {
    when (menuItem.menuItemKey as Int) {
      QUOTE,
      QUOTE_TEXT -> {
        screenCoroutineScope.launch {
          if (menuItem.menuItemKey == QUOTE) {
            replyLayoutViewModel.quotePost(
              chanDescriptor = postCellData.chanDescriptor,
              postCellData = postCellData
            )
          } else {
            val comment = postCellData.parsedPostData?.parsedPostComment
            if (comment.isNullOrEmpty()) {
              replyLayoutViewModel.quotePost(
                chanDescriptor = postCellData.chanDescriptor,
                postCellData = postCellData
              )
            } else {
              replyLayoutViewModel.quotePostWithText(
                chanDescriptor = postCellData.chanDescriptor,
                postCellData = postCellData,
                selectedText = comment
              )
            }
          }
        }
      }
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
      COPY_POST_URL -> {
        val postDescriptor = menuItem.data as? PostDescriptor
          ?: return

        val postUrl = siteManager.bySiteKey(postDescriptor.siteKey)?.desktopUrl(
          threadDescriptor = postDescriptor.threadDescriptor,
          postNo = postDescriptor.postNo,
          postSubNo = postDescriptor.postSubNo
        )

        if (postUrl.isNullOrBlank()) {
          return
        }

        androidHelpers.copyToClipboard("Post url", postUrl)
      }
      POST_SELECTION -> {
        startPostSelection(postCellData.postDescriptor)
      }
      LOAD_INLINED_CONTENT -> {
        postBindProcessorCoordinator.forceLoadInlinedContent(
          isCatalogMode = postListOptions.isCatalogMode,
          postDescriptor = postCellData.postDescriptor
        )
      }
    }
  }

  companion object {
    private const val QUOTE = 0
    private const val QUOTE_TEXT = 1
    private const val MARK_MARK_POST_AS_OWN = 2
    private const val MARK_UNMARK_POST_AS_OWN = 3
    private const val COPY_POST_URL = 4
    private const val POST_SELECTION = 5
    private const val LOAD_INLINED_CONTENT = 6
  }

}