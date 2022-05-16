package com.github.k1rakishou.kurobaexlite.model.source.chan4

import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.suspendConvertWithHtmlReader
import com.github.k1rakishou.kurobaexlite.helpers.suspendConvertWithJsonAdapter
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogPagesData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogsData
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostImageData
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchParams
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchResult
import com.github.k1rakishou.kurobaexlite.model.data.local.StickyThread
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadBookmarkData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadBookmarkInfoPostObject
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.BoardsDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.CatalogPageDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.CatalogPageJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.PostImageDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.ThreadBookmarkInfoJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.ThreadDataJson
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.IBoardDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IBookmarkDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogPagesDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IGlobalSearchDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IThreadDataSource
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class Chan4DataSource(
  private val siteManager: SiteManager,
  private val kurobaOkHttpClient: ProxiedOkHttpClient,
  private val moshi: Moshi
) : ICatalogDataSource<CatalogDescriptor, CatalogData>,
  IThreadDataSource<ThreadDescriptor, ThreadData>,
  IBoardDataSource<SiteKey, CatalogsData>,
  IBookmarkDataSource<ThreadDescriptor, ThreadBookmarkData>,
  ICatalogPagesDataSource<CatalogDescriptor, CatalogPagesData?>,
  IGlobalSearchDataSource<SearchParams, SearchResult> {

  override suspend fun loadThread(
    threadDescriptor: ThreadDescriptor,
  ): Result<ThreadData> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val siteKey = threadDescriptor.catalogDescriptor.siteKey
        val boardCode = threadDescriptor.catalogDescriptor.boardCode
        val threadNo = threadDescriptor.threadNo

        val site = siteManager.bySiteKey(siteKey)
          ?: throw ChanDataSourceException("Unsupported site: ${siteKey}")

        val threadInfo = site.threadInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support threads")
        val postImageInfo = site.postImageInfo()

        val threadUrl = threadInfo.threadUrl(boardCode, threadNo)
        logcat(TAG, LogPriority.VERBOSE) { "loadThread() url='$threadUrl'" }

        val request = Request.Builder()
          .url(threadUrl)
          .get()
          .build()

        val threadDataJsonJsonAdapter = moshi.adapter<ThreadDataJson>(ThreadDataJson::class.java)

        val threadDataJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          threadDataJsonJsonAdapter
        )

        val threadDataJson = threadDataJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert thread json into ThreadDataJson object")

        val postDataList = mutableListWithCap<IPostData>(initialCapacity = threadDataJson.posts.size)

        postDataList += threadDataJson.posts.mapIndexed { index, threadPost ->
          val postDescriptor = PostDescriptor.create(
            siteKey = site.siteKey,
            boardCode = boardCode,
            threadNo = threadNo,
            postNo = threadPost.no
          )

          if (postDescriptor.isOP) {
            return@mapIndexed OriginalPostData(
              originalPostOrder = index,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.sub ?: "",
              postCommentUnparsed = threadPost.com ?: "",
              timeMs = threadPost.time?.times(1000L),
              images = parsePostImages(
                postDescriptor = postDescriptor,
                postImageInfo = postImageInfo,
                postImageDataJson = threadPost,
                boardCode = boardCode
              ),
              threadRepliesTotal = threadPost.replies,
              threadImagesTotal = threadPost.images,
              threadPostersTotal = threadPost.posters,
              lastModified = threadPost.lastModified,
              archived = threadPost.archived?.let { archived -> archived == 1 },
              closed = threadPost.closed?.let { closed -> closed == 1 },
              sticky = threadPost.sticky?.let { sticky -> sticky == 1 },
              bumpLimit = threadPost.bumpLimit?.let { bumpLimit -> bumpLimit == 1 },
              imageLimit = threadPost.imageLimit?.let { imageLimit -> imageLimit == 1 },
            )
          } else {
            return@mapIndexed PostData(
              originalPostOrder = index,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.sub ?: "",
              postCommentUnparsed = threadPost.com ?: "",
              timeMs = threadPost.time?.times(1000L),
              images = parsePostImages(
                postDescriptor = postDescriptor,
                postImageInfo = postImageInfo,
                postImageDataJson = threadPost,
                boardCode = boardCode
              ),
              lastModified = null,
              archived = null,
              closed = null,
              sticky = null,
              bumpLimit = null,
              imageLimit = null,
            )
          }
        }

        return@Try ThreadData(
          threadDescriptor = threadDescriptor,
          threadPosts = postDataList
        )
      }
    }
  }

  override suspend fun loadCatalog(catalogDescriptor: CatalogDescriptor): Result<CatalogData> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val siteKey = catalogDescriptor.siteKey
        val boardCode = catalogDescriptor.boardCode

        val site = siteManager.bySiteKey(siteKey)
          ?: throw ChanDataSourceException("Unsupported site: ${siteKey}")

        val catalogInfo = site.catalogInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support catalog")
        val postImageInfo = site.postImageInfo()

        val catalogUrl = catalogInfo.catalogUrl(boardCode)
        logcat(TAG, LogPriority.VERBOSE) { "loadCatalog() url='$catalogUrl'" }

        val request = Request.Builder()
          .url(catalogUrl)
          .get()
          .build()

        val catalogPagesDataJsonAdapter = moshi.adapter<List<CatalogPageDataJson>>(
          Types.newParameterizedType(List::class.java, CatalogPageDataJson::class.java),
        )

        val catalogPagesDataJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          catalogPagesDataJsonAdapter
        )

        val catalogPagesDataJson = catalogPagesDataJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert catalog json into CatalogDataJson object")

        val totalCount = catalogPagesDataJson.sumOf { catalogPageData -> catalogPageData.threads.size }
        val postDataList = mutableListWithCap<IPostData>(initialCapacity = totalCount)

        catalogPagesDataJson.forEachIndexed { page, catalogPage ->
          postDataList += catalogPage.threads.mapIndexed { index, catalogThread ->
            val postDescriptor = PostDescriptor.create(
              siteKey = site.siteKey,
              boardCode = boardCode,
              threadNo = catalogThread.no,
              postNo = catalogThread.no
            )

            return@mapIndexed OriginalPostData(
              originalPostOrder = page * index,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = catalogThread.sub ?: "",
              postCommentUnparsed = catalogThread.com ?: "",
              timeMs = catalogThread.time?.times(1000L),
              images = parsePostImages(
                postDescriptor = postDescriptor,
                postImageInfo = postImageInfo,
                postImageDataJson = catalogThread,
                boardCode = boardCode
              ),
              threadRepliesTotal = catalogThread.replies,
              threadImagesTotal = catalogThread.images,
              threadPostersTotal = catalogThread.posters,
              lastModified = catalogThread.lastModified,
              archived = catalogThread.archived?.let { archived -> archived == 1 },
              closed = catalogThread.closed?.let { closed -> closed == 1 },
              sticky = catalogThread.sticky?.let { sticky -> sticky == 1 },
              bumpLimit = catalogThread.bumpLimit?.let { bumpLimit -> bumpLimit == 1 },
              imageLimit = catalogThread.imageLimit?.let { imageLimit -> imageLimit == 1 },
            )
          }
        }

        return@Try CatalogData(
          catalogDescriptor = catalogDescriptor,
          catalogThreads = postDataList
        )
      }
    }
  }

  override suspend fun loadBoards(input: SiteKey): Result<CatalogsData> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val site = siteManager.bySiteKey(input)
          ?: throw ChanDataSourceException("Unsupported site: ${input}")

        val boardsInfo = site.boardsInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support boards list")

        val boardsUrl = boardsInfo.boardsUrl()
        logcat(TAG, LogPriority.VERBOSE) { "loadBoards() url='$boardsUrl'" }

        val request = Request.Builder()
          .url(boardsUrl)
          .get()
          .build()

        val boardsDataJsonAdapter = moshi.adapter<BoardsDataJson>(BoardsDataJson::class.java)
        val boardsDataJsonAdapterResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          boardsDataJsonAdapter
        )

        val boardsDataJson = boardsDataJsonAdapterResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert boards json into BoardDataJson object")

        val chanBoards = boardsDataJson.boards.mapNotNull { boardDataJson ->
          val boardCode = boardDataJson.boardCode ?: return@mapNotNull null
          val boardTitle = boardDataJson.boardTitle
          val boardDescription = boardDataJson.boardDescription?.let { HtmlUnescape.unescape(it) }

          return@mapNotNull ChanCatalog(
            catalogDescriptor = CatalogDescriptor(input, boardCode),
            boardTitle = boardTitle,
            boardDescription = boardDescription,
            workSafe = boardDataJson.workSafe == 1
          )
        }

        return@Try CatalogsData(chanBoards)
      }
    }
  }

  override suspend fun loadBookmarkData(input: ThreadDescriptor): Result<ThreadBookmarkData> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val site = siteManager.bySiteKey(input.siteKey)
          ?: throw ChanDataSourceException("Unsupported site: ${input}")

        val bookmarkInfo = site.bookmarkInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support bookmarks")

        val bookmarkUrl = bookmarkInfo.bookmarkUrl(
          boardCode = input.boardCode,
          threadNo = input.threadNo
        )
        logcat(TAG, LogPriority.VERBOSE) { "loadBookmarkData() url='$bookmarkUrl'" }

        val request = Request.Builder()
          .url(bookmarkUrl)
          .get()
          .also { requestBuilder ->
            site.requestModifier().modifyCatalogOrThreadGetRequest(
              site = site,
              chanDescriptor = input,
              requestBuilder = requestBuilder
            )
          }
          .build()

        val threadBookmarkInfoJsonAdapter = moshi.adapter<ThreadBookmarkInfoJson>(ThreadBookmarkInfoJson::class.java)
        val threadBookmarkInfoJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          threadBookmarkInfoJsonAdapter
        )

        val boardsDataJson = threadBookmarkInfoJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert thread json into ThreadBookmarkInfoJson object")

        val threadBookmarkInfoPostObjects = boardsDataJson.postInfoForBookmarkList.map { postInfoForBookmarkJson ->
          if (postInfoForBookmarkJson.isOp) {
            val stickyPost = StickyThread.create(
              isSticky = postInfoForBookmarkJson.isSticky,
              stickyCap = postInfoForBookmarkJson.stickyCap ?: -1
            )

            return@map ThreadBookmarkInfoPostObject.OriginalPost(
              postDescriptor = PostDescriptor.create(input, postInfoForBookmarkJson.postNo),
              closed = postInfoForBookmarkJson.isClosed,
              archived = postInfoForBookmarkJson.isArchived,
              isBumpLimit = postInfoForBookmarkJson.isBumpLimit,
              isImageLimit = postInfoForBookmarkJson.isImageLimit,
              stickyThread = stickyPost,
              comment = postInfoForBookmarkJson.comment
            )
          } else {
            return@map ThreadBookmarkInfoPostObject.RegularPost(
              postDescriptor = PostDescriptor.create(input, postInfoForBookmarkJson.postNo),
              comment = postInfoForBookmarkJson.comment
            )
          }
        }

        return@Try ThreadBookmarkData(
          threadDescriptor = input,
          postObjects = threadBookmarkInfoPostObjects
        )
      }
    }
  }

  override suspend fun loadCatalogPagesData(input: CatalogDescriptor): Result<CatalogPagesData?> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val site = siteManager.bySiteKey(input.siteKey)
          ?: throw ChanDataSourceException("Unsupported site: ${input}")

        val catalogPagesInfo = site.catalogPagesInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support catalogPagesInfo")

        val catalogPagesUrl = catalogPagesInfo.catalogPagesUrl(input.boardCode)
        logcat(TAG, LogPriority.VERBOSE) { "loadCatalogPagesData() url='$catalogPagesUrl'" }

        val request = Request.Builder()
          .url(catalogPagesUrl)
          .get()
          .build()

        val catalogPageJsonListType = Types.newParameterizedType(List::class.java, CatalogPageJson::class.java)
        val catalogPageJsonListAdapter = moshi.adapter<List<CatalogPageJson>>(catalogPageJsonListType)

        val catalogPageJsonListResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          catalogPageJsonListAdapter
        )

        val catalogPageJsonList = catalogPageJsonListResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert catalog pages json into catalogPageJsonList object")

        if (catalogPageJsonList.isEmpty()) {
          return@Try null
        }

        val pagesInfoMap = mutableMapWithCap<ThreadDescriptor, Int>(100)

        catalogPageJsonList.forEach { catalogPageJson ->
          val page = catalogPageJson.page

          catalogPageJson.threads.forEach { catalogPageThreadJson ->
            val threadDescriptor = ThreadDescriptor.create(input, catalogPageThreadJson.postNo)
            pagesInfoMap[threadDescriptor] = page
          }
        }

        return@Try CatalogPagesData(
          pagesTotal = catalogPageJsonList.size,
          pagesInfo = pagesInfoMap
        )
      }
    }
  }

  override suspend fun loadSearchPageData(input: SearchParams): Result<SearchResult> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val site = siteManager.bySiteKey(input.catalogDescriptor.siteKey)
          ?: throw ChanDataSourceException("Unsupported site: ${input}")

        val globalSearchInfo = site.globalSearchInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support globalSearchInfo")

        val globalSearchUrl = if (input.isSiteWideSearch) {
          globalSearchInfo.globalSearchUrl(
            query = input.query,
            page = input.page
          )
        } else {
          globalSearchInfo.globalSearchUrl(
            boardCode = input.catalogDescriptor.boardCode,
            query = input.query,
            page = input.page
          )
        }

        logcat(TAG, LogPriority.VERBOSE) { "loadSearchPageData() url='$globalSearchUrl'" }

        val request = Request.Builder()
          .url(globalSearchUrl)
          .get()
          .build()

        val htmlReader = Chan4SearchHtmlReader(
          catalogDescriptor = input.catalogDescriptor,
          currentOffset = input.page * globalSearchInfo.resultsPerPage
        )

        return@Try kurobaOkHttpClient.okHttpClient().suspendConvertWithHtmlReader(request, htmlReader)
          .unwrap()
      }
    }
  }

  private fun parsePostImages(
    postDescriptor: PostDescriptor,
    postImageInfo: Site.PostImageInfo?,
    postImageDataJson: PostImageDataJson,
    boardCode: String
  ): List<PostImageData> {
    if (postImageInfo == null || !postImageDataJson.hasImage()) {
      return emptyList()
    }

    val extension = postImageDataJson.ext
      ?.removePrefix(".")
      ?: "jpg"

    val thumbnailUrl = postImageInfo.thumbnailUrl(
      boardCode = boardCode,
      tim = postImageDataJson.tim!!,
      extension = "jpg"
    ).toHttpUrlOrNull()
      ?: return emptyList()

    val fullUrl = postImageInfo.fullUrl(
      boardCode = boardCode,
      tim = postImageDataJson.tim!!,
      extension = extension
    ).toHttpUrlOrNull()
      ?: return emptyList()

    val serverFileName = postImageDataJson.tim.toString()
    val originalFileName = postImageDataJson.filename
      ?.takeIf { filename -> filename.isNotNullNorBlank() }
      ?: serverFileName

    val postImageData = PostImageData(
      thumbnailUrl = thumbnailUrl,
      fullImageUrl = fullUrl,
      originalFileNameEscaped = originalFileName,
      serverFileName = serverFileName,
      ext = extension,
      width = postImageDataJson.w!!,
      height = postImageDataJson.h!!,
      fileSize = postImageDataJson.fsize!!,
      ownerPostDescriptor = postDescriptor
    )

    return listOf(postImageData)
  }

  class ChanDataSourceException(message: String) : ClientException(message)

  companion object {
    private const val TAG = "Chan4DataSource"
  }

}