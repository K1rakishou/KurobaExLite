package com.github.k1rakishou.kurobaexlite.interactors.catalog

import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.repoository.GlobalSearchRepository
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.logcat

class CatalogGlobalSearch(
  private val globalSearchRepository: GlobalSearchRepository,
  private val parsedPostDataCache: ParsedPostDataCache,
  private val themeEngine: ThemeEngine,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

  suspend fun await(
    catalogDescriptor: CatalogDescriptor,
    isSiteWideSearch: Boolean,
    searchQuery: String?,
    page: Int,
  ): Result<List<PostCellData>> {
    logcat(TAG) {
      "Searching ${catalogDescriptor} with query '$searchQuery' and page $page start, " +
        "isSiteWideSearch=${isSiteWideSearch}"
    }

    val searchResult = globalSearchRepository.getPage(
      catalogDescriptor = catalogDescriptor,
      isSiteWideSearch = isSiteWideSearch,
      searchQuery = searchQuery,
      pageIndex = page
    )

    val posts = if (searchResult.isFailure) {
      logcatError(TAG) { "Got error: ${searchResult.exceptionOrThrow().errorMessageOrClassName()}" }
      return Result.failure(searchResult.exceptionOrThrow())
    } else {
      searchResult.getOrThrow().foundPosts
    }

    logcat(TAG) {
      "Searching ${catalogDescriptor} with query '$searchQuery' and page $page end, " +
        "got ${posts.size} posts"
    }

    if (posts.isEmpty()) {
      return Result.success(emptyList())
    }

    val chanTheme = themeEngine.chanTheme

    val parsedPosts = withContext(dispatcher) {
      return@withContext posts.map { postData ->
        val parsedPostData = parsedPostDataCache.calculateParsedPostData(
          postData = postData,
          parsedPostDataContext = ParsedPostDataContext(isParsingCatalog = true),
          chanTheme = chanTheme
        )

        return@map PostCellData.fromPostData(
          postData = postData,
          parsedPostData = parsedPostData
        )
      }
    }

    return Result.success(parsedPosts)
  }

  companion object {
    private const val TAG = "CatalogGlobalSearch"
  }

}