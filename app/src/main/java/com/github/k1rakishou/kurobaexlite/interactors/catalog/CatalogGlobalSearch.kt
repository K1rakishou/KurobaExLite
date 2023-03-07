package com.github.k1rakishou.kurobaexlite.interactors.catalog

import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.local.ParsedPostDataContext
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.GlobalSearchRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.ParsedPostDataRepository
import com.github.k1rakishou.kurobaexlite.ui.themes.IThemeEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.logcat

class CatalogGlobalSearch(
  private val globalSearchRepository: GlobalSearchRepository,
  private val parsedPostDataRepository: ParsedPostDataRepository,
  private val postHideRepository: IPostHideRepository,
  private val postCommentApplier: PostCommentApplier,
  private val themeEngine: IThemeEngine,
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
    val postViewMode = PostViewMode.List

    // TODO(KurobaEx): multi-threading?
    val parsedPosts = withContext(dispatcher) {
      return@withContext posts.map { postData ->
        val parsedPostData = parsedPostDataRepository.calculateParsedPostData(
          postData = postData,
          parsedPostDataContext = ParsedPostDataContext(
            isParsingCatalog = true,
            postViewMode = postViewMode,
            revealFullPostComment = true
          ),
          chanTheme = chanTheme
        )

        val (foundOccurrencesInComment, newProcessedPostComment) = postCommentApplier.markOrUnmarkSearchQuery(
          chanTheme = chanTheme,
          searchQuery = searchQuery,
          minQueryLength = 1,
          string = parsedPostData.processedPostComment
        )

        val (foundOccurrencesInSubject, newProcessedPostSubject) = postCommentApplier.markOrUnmarkSearchQuery(
          chanTheme = chanTheme,
          searchQuery = searchQuery,
          minQueryLength = 1,
          string = parsedPostData.processedPostSubject
        )

        val updatedParsedPostData = if (foundOccurrencesInComment || foundOccurrencesInSubject) {
          parsedPostData.copy(
            processedPostComment = newProcessedPostComment,
            processedPostSubject = newProcessedPostSubject,
          )
        } else {
          parsedPostData
        }

        val postHideUi = postHideRepository.postHideForPostDescriptor(postData.postDescriptor)
          ?.toPostHideUi()

        return@map PostCellData.fromPostData(
          chanDescriptor = catalogDescriptor,
          postData = postData,
          parsedPostData = updatedParsedPostData,
          postHideUi = postHideUi
        )
      }
    }

    return Result.success(parsedPosts)
  }

  companion object {
    private const val TAG = "CatalogGlobalSearch"
  }

}