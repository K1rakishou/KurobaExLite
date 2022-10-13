package com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.toolbar.CatalogScreenDefaultToolbar
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.PostScreenState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.state.ThreadScreenPostsState
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar

abstract class PostsScreenDefaultToolbar<TS : PostsScreenDefaultToolbar.PostsScreenToolbarState> : KurobaChildToolbar() {

  @Composable
  protected fun UpdateToolbarTitle(
    isCatalogMode: Boolean,
    postScreenState: PostScreenState,
    defaultToolbarState: () -> TS?
  ) {
    val context = LocalContext.current

    val parsedPostDataCache: ParsedPostDataCache = koinRemember()
    val siteManager: SiteManager = koinRemember()
    val loadChanCatalog: LoadChanCatalog = koinRemember()

    val postListAsyncMut by postScreenState.postsAsyncDataState.collectAsState()
    val postListAsync = postListAsyncMut

    LaunchedEffect(
      key1 = postListAsync,
      block = {
        defaultToolbarState()?.let { state ->
          if (isCatalogMode && state is CatalogScreenDefaultToolbar.State) {
            state.showClickableMenuIcon.value = postListAsync is AsyncData.Uninitialized
              || postListAsync is AsyncData.Data
          }
        }

        when (postListAsync) {
          AsyncData.Uninitialized -> {
            val state = defaultToolbarState()
              ?: return@LaunchedEffect

            state.toolbarTitleState.value = if (isCatalogMode) {
              context.resources.getString(R.string.toolbar_loading_empty)
            } else {
              null
            }

            state.contentFullyLoaded.value = false
          }
          AsyncData.Loading -> {
            val state = defaultToolbarState()
              ?: return@LaunchedEffect

            state.toolbarTitleState.value = context.resources.getString(R.string.toolbar_loading_title)
            state.contentFullyLoaded.value = false
          }
          is AsyncData.Error -> {
            val state = defaultToolbarState()
              ?: return@LaunchedEffect

            state.toolbarTitleState.value = context.resources.getString(R.string.toolbar_loading_error)
            state.contentFullyLoaded.value = false
          }
          is AsyncData.Data -> {
            when (val chanDescriptor = postListAsync.data.chanDescriptor) {
              is CatalogDescriptor -> {
                val state = defaultToolbarState()
                  ?: return@LaunchedEffect

                val boardTitle = loadChanCatalog.await(chanDescriptor).getOrNull()?.boardTitle

                if (state is CatalogScreenDefaultToolbar.State) {
                  state.siteIconUrl.value = siteManager.bySiteKey(chanDescriptor.siteKey)
                    ?.icon()
                    ?.toString()
                }

                state.toolbarTitleState.value = "/${chanDescriptor.boardCode}/"
                state.toolbarSubtitleState.value = boardTitle
                state.contentFullyLoaded.value = true
              }
              is ThreadDescriptor -> {
                snapshotFlow { (postScreenState as ThreadScreenPostsState).originalPostState.value }
                  .collect { originalPost ->
                    if (originalPost == null) {
                      return@collect
                    }

                    parsedPostDataCache.doWithPostDataOnceItsLoaded(
                      isCatalog = false,
                      postDescriptor = originalPost.postDescriptor,
                      func = {
                        val state = defaultToolbarState()
                          ?: return@doWithPostDataOnceItsLoaded

                        val title = parsedPostDataCache.formatThreadToolbarTitle(originalPost.postDescriptor)
                          ?: return@doWithPostDataOnceItsLoaded

                        state.toolbarTitleState.value = title
                        state.contentFullyLoaded.value = true
                      }
                    )
                  }
              }
            }
          }
        }
      })
  }

  abstract class PostsScreenToolbarState : KurobaChildToolbar.ToolbarState {
    val toolbarTitleState = mutableStateOf<String?>(null)
    val toolbarSubtitleState = mutableStateOf<String?>(null)
    val contentFullyLoaded = mutableStateOf(false)
  }

}