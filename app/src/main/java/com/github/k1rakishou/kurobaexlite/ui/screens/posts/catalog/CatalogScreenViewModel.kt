package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.compose.runtime.*
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.model.ChanDataSource
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Chan4
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostScreenViewModel
import logcat.asLog

class CatalogScreenViewModel(
  private val chanDataSource: ChanDataSource,
  postCommentParser: PostCommentParser,
  postCommentApplier: PostCommentApplier
) : PostScreenViewModel(postCommentParser, postCommentApplier) {
  private val catalogScreenState = CatalogScreenState()
  override val postScreenState: PostScreenState = catalogScreenState

  override fun reload() {
    // TODO(KurobaEx): 
  }

  suspend fun loadCatalog(
    catalogDescriptor: CatalogDescriptor = CatalogDescriptor(Chan4.SITE_KEY, "a"),
    forced: Boolean = false
  ) {
    if (!forced && catalogScreenState.currentCatalogDescriptorOrNull == catalogDescriptor) {
      return
    }

    catalogScreenState.catalogThreadsAsync = AsyncData.Loading

    val catalogDataResult = chanDataSource.loadCatalog(catalogDescriptor)
    if (catalogDataResult.isFailure) {
      val error = catalogDataResult.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      catalogScreenState.catalogThreadsAsync = AsyncData.Error(error)
      return
    }

    val catalogData = catalogDataResult.unwrap()
    if (catalogData.catalogThreads.isEmpty()) {
      val error = CatalogDisplayException("Catalog /${catalogDescriptor}/ has no posts")
      catalogScreenState.catalogThreadsAsync = AsyncData.Error(error)
      return
    }

    val catalogThreadsState = CatalogThreadsState(
      catalogDescriptor = catalogDescriptor,
      catalogThreads = catalogData.catalogThreads
    )

    catalogScreenState.catalogThreadsAsync = AsyncData.Data(catalogThreadsState)
  }

  class CatalogDisplayException(message: String) : ClientException(message)

  class CatalogScreenState(
    private val catalogThreadsAsyncState: MutableState<AsyncData<CatalogThreadsState>> = mutableStateOf(AsyncData.Empty)
  ) : PostScreenState {
    internal var catalogThreadsAsync by catalogThreadsAsyncState

    val currentCatalogDescriptorOrNull: CatalogDescriptor?
      get() = (catalogThreadsAsync as? AsyncData.Data)?.data?.catalogDescriptor

    override fun postDataAsync(): AsyncData<List<PostData>> {
      return when (val asyncDataStateValue = catalogThreadsAsyncState.value) {
        is AsyncData.Data -> AsyncData.Data(asyncDataStateValue.data.catalogThreads)
        is AsyncData.Error -> AsyncData.Error(asyncDataStateValue.error)
        AsyncData.Empty -> AsyncData.Empty
        AsyncData.Loading -> AsyncData.Loading
      }
    }
  }

  class CatalogThreadsState(
    val catalogDescriptor: CatalogDescriptor,
    catalogThreads: List<PostData>
  ) {
    private val _catalogThreads = mutableStateListOf<PostData>()
    val catalogThreads: List<PostData>
      get() = _catalogThreads

    init {
      _catalogThreads.addAll(catalogThreads)
    }
  }

}