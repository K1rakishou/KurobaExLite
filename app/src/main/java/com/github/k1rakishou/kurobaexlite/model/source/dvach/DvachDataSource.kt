package com.github.k1rakishou.kurobaexlite.model.source.dvach

import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendConvertWithJsonAdapter
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.interactors.catalog.LoadChanCatalog
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.ChanDataSourceException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.PostDataSticky
import com.github.k1rakishou.kurobaexlite.model.data.PostIcon
import com.github.k1rakishou.kurobaexlite.model.data.local.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogPagesData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogsData
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.data.local.DvachLoginDetails
import com.github.k1rakishou.kurobaexlite.model.data.local.DvachLoginResult
import com.github.k1rakishou.kurobaexlite.model.data.local.OriginalPostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchParams
import com.github.k1rakishou.kurobaexlite.model.data.local.SearchResult
import com.github.k1rakishou.kurobaexlite.model.data.local.StickyThread
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadBookmarkData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadBookmarkInfoPostObject
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachBoardDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachBoardIconJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachBookmarkCatalogInfo
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachCatalog
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachCatalogPageJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachPasscodeResult
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachPost
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachSearchResult
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachThreadFull
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachThreadPartial
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.IBoardDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IBookmarkDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogPagesDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IGlobalSearchDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ILoginDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ILogoutDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IThreadDataSource
import com.github.k1rakishou.kurobaexlite.sites.dvach.Dvach
import com.github.k1rakishou.kurobaexlite.sites.settings.DvachSiteSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.HttpCookie
import kotlin.math.ceil

class DvachDataSource(
  private val siteManager: SiteManager,
  private val kurobaOkHttpClient: IKurobaOkHttpClient,
  private val moshi: Moshi,
  private val loadChanCatalog: LoadChanCatalog
) : ICatalogDataSource<CatalogDescriptor, CatalogData>,
  IThreadDataSource<ThreadDescriptor, ThreadData>,
  IBoardDataSource<SiteKey, CatalogsData>,
  IBookmarkDataSource<ThreadDescriptor, ThreadBookmarkData>,
  ICatalogPagesDataSource<CatalogDescriptor, CatalogPagesData?>,
  IGlobalSearchDataSource<SearchParams, SearchResult>,
  ILoginDataSource<DvachLoginDetails, DvachLoginResult>,
  ILogoutDataSource<Unit, Unit> {

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
          .also { requestBuilder ->
            site.requestModifier().modifyGetBoardsRequest(
              requestBuilder = requestBuilder
            )
          }
          .build()

        val dvachBoardDataListJsonAdapter = Types.newParameterizedType(List::class.java, DvachBoardDataJson::class.java)

        val boardsDataJsonAdapter = moshi.adapter<List<DvachBoardDataJson>>(dvachBoardDataListJsonAdapter)
        val boardsDataJsonAdapterResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          boardsDataJsonAdapter
        )

        val boardsDataJson = boardsDataJsonAdapterResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert boards json into BoardDataJson object")

        val chanBoards = boardsDataJson.mapNotNull { boardDataJson ->
          val boardCode = boardDataJson.boardCode ?: return@mapNotNull null
          val boardTitle = boardDataJson.boardTitle
          val boardDescription = boardDataJson.boardDescription?.let { HtmlUnescape.unescape(it) }

          return@mapNotNull ChanCatalog(
            catalogDescriptor = CatalogDescriptor(input, boardCode),
            boardTitle = boardTitle,
            boardDescription = boardDescription,
            workSafe = boardDataJson.workSafe,
            maxAttachFilesPerPost = 4,
            flags = parseSupportedIcons(boardDataJson.icons),
            bumpLimit = boardDataJson.bumpLimit
          )
        }

        return@Try CatalogsData(chanBoards)
      }
    }
  }

  override suspend fun loadCatalog(input: CatalogDescriptor): Result<CatalogData> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val siteKey = input.siteKey
        val boardCode = input.boardCode

        val site = siteManager.bySiteKey(siteKey)
          ?: throw ChanDataSourceException("Unsupported site: ${siteKey}")

        val catalogInfo = site.catalogInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support catalog")
        val postImageInfo = site.postImageInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support post images/thumbnails")

        val catalogUrl = catalogInfo.catalogUrl(boardCode)
        logcat(TAG, LogPriority.VERBOSE) { "loadCatalog() url='$catalogUrl'" }

        val request = Request.Builder()
          .url(catalogUrl)
          .get()
          .also { requestBuilder ->
            site.requestModifier().modifyCatalogOrThreadGetRequest(
              chanDescriptor = input,
              requestBuilder = requestBuilder
            )
          }
          .build()

        val dvachCatalogJsonAdapter = moshi.adapter<DvachCatalog>(DvachCatalog::class.java)

        val dvachCatalogJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          dvachCatalogJsonAdapter
        )

        val dvachCatalog = dvachCatalogJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert catalog json into DvachCatalog object")

        if (dvachCatalog.error != null) {
          throw ChanDataSourceException("Failed to load catalog. Server returned error: \'${dvachCatalog.error.message()}\'")
        }

        val catalogThreads = dvachCatalog.threads ?: emptyList()
        val totalCount = catalogThreads.size
        val postDataList = mutableListWithCap<IPostData>(initialCapacity = totalCount)
        val defaultName = dvachCatalog.board?.defaultName ?: DEFAULT_NAME

        catalogThreads.forEach { catalogThread ->
          val postDescriptor = PostDescriptor.create(
            siteKey = site.siteKey,
            boardCode = boardCode,
            threadNo = catalogThread.num,
            postNo = catalogThread.num
          )

          val sticky = if (catalogThread.isSticky) {
            val maxCapacity = if (catalogThread.endless == 1) {
              1000
            } else {
              null
            }

            PostDataSticky(maxCapacity)
          } else {
            null
          }

          val bumpLimit = dvachCatalog.board
            ?.let { dvachBoardInfo -> dvachBoardInfo.bumpLimit != null && totalCount > dvachBoardInfo.bumpLimit }

          val images = catalogThread.files
            ?.mapNotNull { dvachFile ->
              return@mapNotNull dvachFile.toPostImageData(
                postImageInfo = postImageInfo,
                postDescriptor = postDescriptor
              )
            }
            ?: emptyList()

          val parsedFlags = parseFlags(catalogThread.icon)
          val parsedName = parseName(defaultName, catalogThread.name)

          postDataList += OriginalPostData(
            originalPostOrder = -1,
            postDescriptor = postDescriptor,
            postSubjectUnparsed = catalogThread.subject,
            postCommentUnparsed = processComment(catalogThread),
            opMark = catalogThread.opMark,
            sage = catalogThread.sage,
            name = parsedName.name,
            tripcode = catalogThread.trip,
            posterId = parsedName.posterId,
            countryFlag = parsedFlags.countryFlag,
            boardFlag = parsedFlags.boardFlag,
            timeMs = catalogThread.timestamp.times(1000L),
            images = images,
            threadRepliesTotal = catalogThread.postsCount,
            threadImagesTotal = catalogThread.filesCount,
            threadPostersTotal = catalogThread.postersCount,
            lastModified = catalogThread.lasthit,
            archived = false,
            deleted = false,
            closed = catalogThread.closed == 1,
            sticky = sticky,
            bumpLimit = bumpLimit,
            imageLimit = null,
          )
        }

        return@Try CatalogData(
          catalogDescriptor = input,
          catalogThreads = postDataList
        )
      }
    }
  }

  override suspend fun loadThread(
    input: ThreadDescriptor,
    lastCachedThreadPost: PostDescriptor?
  ): Result<ThreadData> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val siteKey = input.catalogDescriptor.siteKey
        val boardCode = input.catalogDescriptor.boardCode
        val threadNo = input.threadNo

        val site = siteManager.bySiteKey(siteKey)
          ?: throw ChanDataSourceException("Unsupported site: ${siteKey}")

        val threadInfo = site.threadInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support threads")
        val postImageInfo = site.postImageInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support post images/thumbnails")

        val threadUrl = if (lastCachedThreadPost == null) {
          threadInfo.fullThreadUrl(boardCode, threadNo)
        } else {
          threadInfo.partialThreadUrl(boardCode, threadNo, lastCachedThreadPost)
        }

        logcat(TAG, LogPriority.VERBOSE) { "loadThread() url='$threadUrl'" }

        val request = Request.Builder()
          .url(threadUrl)
          .get()
          .also { requestBuilder ->
            site.requestModifier().modifyCatalogOrThreadGetRequest(
              chanDescriptor = input,
              requestBuilder = requestBuilder
            )
          }
          .build()

        val dvachThread = if (lastCachedThreadPost == null) {
          val dvachThreadFullJsonAdapter = moshi.adapter<DvachThreadFull>(DvachThreadFull::class.java)

          val threadDataJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
            request,
            dvachThreadFullJsonAdapter
          )

          threadDataJsonResult.unwrap()
            ?: throw ChanDataSourceException("Failed to convert thread json into DvachThreadFull object")
        } else {
          val dvachThreadPartialJsonAdapter = moshi.adapter<DvachThreadPartial>(DvachThreadPartial::class.java)

          val threadDataJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
            request,
            dvachThreadPartialJsonAdapter
          )

          threadDataJsonResult.unwrap()
            ?: throw ChanDataSourceException("Failed to convert thread json into DvachThreadPartial object")
        }

        if (dvachThread.error != null) {
          throw ChanDataSourceException("Failed to load thread. Server returned error: \'${dvachThread.error!!.message()}\'")
        }

        val threadPosts = dvachThread.threadPosts ?: emptyList()
        val totalCount = threadPosts.size
        val postDataList = mutableListWithCap<IPostData>(initialCapacity = totalCount)

        val catalogBumpLimit = if (dvachThread is DvachThreadFull) {
          dvachThread.board?.bumpLimit
        } else {
          loadChanCatalog.await(chanDescriptor = input.catalogDescriptor).getOrNull()?.bumpLimit
        }

        val isBumpLimit = catalogBumpLimit != null && totalCount > catalogBumpLimit

        val threadRepliesTotal = (dvachThread as? DvachThreadFull)?.postsCount
        val threadImagesTotal = (dvachThread as? DvachThreadFull)?.filesCount
        val threadPostersTotal = (dvachThread as? DvachThreadFull)?.postersCount

        threadPosts.forEach { threadPost ->
          val threadNo = if (threadPost.parent == 0L) {
            threadPost.num
          } else {
            threadPost.parent
          }

          val postNo = threadPost.num

          val postDescriptor = PostDescriptor.create(
            siteKey = site.siteKey,
            boardCode = boardCode,
            threadNo = threadNo,
            postNo = postNo
          )

          val sticky = if (threadPost.isSticky) {
            val maxCapacity = if (threadPost.isEndless) {
              1000
            } else {
              0
            }

            PostDataSticky(maxCapacity)
          } else {
            null
          }

          val images = threadPost.files
            ?.mapNotNull { dvachFile ->
              return@mapNotNull dvachFile.toPostImageData(
                postImageInfo = postImageInfo,
                postDescriptor = postDescriptor
              )
            }
            ?: emptyList()

          val parsedFlags = parseFlags(threadPost.icon)
          val parsedName = parseName(DEFAULT_NAME, threadPost.name)

          postDataList += if (threadNo == postNo) {
            OriginalPostData(
              originalPostOrder = -1,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.subject,
              postCommentUnparsed = processComment(threadPost),
              opMark = threadPost.opMark,
              sage = threadPost.sage,
              name = parsedName.name,
              tripcode = threadPost.trip,
              posterId = parsedName.posterId,
              countryFlag = parsedFlags.countryFlag,
              boardFlag = parsedFlags.boardFlag,
              timeMs = threadPost.timestamp.times(1000L),
              images = images,
              threadRepliesTotal = threadRepliesTotal,
              threadImagesTotal = threadImagesTotal,
              threadPostersTotal = threadPostersTotal,
              lastModified = threadPost.lasthit,
              archived = false,
              deleted = false,
              closed = threadPost.closed == 1,
              sticky = sticky,
              bumpLimit = isBumpLimit,
              imageLimit = null,
            )
          } else {
            PostData(
              originalPostOrder = -1,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.subject,
              postCommentUnparsed = processComment(threadPost),
              opMark = threadPost.opMark,
              sage = threadPost.sage,
              name = parsedName.name,
              tripcode = threadPost.trip,
              posterId = parsedName.posterId,
              countryFlag = parsedFlags.countryFlag,
              boardFlag = parsedFlags.boardFlag,
              timeMs = threadPost.timestamp.times(1000L),
              images = images,
              threadRepliesTotal = null,
              threadImagesTotal = null,
              threadPostersTotal = null,
              archived = false,
              deleted = false,
              closed = false,
              sticky = null,
              bumpLimit = null,
              imageLimit = null,
            )
          }
        }

        return@Try ThreadData(
          threadDescriptor = input,
          threadPosts = postDataList
        )
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
              chanDescriptor = input,
              requestBuilder = requestBuilder
            )
          }
          .build()

        val threadBookmarkInfoJsonAdapter = moshi.adapter<DvachBookmarkCatalogInfo>(DvachBookmarkCatalogInfo::class.java)
        val threadBookmarkInfoJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          threadBookmarkInfoJsonAdapter
        )

        val boardsDataJson = threadBookmarkInfoJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert thread json into ThreadBookmarkInfoJson object")

        val thread = boardsDataJson.threads.firstOrNull()
          ?: throw ChanDataSourceException("Server returned no thread info")

        val threadBookmarkInfoPostObjects = thread.posts.map { postInfoForBookmarkJson ->
          if (postInfoForBookmarkJson.isOp) {
            val stickyCap = if (postInfoForBookmarkJson.isEndless) {
              1000
            } else {
              0
            }

            val stickyPost = StickyThread.create(
              isSticky = postInfoForBookmarkJson.isSticky,
              stickyCap = stickyCap
            )

            val isBumpLimit = boardsDataJson.board?.let { boardsDataJson ->
              val bumpLimit = loadChanCatalog.await(input.catalogDescriptor).getOrNull()?.bumpLimit ?: return@let false
              return@let boardsDataJson.bumpLimit > bumpLimit
            } ?: false

            val thumbnailParams = postInfoForBookmarkJson.firstFile?.thumbnail?.let { thumbnail ->
              ThreadBookmarkInfoPostObject.ThumbnailParams.Dvach(thumbnail)
            }

            return@map ThreadBookmarkInfoPostObject.OriginalPost(
              postDescriptor = PostDescriptor.create(input, postInfoForBookmarkJson.num),
              closed = boardsDataJson.isClosed,
              archived = false,
              isBumpLimit = isBumpLimit,
              isImageLimit = false,
              stickyThread = stickyPost,
              thumbnailParams = thumbnailParams,
              subject = postInfoForBookmarkJson.subject,
              comment = postInfoForBookmarkJson.comment ?: "",
            )
          } else {
            return@map ThreadBookmarkInfoPostObject.RegularPost(
              postDescriptor = PostDescriptor.create(input, postInfoForBookmarkJson.num),
              comment = postInfoForBookmarkJson.comment ?: ""
            )
          }
        }

        val thumbnailParams = (threadBookmarkInfoPostObjects.firstOrNull() as? ThreadBookmarkInfoPostObject.OriginalPost)?.thumbnailParams
        val originalPostThumbnail = (thumbnailParams as? ThreadBookmarkInfoPostObject.ThumbnailParams.Dvach)?.thumbnail

        val bookmarkThumbnailUrl = if (originalPostThumbnail != null) {
          site.postImageInfo()?.let { postImageInfo ->
            val params = (postImageInfo as Dvach.PostImageInfo).wrapThumbnailParameters(originalPostThumbnail)
            postImageInfo.thumbnailUrl(params)
          }
        } else {
          null
        }

        return@Try ThreadBookmarkData(
          threadDescriptor = input,
          postObjects = threadBookmarkInfoPostObjects,
          bookmarkThumbnailUrl = bookmarkThumbnailUrl
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
          .also { requestBuilder ->
            site.requestModifier().modifyGetCatalogPagesRequest(
              requestBuilder = requestBuilder
            )
          }
          .build()

        val dvachCatalogPageJsonAdapter = moshi.adapter<DvachCatalogPageJson>(DvachCatalogPageJson::class.java)

        val dvachCatalogPageJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          dvachCatalogPageJsonAdapter
        )

        val dvachCatalogPageJson = dvachCatalogPageJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert catalog pages json into catalogPageJsonList object")

        if (!dvachCatalogPageJson.isValid()) {
          return@Try null
        }

        val threadsPerPage = dvachCatalogPageJson.board?.threadsPerPage ?: 10
        val threadsTotalCount = dvachCatalogPageJson.threads.size
        val maxPages = ceil(threadsTotalCount.toDouble() / (threadsPerPage.coerceAtLeast(1)).toDouble()).toInt()

        val totalThreadsCount = threadsPerPage * maxPages
        val pagesInfoMap = mutableMapWithCap<ThreadDescriptor, Int>(totalThreadsCount)

        dvachCatalogPageJson.threads.forEachIndexed { index, dvachCatalogPageThreadJson ->
          val page = ((index / threadsPerPage) + 1)

          val threadDescriptor = ThreadDescriptor.create(input, dvachCatalogPageThreadJson.postNo)
          pagesInfoMap[threadDescriptor] = page
        }

        return@Try CatalogPagesData(
          pagesTotal = maxPages,
          pagesInfo = pagesInfoMap
        )
      }
    }

  }

  override suspend fun loadSearchPageData(input: SearchParams): Result<SearchResult> {
    // Dvach search returns all results as a single list with no paging
    if (input.page > 0) {
      return Result.success(SearchResult(emptyList()))
    }

    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val site = siteManager.bySiteKey(input.catalogDescriptor.siteKey)
          ?: throw ChanDataSourceException("Unsupported site: ${input}")

        val globalSearchInfo = site.globalSearchInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support globalSearchInfo")

        if (input.isSiteWideSearch && !globalSearchInfo.supportsSiteWideSearch) {
          throw ChanDataSourceException("Site \'${site.readableName}\' does not support site-wide global search")
        }

        if (!input.isSiteWideSearch && !globalSearchInfo.supportsCatalogSpecificSearch) {
          throw ChanDataSourceException("Site \'${site.readableName}\' does not support catalog specific global search")
        }

        val boardCode = if (input.isSiteWideSearch) {
          null
        } else {
          input.catalogDescriptor.boardCode
        }

        val globalSearchUrl = globalSearchInfo.globalSearchUrl(
          boardCode = boardCode,
          query = input.query,
          page = input.page
        )

        logcat(TAG, LogPriority.VERBOSE) { "loadSearchPageData() url='$globalSearchUrl'" }

        val formBuilder = FormBody.Builder().apply {
          add("board", input.catalogDescriptor.boardCode)
          add("text", input.query)
        }

        val request = Request.Builder()
          .url(globalSearchUrl)
          .post(formBuilder.build())
          .also { requestBuilder ->
            site.requestModifier().modifySearchRequest(
              requestBuilder = requestBuilder
            )
          }
          .build()

        val dvachSearchResultJsonAdapter = moshi.adapter<DvachSearchResult>(DvachSearchResult::class.java)
        val dvachSearchResultJsonResult = kurobaOkHttpClient.okHttpClient()
          .suspendConvertWithJsonAdapter(request, dvachSearchResultJsonAdapter)

        val dvachSearchResultJson = dvachSearchResultJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert catalog pages json into DvachSearchResult object")

        if (dvachSearchResultJson.error != null) {
          throw ChanDataSourceException("Failed to load thread. Server returned error: \'${dvachSearchResultJson.error.message()}\'")
        }

        val searchResultPosts = dvachSearchResultJson.posts
        if (searchResultPosts.isNullOrEmpty()) {
          return@Try SearchResult(emptyList())
        }

        val postDataList = mutableListWithCap<IPostData>(initialCapacity = searchResultPosts.size)

        searchResultPosts.forEachIndexed { order, dvachPost ->
          val threadNo = if (dvachPost.parent == 0L) {
            dvachPost.num
          } else {
            dvachPost.parent
          }

          val postNo = dvachPost.num

          val postDescriptor = PostDescriptor.create(
            siteKey = site.siteKey,
            boardCode = input.catalogDescriptor.boardCode,
            threadNo = threadNo,
            postNo = postNo
          )

          val parsedFlags = parseFlags(dvachPost.icon)
          val parsedName = parseName(DEFAULT_NAME, dvachPost.name)

          postDataList += PostData(
            originalPostOrder = order,
            postDescriptor = postDescriptor,
            postSubjectUnparsed = dvachPost.subject,
            postCommentUnparsed = processComment(dvachPost),
            opMark = dvachPost.opMark,
            sage = dvachPost.sage,
            name = parsedName.name,
            tripcode = dvachPost.trip,
            posterId = parsedName.posterId,
            countryFlag = parsedFlags.countryFlag,
            boardFlag = parsedFlags.boardFlag,
            timeMs = dvachPost.timestamp.times(1000L),
            images = emptyList(),
            threadRepliesTotal = null,
            threadImagesTotal = null,
            threadPostersTotal = null,
            archived = false,
            deleted = false,
            closed = false,
            sticky = null,
            bumpLimit = null,
            imageLimit = null,
          )
        }

        // Need to reverse the results because by default they go from oldest to newest
        return@Try SearchResult(postDataList.asReversed())
      }
    }
  }

  private fun processComment(dvachPost: DvachPost): String {
    return buildString(capacity = dvachPost.comment.length) {
      append(dvachPost.comment)

      if (dvachPost.isBanned) {
        append(BANNED_MARK_HTML)
      } else if (dvachPost.isWarned) {
        append(WARNED_MARK_HTML)
      }
    }
  }

  override suspend fun login(input: DvachLoginDetails): Result<DvachLoginResult> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val site = siteManager.bySiteKey(Dvach.SITE_KEY)
          ?: throw ChanDataSourceException("Unsupported site: ${input}")
        val dvachSiteSettings = site.siteSettings as DvachSiteSettings

        val passcodeInfo = site.passcodeInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support passcodeInfo")

        val loginUrl = passcodeInfo.loginUrl()

        logcat(TAG, LogPriority.VERBOSE) { "login() url='$loginUrl'" }

        val formBuilder = FormBody.Builder()

        formBuilder
          .add("passcode", input.passcode)

        val request = Request.Builder()
          .url(loginUrl)
          .post(formBuilder.build())
          .also { requestBuilder ->
            site.requestModifier().modifyLoginRequest(
              requestBuilder = requestBuilder
            )
          }
          .build()

        return@Try kurobaOkHttpClient.okHttpClient().suspendCall(request).unwrap().use { response ->
          val result = response.body?.string()
            ?: throw EmptyBodyResponseException()

          if (!response.isSuccessful) {
            throw ChanDataSourceException("Login failure! Bad response status code: ${response.code}")
          }

          val passcodeResult = moshi
            .adapter(DvachPasscodeResult::class.java)
            .fromJson(result)

          if (passcodeResult == null) {
            throw ChanDataSourceException("Login failure! Failed to parse server response")
          }

          if (passcodeResult.error != null) {
            throw ChanDataSourceException(passcodeResult.error.message)
          }

          val cookies = response.headers("Set-Cookie")
          var tokenCookie: String? = null

          for (cookie in cookies) {
            try {
              val parsedList = HttpCookie.parse(cookie)

              for (parsed in parsedList) {
                if (parsed.name == "passcode_auth" && parsed.value.isNotEmpty()) {
                  tokenCookie = parsed.value
                }
              }
            } catch (error: IllegalArgumentException) {
              logcatError(TAG) { "Error while processing cookies, error: ${error.asLogIfImportantOrErrorMessage()}" }
            }
          }

          if (tokenCookie.isNullOrBlank()) {
            throw ChanDataSourceException("Could not get pass id")
          }

          dvachSiteSettings.passcodeCookie.write(tokenCookie)
          return@use DvachLoginResult(tokenCookie )
        }
      }
    }
  }

  override suspend fun logout(input: Unit): Result<Unit> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val dvach = siteManager.bySiteKey(Dvach.SITE_KEY)
          ?: throw ChanDataSourceException("Unsupported site: ${input}")

        val dvachSiteSettings = dvach.siteSettings as DvachSiteSettings
        dvachSiteSettings.passcodeCookie.write("")
      }
    }
  }

  private fun parseSupportedIcons(icons: List<DvachBoardIconJson>?): List<BoardFlag> {
    if (icons.isNullOrEmpty()) {
      return emptyList()
    }

    val flags = mutableListWithCap<BoardFlag>(icons.size + 1)
    flags += BoardFlag.defaultEntry()

    icons.forEach { dvachBoardIconJson ->
      flags += BoardFlag(
        key = dvachBoardIconJson.url,
        name = dvachBoardIconJson.name,
        flagId = dvachBoardIconJson.num
      )
    }

    return flags
  }

  private fun parseName(defaultName: String, name: String): ParsedName {
    val result = HtmlUnescape.unescape(name).trim()
    val posterIdMarker = " ID: "

    if (result.contains(posterIdMarker)) {
      val split = result.split(posterIdMarker).map { it.trim() }

      val posterName = split.getOrNull(0) ?: defaultName
      val posterIdHtml = split.getOrNull(1) ?: ""
      val posterId = Jsoup.parseBodyFragment(posterIdHtml).text().trim()

      return ParsedName(name = posterName, posterId = posterId)
    }

    val styleAttrMarker = "style=\"color:rgb("
    if (result.contains(styleAttrMarker)) {
      val startOffset = result.indexOfFirst { it == '>' }
      if (startOffset > 0) {
        val posterId = Jsoup.parseBodyFragment(result.substring(startOffset + 1)).text().trim()

        var posterName = result.substringBefore("<")
        if (posterName.isEmpty()) {
          posterName = DEFAULT_NAME
        }

        return ParsedName(name = posterName, posterId = posterId)
      }
    }

    return ParsedName(name = result, posterId = null)
  }

  private fun parseFlags(iconRaw: String?): ParsedFlags {
    if (iconRaw == null) {
      return ParsedFlags()
    }

    val html = Jsoup.parseBodyFragment(iconRaw)

    val icons = html.select("img")

    var boardFlag: PostIcon.BoardFlag? = null
    var countryFlag: PostIcon.CountryFlag? = null

    for (icon in icons) {
      val iconSrc = icon.attr("src")

      if (iconSrc.contains("/static/icons/logos")) {
        val title = icon.attr("title").takeIf { it.isNotBlank() }
        boardFlag = PostIcon.BoardFlag(flagId = iconSrc, flagName = title)
      } else {
        countryFlag = PostIcon.CountryFlag(flagId = iconSrc, flagName = null)
      }
    }

    return ParsedFlags(
      boardFlag = boardFlag,
      countryFlag = countryFlag
    )
  }

  data class ParsedName(
    val name: String,
    val posterId: String?
  )

  data class ParsedFlags(
    val boardFlag: PostIcon.BoardFlag? = null,
    val countryFlag: PostIcon.CountryFlag? = null
  )

  companion object {
    private const val TAG = "DvachDataSource"

    private const val DEFAULT_NAME = "Аноним"
    private const val BANNED_MARK_HTML = "<br><br><span class=\"post__pomyanem\">(Автор этого поста был забанен. Помянем.)</span>"
    private const val WARNED_MARK_HTML = "<br><br><span class=\"post__pomyanem\">(Автор этого поста был предупрежден.)</span>"
  }

}