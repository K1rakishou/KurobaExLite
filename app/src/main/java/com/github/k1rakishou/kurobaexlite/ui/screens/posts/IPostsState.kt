package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import androidx.compose.runtime.State
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor

interface IPostsState {
  val chanDescriptor: ChanDescriptor
  val posts: List<State<PostData>>

  fun update(postData: PostData)
  fun mergePostsWith(newThreadPosts: List<PostData>): PostsMergeResult
}

data class PostsMergeResult(
  val newPostsCount: Int,
  val newOrUpdatedPostsToReparse: List<PostData>
)