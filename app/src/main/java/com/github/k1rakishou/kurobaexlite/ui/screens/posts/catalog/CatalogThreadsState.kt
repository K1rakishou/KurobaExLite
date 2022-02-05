package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.IPostsState
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostsMergeResult

class CatalogThreadsState(
  val catalogDescriptor: CatalogDescriptor,
  catalogThreads: List<PostData>
) : IPostsState {
  private val _catalogThreads = mutableStateListOf<MutableState<PostData>>()

  override val posts: List<State<PostData>>
    get() = _catalogThreads

  init {
    _catalogThreads.addAll(catalogThreads.map { mutableStateOf(it) })
  }

  override fun update(postData: PostData) {
    val index = _catalogThreads.indexOfFirst { it.value.postDescriptor == postData.postDescriptor }
    if (index < 0) {
      return
    }

    _catalogThreads[index].value = postData
  }

  override fun mergePostsWith(newThreadPosts: List<PostData>): PostsMergeResult {
    return NO_OP_MERGE_RESULT
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CatalogThreadsState

    if (catalogDescriptor != other.catalogDescriptor) return false
    if (_catalogThreads != other._catalogThreads) return false

    return true
  }

  override fun hashCode(): Int {
    var result = catalogDescriptor.hashCode()
    result = 31 * result + _catalogThreads.hashCode()
    return result
  }

  companion object {
    private val NO_OP_MERGE_RESULT = PostsMergeResult(0, emptyList())
  }

}