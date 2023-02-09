package com.github.k1rakishou.kurobaexlite.features.posts.shared

import android.content.Context
import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.reply.PopupPostsScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.CrossThreadFollowHistory
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPartSpan
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class LinkableClickHelper(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter,
  private val screenCoroutineScope: CoroutineScope
) {
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val crossThreadFollowHistory: CrossThreadFollowHistory by inject(CrossThreadFollowHistory::class.java)
  private val chanPostCache: IChanPostCache by inject(IChanPostCache::class.java)
  private val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)

  fun processLongClickedLinkable(
    sourceScreenKey: ScreenKey,
    postCellData: PostCellData,
    linkable: TextPartSpan.Linkable,
  ) {
    logcat(TAG) {
      "processLongClickedLinkable() sourceScreenKey=${sourceScreenKey}, " +
        "postDescriptor=${postCellData.postDescriptor}, linkable=${linkable}"
    }

    // TODO(KurobaEx): copy full link
    // TODO(KurobaEx): open link in browser

    when (linkable) {
      is TextPartSpan.Linkable.Board -> {
        // TODO(KurobaEx):
      }
      is TextPartSpan.Linkable.Quote -> {
        // TODO(KurobaEx):
      }
      is TextPartSpan.Linkable.Search -> {
        // TODO(KurobaEx):
      }
      is TextPartSpan.Linkable.Url -> {
        // TODO(KurobaEx):
      }
    }
  }

  fun processClickedLinkable(
    context: Context,
    sourceScreenKey: ScreenKey,
    postCellData: PostCellData,
    linkable: TextPartSpan.Linkable,
    loadThreadFunc: (ThreadDescriptor) -> Unit,
    loadCatalogFunc: (CatalogDescriptor) -> Unit,
    showRepliesForPostFunc: (PopupPostsScreen.PopupPostViewMode) -> Unit
  ) {
    screenCoroutineScope.launch {
      logcat(TAG) {
        "processClickedLinkable() sourceScreenKey=${sourceScreenKey}, " +
          "postDescriptor=${postCellData.postDescriptor}, linkable=${linkable}"
      }

      when (linkable) {
        is TextPartSpan.Linkable.Quote -> {
          if (linkable.dead) {
            if (chanPostCache.getThreadPost(linkable.postDescriptor) == null) {
              snackbarManager.toast(
                messageId = R.string.thread_toolbar_cannot_view_dead_posts,
                screenKey = sourceScreenKey
              )

              return@launch
            }
          }

          if (linkable.crossThread) {
            val postDescriptorReadable = linkable.postDescriptor.asReadableString()

            navigationRouter.presentScreen(
              DialogScreen(
                dialogKey = DialogScreen.OPEN_EXTERNAL_THREAD,
                componentActivity = componentActivity,
                navigationRouter = navigationRouter,
                params = DialogScreen.Params(
                  title = DialogScreen.Text.Id(R.string.thread_toolbar_open_external_thread_dialog_title),
                  description = DialogScreen.Text.String(
                    context.resources.getString(
                      R.string.thread_toolbar_open_external_thread_dialog_description,
                      postDescriptorReadable
                    )
                  ),
                  negativeButton = DialogScreen.cancelButton(),
                  positiveButton = DialogScreen.okButton {
                    crossThreadFollowHistory.push(postCellData.postDescriptor.threadDescriptor)

                    loadThreadFunc(linkable.postDescriptor.threadDescriptor)
                  }
                )
              )
            )

            return@launch
          }

          val replyTo = PopupPostsScreen.PopupPostViewMode.ReplyTo(
            chanDescriptor = postCellData.chanDescriptor,
            postDescriptor = linkable.postDescriptor
          )

          showRepliesForPostFunc(replyTo)
        }
        is TextPartSpan.Linkable.Board -> {
          val catalogDescriptor = CatalogDescriptor(
            siteKey = postCellData.postDescriptor.siteKey,
            boardCode = linkable.boardCode
          )

          loadCatalogFunc(catalogDescriptor)
        }
        is TextPartSpan.Linkable.Search -> {
          // TODO(KurobaEx):
        }
        is TextPartSpan.Linkable.Url -> {
          // TODO(KurobaEx): show dialog?
          // TODO(KurobaEx): open in media viewer if the link ends with a recognizable media extension.
          androidHelpers.openLink(context, linkable.url)
        }
      }
    }
  }

  companion object {
    private const val TAG = "LinkableClickHelper"
  }
}