package com.github.k1rakishou.kurobaexlite.model.source.dvach

import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendConvertWithJsonAdapter
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.ChanDataSourceException
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.PostDataSticky
import com.github.k1rakishou.kurobaexlite.model.data.PostIcon
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
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadBookmarkData
import com.github.k1rakishou.kurobaexlite.model.data.local.ThreadData
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachBoardDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachCatalog
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachCatalogPageJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.dvach.DvachThread
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
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.Request
import org.jsoup.Jsoup
import kotlin.math.ceil

class DvachDataSource(
  private val siteManager: SiteManager,
  private val kurobaOkHttpClient: ProxiedOkHttpClient,
  private val moshi: Moshi
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
            flags = emptyList()
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

        catalogThreads.forEachIndexed { order, catalogThread ->
          val postDescriptor = PostDescriptor.create(
            siteKey = site.siteKey,
            boardCode = boardCode,
            threadNo = catalogThread.num,
            postNo = catalogThread.num
          )

          val sticky = if (catalogThread.sticky == 1) {
            val maxCapacity = if (catalogThread.endless == 1) {
              1000
            } else {
              0
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
            originalPostOrder = order,
            postDescriptor = postDescriptor,
            postSubjectUnparsed = catalogThread.subject ?: "",
            postCommentUnparsed = catalogThread.comment ?: "",
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
            threadPostersTotal = null,
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

        val dvachThreadJsonAdapter = moshi.adapter<DvachThread>(DvachThread::class.java)

        val threadDataJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          dvachThreadJsonAdapter
        )

        val dvachThread = threadDataJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert thread json into DvachThread object")

        if (dvachThread.error != null) {
          throw ChanDataSourceException("Failed to load thread. Server returned error: \'${dvachThread.error.message()}\'")
        }

        val defaultName = dvachThread.board?.defaultName ?: DEFAULT_NAME
        val threadPosts = dvachThread.threads?.getOrNull(0)?.posts ?: emptyList()
        val totalCount = threadPosts.size
        val postDataList = mutableListWithCap<IPostData>(initialCapacity = totalCount)

        threadPosts.forEachIndexed { order, threadPost ->
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

          val sticky = if (threadPost.sticky == 1) {
            val maxCapacity = if (threadPost.endless == 1) {
              1000
            } else {
              0
            }

            PostDataSticky(maxCapacity)
          } else {
            null
          }

          val bumpLimit = dvachThread.board
            ?.let { dvachBoardInfo -> dvachBoardInfo.bumpLimit != null && totalCount > dvachBoardInfo.bumpLimit }

          val images = threadPost.files
            ?.mapNotNull { dvachFile ->
              return@mapNotNull dvachFile.toPostImageData(
                postImageInfo = postImageInfo,
                postDescriptor = postDescriptor
              )
            }
            ?: emptyList()

          val parsedFlags = parseFlags(threadPost.icon)
          val parsedName = parseName(defaultName, threadPost.name)

          postDataList += if (threadNo == postNo) {
            OriginalPostData(
              originalPostOrder = order,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.subject ?: "",
              postCommentUnparsed = threadPost.comment ?: "",
              opMark = threadPost.opMark,
              sage = threadPost.sage,
              name = parsedName.name,
              tripcode = threadPost.trip,
              posterId = parsedName.posterId,
              countryFlag = parsedFlags.countryFlag,
              boardFlag = parsedFlags.boardFlag,
              timeMs = threadPost.timestamp.times(1000L),
              images = images,
              threadRepliesTotal = threadPost.postsCount,
              threadImagesTotal = threadPost.filesCount,
              threadPostersTotal = threadPost.postersCount,
              lastModified = threadPost.lasthit,
              archived = false,
              deleted = false,
              closed = threadPost.closed == 1,
              sticky = sticky,
              bumpLimit = bumpLimit,
              imageLimit = null,
            )
          } else {
            PostData(
              originalPostOrder = order,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.subject ?: "",
              postCommentUnparsed = threadPost.comment ?: "",
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
    // TODO: Dvach support
    return Result.failure(NotImplementedError())
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
    // TODO: Dvach support
    return Result.failure(NotImplementedError())
  }

  override suspend fun login(input: DvachLoginDetails): Result<DvachLoginResult> {
    // TODO: Dvach support
    return Result.failure(NotImplementedError())
  }

  override suspend fun logout(input: Unit): Result<Unit> {
    // TODO: Dvach support
    return Result.failure(NotImplementedError())
  }

  private fun parseName(defaultName: String, name: String): ParsedName {
    val result = HtmlUnescape.unescape(name).trim()
    val posterIdMarker = " ID: "

    if (!result.contains(posterIdMarker)) {
      return ParsedName(name = result, posterId = null)
    }

    val split = result.split(posterIdMarker).map { it.trim() }

    val posterName = split.getOrNull(0) ?: defaultName
    val posterIdHtml = split.getOrNull(1) ?: ""
    val posterId = Jsoup.parseBodyFragment(posterIdHtml).text().trim()

    return ParsedName(name = posterName, posterId = posterId)
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
  }

}