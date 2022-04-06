package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared

import android.content.Context
import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.reply.PopupRepliesScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.CrossThreadFollowHistory
import org.koin.java.KoinJavaComponent.inject

class LinkableClickHelper(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter
) {
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val crossThreadFollowHistory: CrossThreadFollowHistory by inject(CrossThreadFollowHistory::class.java)

  fun processClickedLinkable(
    context: Context,
    postCellData: PostCellData,
    linkable: PostCommentParser.TextPartSpan.Linkable,
    loadThreadFunc: (ThreadDescriptor, PostScreenViewModel.LoadOptions) -> Unit,
    loadCatalogFunc: (CatalogDescriptor, PostScreenViewModel.LoadOptions) -> Unit,
    showRepliesForPostFunc: (PopupRepliesScreen.ReplyViewMode) -> Unit
  ) {
    when (linkable) {
      is PostCommentParser.TextPartSpan.Linkable.Quote -> {
        if (linkable.crossThread) {
          val postDescriptorReadable = linkable.postDescriptor.asReadableString()

          navigationRouter.presentScreen(
            DialogScreen(
              dialogKey = DialogScreen.OPEN_EXTERNAL_THREAD,
              componentActivity = componentActivity,
              navigationRouter = navigationRouter,
              params = DialogScreen.Params(
                title = DialogScreen.Text.Id(R.string.thread_screen_open_external_thread_dialog_title),
                description = DialogScreen.Text.String(
                  context.resources.getString(
                    R.string.thread_screen_open_external_thread_dialog_description,
                    postDescriptorReadable
                  )
                ),
                negativeButton = DialogScreen.cancelButton(),
                positiveButton = DialogScreen.okButton {
                  crossThreadFollowHistory.push(postCellData.postDescriptor.threadDescriptor)

                  loadThreadFunc(
                    linkable.postDescriptor.threadDescriptor,
                    PostScreenViewModel.LoadOptions(forced = true)
                  )
                }
              )
            )
          )

          return
        }

        val replyTo = PopupRepliesScreen.ReplyViewMode.ReplyTo(linkable.postDescriptor)
        showRepliesForPostFunc(replyTo)
      }
      is PostCommentParser.TextPartSpan.Linkable.Board -> {
        val catalogDescriptor = CatalogDescriptor(
          siteKey = postCellData.postDescriptor.siteKey,
          boardCode = linkable.boardCode
        )

        loadCatalogFunc(
          catalogDescriptor,
          PostScreenViewModel.LoadOptions(forced = true)
        )
      }
      is PostCommentParser.TextPartSpan.Linkable.Search -> {
        // TODO()
      }
      is PostCommentParser.TextPartSpan.Linkable.Url -> {
        // TODO(KurobaEx): show dialog?
        androidHelpers.openLink(context, linkable.url)
      }
    }
  }


}