package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.AbstractPostsState

class CatalogThreadsState(
  catalogDescriptor: CatalogDescriptor,
  catalogThreads: List<PostData>
) : AbstractPostsState(catalogDescriptor) {
  private val _catalogThreads = mutableStateListOf<MutableState<PostData>>()

  override val postsMutable: SnapshotStateList<MutableState<PostData>>
    get() = _catalogThreads
  override val posts: List<State<PostData>>
    get() = _catalogThreads

  init {
    _catalogThreads.addAll(catalogThreads.map { mutableStateOf(it) })
  }

}