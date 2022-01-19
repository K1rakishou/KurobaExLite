package com.github.k1rakishou.kurobaexlite.ui.screens.catalog

import androidx.compose.runtime.*
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.model.CatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Chan4
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import logcat.asLog

class CatalogScreenViewModel(
  private val catalogDataSource: CatalogDataSource,
  private val postCommentParser: PostCommentParser,
  private val postCommentApplier: PostCommentApplier
) : BaseViewModel() {
  val catalogScreenState = CatalogScreenState()

  suspend fun loadCatalog(
    catalogDescriptor: CatalogDescriptor = CatalogDescriptor(Chan4.SITE_KEY, "a")
  ) {
    catalogScreenState.catalogThreadsAsync = AsyncData.Loading

    val catalogDataResult = catalogDataSource.loadCatalog(catalogDescriptor)
    if (catalogDataResult.isFailure) {
      val error = catalogDataResult.exceptionOrThrow()
      logcatError { "loadCatalog() error=${error.asLog()}" }

      catalogScreenState.catalogThreadsAsync = AsyncData.Error(error)
      return
    }

    val catalogData = catalogDataResult.unwrap()
    if (catalogData.catalogThreads.isEmpty()) {
      val error = CatalogDisplayException("Catalog /${catalogDescriptor.boardCode}/ has no posts")
      catalogScreenState.catalogThreadsAsync = AsyncData.Error(error)
      return
    }

    catalogScreenState.catalogThreadsAsync = AsyncData.Data(
      CatalogThreadsState(catalogThreads = catalogData.catalogThreads)
    )
  }

  suspend fun parseComment(chanTheme: ChanTheme, postData: PostData): AnnotatedString {
    val textParts = postCommentParser.parsePostComment(postData)
    return postCommentApplier.textPartsToAnnotatedString(chanTheme, textParts)
  }

  class CatalogDisplayException(message: String) : ClientException(message)

  class CatalogScreenState(
    private val catalogThreadsAsyncState: MutableState<AsyncData<CatalogThreadsState>> = mutableStateOf(AsyncData.Loading)
  ) {
    internal var catalogThreadsAsync by catalogThreadsAsyncState
  }

  class CatalogThreadsState(
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