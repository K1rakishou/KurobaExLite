package com.github.k1rakishou.kurobaexlite.model.source.remote.chan4

import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendConvertWithHtmlReader
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendConvertWithJsonAdapter
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.ChanDataSourceException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.ThumbnailSpoiler
import com.github.k1rakishou.kurobaexlite.model.data.local.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogPagesData
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogsData
import com.github.k1rakishou.kurobaexlite.model.data.local.Chan4LoginDetails
import com.github.k1rakishou.kurobaexlite.model.data.local.Chan4LoginResult
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
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.Chan4BoardsDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.Chan4CatalogPageDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.Chan4CatalogPageJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.Chan4SharedDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.Chan4ThreadBookmarkInfoJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.Chan4ThreadDataJson
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.remote.IBoardDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.IBookmarkDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.ICatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.ICatalogPagesDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.IGlobalSearchDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.ILoginDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.ILogoutDataSource
import com.github.k1rakishou.kurobaexlite.model.source.remote.IThreadDataSource
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.github.k1rakishou.kurobaexlite.sites.chan4.Chan4
import com.github.k1rakishou.kurobaexlite.sites.settings.Chan4SiteSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.FormBody
import okhttp3.Request
import java.net.HttpCookie

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class Chan4DataSource(
  private val siteManager: SiteManager,
  private val kurobaOkHttpClient: IKurobaOkHttpClient,
  private val moshi: Moshi
) : ICatalogDataSource<CatalogDescriptor, CatalogData>,
  IThreadDataSource<ThreadDescriptor, ThreadData>,
  IBoardDataSource<SiteKey, CatalogsData>,
  IBookmarkDataSource<ThreadDescriptor, ThreadBookmarkData>,
  ICatalogPagesDataSource<CatalogDescriptor, CatalogPagesData?>,
  IGlobalSearchDataSource<SearchParams, SearchResult>,
  ILoginDataSource<Chan4LoginDetails, Chan4LoginResult>,
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

        val boardsDataJsonAdapter = moshi.adapter<Chan4BoardsDataJson>(Chan4BoardsDataJson::class.java)
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

          val allFlags = mutableListWithCap<BoardFlag>(64)

          val loadedFlags = boardDataJson.boardFlags
            ?.list
            ?.map { boardFlagJson ->
              return@map BoardFlag(
                key = boardFlagJson.key,
                name = boardFlagJson.name,
                flagId = null
              )
            }

          if (loadedFlags.isNotNullNorEmpty()) {
            allFlags += BoardFlag.defaultEntry()
            allFlags.addAll(loadedFlags)
          }

          return@mapNotNull ChanCatalog(
            catalogDescriptor = CatalogDescriptor(input, boardCode),
            boardTitle = boardTitle,
            boardDescription = boardDescription,
            workSafe = boardDataJson.workSafe == 1,
            maxAttachFilesPerPost = 1,
            flags = allFlags,
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

        val catalogPagesDataJsonAdapter = moshi.adapter<List<Chan4CatalogPageDataJson>>(
          Types.newParameterizedType(List::class.java, Chan4CatalogPageDataJson::class.java),
        )

        val catalogPagesDataJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          catalogPagesDataJsonAdapter
        )

        val catalogPagesDataJson = catalogPagesDataJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert catalog json into CatalogDataJson object")

        val totalCount = catalogPagesDataJson.sumOf { catalogPageData -> catalogPageData.threads.size }
        val postDataList = mutableListWithCap<IPostData>(initialCapacity = totalCount)

        catalogPagesDataJson.forEach { catalogPage ->
          postDataList += catalogPage.threads.map { catalogThread ->
            val postDescriptor = PostDescriptor.create(
              siteKey = site.siteKey,
              boardCode = boardCode,
              threadNo = catalogThread.no,
              postNo = catalogThread.no
            )

            return@map OriginalPostData(
              originalPostOrder = -1,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = catalogThread.sub ?: "",
              postCommentUnparsed = catalogThread.com ?: "",
              opMark = false,
              sage = false,
              name = catalogThread.name,
              tripcode = catalogThread.trip,
              posterId = catalogThread.id,
              countryFlag = catalogThread.countryFlag(),
              boardFlag = catalogThread.boardFlag(),
              timeMs = catalogThread.time?.times(1000L),
              images = parsePostImages(
                postDescriptor = postDescriptor,
                postImageInfo = postImageInfo,
                sharedDataJson = catalogThread,
                boardCode = boardCode
              ),
              threadRepliesTotal = catalogThread.replies,
              threadImagesTotal = catalogThread.images,
              threadPostersTotal = catalogThread.posters,
              lastModified = catalogThread.lastModified,
              archived = catalogThread.archived == 1,
              closed = catalogThread.closed == 1,
              deleted = false,
              sticky = catalogThread.sticky(),
              bumpLimit = catalogThread.bumpLimit?.let { bumpLimit -> bumpLimit == 1 },
              imageLimit = catalogThread.imageLimit?.let { imageLimit -> imageLimit == 1 },
            )
          }
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

        val threadUrl = threadInfo.fullThreadUrl(boardCode, threadNo)
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

        val threadDataJsonJsonAdapter = moshi.adapter<Chan4ThreadDataJson>(Chan4ThreadDataJson::class.java)

        val threadDataJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(
          request,
          threadDataJsonJsonAdapter
        )

        val threadDataJson = threadDataJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert thread json into ThreadDataJson object")

        val postDataList = mutableListWithCap<IPostData>(initialCapacity = threadDataJson.posts.size)

        postDataList += threadDataJson.posts.map { threadPost ->
          val postDescriptor = PostDescriptor.create(
            siteKey = site.siteKey,
            boardCode = boardCode,
            threadNo = threadNo,
            postNo = threadPost.no
          )

          if (postDescriptor.isOP) {
            return@map OriginalPostData(
              originalPostOrder = -1,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.sub ?: "",
              postCommentUnparsed = threadPost.com ?: "",
              opMark = false,
              sage = false,
              name = threadPost.name,
              tripcode = threadPost.trip,
              posterId = threadPost.id,
              countryFlag = threadPost.countryFlag(),
              boardFlag = threadPost.boardFlag(),
              timeMs = threadPost.time?.times(1000L),
              images = parsePostImages(
                postDescriptor = postDescriptor,
                postImageInfo = postImageInfo,
                sharedDataJson = threadPost,
                boardCode = boardCode
              ),
              threadRepliesTotal = threadPost.replies,
              threadImagesTotal = threadPost.images,
              threadPostersTotal = threadPost.posters,
              lastModified = threadPost.lastModified,
              archived = threadPost.archived == 1,
              closed = threadPost.closed == 1,
              deleted = false,
              sticky = threadPost.sticky(),
              bumpLimit = threadPost.bumpLimit?.let { bumpLimit -> bumpLimit == 1 },
              imageLimit = threadPost.imageLimit?.let { imageLimit -> imageLimit == 1 },
            )
          } else {
            return@map PostData(
              originalPostOrder = -1,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.sub ?: "",
              postCommentUnparsed = threadPost.com ?: "",
              opMark = false,
              sage = false,
              name = threadPost.name,
              tripcode = threadPost.trip,
              posterId = threadPost.id,
              countryFlag = threadPost.countryFlag(),
              boardFlag = threadPost.boardFlag(),
              timeMs = threadPost.time?.times(1000L),
              images = parsePostImages(
                postDescriptor = postDescriptor,
                postImageInfo = postImageInfo,
                sharedDataJson = threadPost,
                boardCode = boardCode
              ),
              lastModified = null,
              archived = false,
              closed = false,
              deleted = false,
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

        val threadBookmarkInfoJsonAdapter = moshi.adapter<Chan4ThreadBookmarkInfoJson>(Chan4ThreadBookmarkInfoJson::class.java)
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

            val thumbnailParams = postInfoForBookmarkJson.tim?.let { tim ->
              ThreadBookmarkInfoPostObject.ThumbnailParams.Chan4(tim)
            }

            return@map ThreadBookmarkInfoPostObject.OriginalPost(
              postDescriptor = PostDescriptor.create(input, postInfoForBookmarkJson.postNo),
              closed = postInfoForBookmarkJson.isClosed,
              archived = postInfoForBookmarkJson.isArchived,
              isBumpLimit = postInfoForBookmarkJson.isBumpLimit,
              isImageLimit = postInfoForBookmarkJson.isImageLimit,
              stickyThread = stickyPost,
              thumbnailParams = thumbnailParams,
              subject = postInfoForBookmarkJson.sub,
              comment = postInfoForBookmarkJson.comment,
            )
          } else {
            return@map ThreadBookmarkInfoPostObject.RegularPost(
              postDescriptor = PostDescriptor.create(input, postInfoForBookmarkJson.postNo),
              comment = postInfoForBookmarkJson.comment
            )
          }
        }

        val thumbnailParams = (threadBookmarkInfoPostObjects.firstOrNull() as? ThreadBookmarkInfoPostObject.OriginalPost)?.thumbnailParams
        val originalPostTim = (thumbnailParams as? ThreadBookmarkInfoPostObject.ThumbnailParams.Chan4)?.tim

        val bookmarkThumbnailUrl = if (originalPostTim != null) {
          site.postImageInfo()?.let { postImageInfo ->
            val params = (postImageInfo as Chan4.PostImageInfo).wrapParameters(input.boardCode, originalPostTim, "jpg")
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

        val catalogPageJsonListType = Types.newParameterizedType(List::class.java, Chan4CatalogPageJson::class.java)
        val catalogPageJsonListAdapter = moshi.adapter<List<Chan4CatalogPageJson>>(catalogPageJsonListType)

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

        val request = Request.Builder()
          .url(globalSearchUrl)
          .get()
          .also { requestBuilder ->
            site.requestModifier().modifySearchRequest(
              requestBuilder = requestBuilder
            )
          }
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

  override suspend fun login(input: Chan4LoginDetails): Result<Chan4LoginResult> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val site = siteManager.bySiteKey(Chan4.SITE_KEY)
          ?: throw ChanDataSourceException("Unsupported site: ${input}")
        val chan4SiteSettings = site.siteSettings as Chan4SiteSettings

        val passcodeInfo = site.passcodeInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support passcodeInfo")

        val loginUrl = passcodeInfo.loginUrl()

        logcat(TAG, LogPriority.VERBOSE) { "login() url='$loginUrl'" }

        val formBuilder = FormBody.Builder().apply {
          add("act", "do_login")
          add("id", input.token)
          add("pin", input.pin)
        }

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

          if (result.contains("Success! Your device is now authorized")) {
            val cookies: List<String> = response.headers("Set-Cookie")
            var passId: String? = null

            for (cookie in cookies) {
              val parsedList = HttpCookie.parse(cookie)
              for (parsed in parsedList) {
                if (parsed.name == "pass_id" && parsed.value != "0") {
                  passId = parsed.value
                }
              }
            }

            if (passId.isNullOrEmpty()) {
              throw ChanDataSourceException("Could not get pass id")
            }

            if (passId.isNotEmpty()) {
              chan4SiteSettings.passcodeCookie.write(passId)
            }

            return@use Chan4LoginResult(passId)
          }

          // TODO: strings
          val message = if (result.contains("Your Token must be exactly 10 characters")) {
            "Incorrect token"
          } else if (result.contains("You have left one or more fields blank")) {
            "You have left one or more fields blank"
          } else if (result.contains("Incorrect Token or PIN")) {
            "Incorrect Token or PIN"
          } else {
            "Unknown error"
          }

          throw ChanDataSourceException(message)
        }
      }
    }
  }

  override suspend fun logout(input: Unit): Result<Unit> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val chan4 = siteManager.bySiteKey(Chan4.SITE_KEY)
          ?: throw ChanDataSourceException("Unsupported site: ${input}")

        val chan4SiteSettings = chan4.siteSettings as Chan4SiteSettings
        chan4SiteSettings.passcodeCookie.write("")
      }
    }
  }

  private fun parsePostImages(
    postDescriptor: PostDescriptor,
    postImageInfo: Site.PostImageInfo?,
    sharedDataJson: Chan4SharedDataJson,
    boardCode: String
  ): List<PostImageData> {
    if (postImageInfo == null || !sharedDataJson.hasImage()) {
      return emptyList()
    }

    if (postImageInfo !is Chan4.PostImageInfo) {
      return emptyList()
    }

    val extension = sharedDataJson.ext
      ?.removePrefix(".")
      ?: "jpg"

    val thumbnailUrl = postImageInfo.thumbnailUrl(
      postImageInfo.wrapParameters(boardCode, sharedDataJson.tim!!, "jpg")
    ) ?: return emptyList()

    val fullUrl = postImageInfo.fullUrl(
      postImageInfo.wrapParameters(boardCode, sharedDataJson.tim!!, extension)
    ) ?: return emptyList()

    val serverFileName = sharedDataJson.tim.toString()
    val originalFileName = sharedDataJson.filename
      ?.takeIf { filename -> filename.isNotNullNorBlank() }
      ?: serverFileName

    val postImageData = PostImageData(
      thumbnailUrl = thumbnailUrl,
      fullImageUrl = fullUrl,
      originalFileNameEscaped = HtmlUnescape.unescape(originalFileName),
      serverFileName = serverFileName,
      ext = extension,
      width = sharedDataJson.w!!,
      height = sharedDataJson.h!!,
      fileSize = sharedDataJson.fsize!!.toLong(),
      thumbnailSpoiler = sharedDataJson.spoiler?.let { spoilerId -> ThumbnailSpoiler.Chan4(spoilerId) },
      ownerPostDescriptor = postDescriptor
    )

    return listOf(postImageData)
  }

  companion object {
    private const val TAG = "Chan4DataSource"
  }

}