package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.AbstractPostsState

class CatalogThreadsState(
  val catalogDescriptor: CatalogDescriptor,
  catalogThreads: List<PostData>
) : AbstractPostsState() {
  private val _catalogThreads = mutableStateListOf<MutableState<PostData>>()

  override val postsMutable: SnapshotStateList<MutableState<PostData>>
    get() = _catalogThreads
  override val posts: List<State<PostData>>
    get() = _catalogThreads
  override val chanDescriptor: ChanDescriptor
    get() = catalogDescriptor

  init {
    _catalogThreads.addAll(catalogThreads.map { mutableStateOf(it) })
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

}