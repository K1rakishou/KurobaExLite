package com.github.k1rakishou.kurobaexlite.model.source.chan4

import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.local.*
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.BoardsDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.CatalogPageDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.PostImageDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.chan4.ThreadDataJson
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.IBoardDataSource
import com.github.k1rakishou.kurobaexlite.model.source.ICatalogDataSource
import com.github.k1rakishou.kurobaexlite.model.source.IThreadDataSource
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class Chan4DataSource(
  private val siteManager: SiteManager,
  private val kurobaOkHttpClient: ProxiedOkHttpClient,
  private val moshi: Moshi
) : ICatalogDataSource<CatalogDescriptor, CatalogData>,
  IThreadDataSource<ThreadDescriptor, ThreadData>,
  IBoardDataSource<SiteKey, BoardsData> {

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

        val request = Request.Builder()
          .url(threadUrl)
          .get()
          .build()

        val threadDataJsonJsonAdapter = moshi.adapter<ThreadDataJson>(ThreadDataJson::class.java)

        val threadDataJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
          request,
          threadDataJsonJsonAdapter
        )

        val threadDataJson = threadDataJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert thread json into ThreadDataJson object")

        val postDataList = mutableListWithCap<PostData>(initialCapacity = 16)

        postDataList += threadDataJson.posts.mapIndexed { index, threadPost ->
          val postDescriptor = PostDescriptor.create(
            siteKey = site.siteKey,
            boardCode = boardCode,
            threadNo = threadNo,
            postNo = threadPost.no
          )

          if (postDescriptor.isOP) {
            return@mapIndexed OriginalPostData(
              postIndex = index,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.sub ?: "",
              postCommentUnparsed = threadPost.com ?: "",
              timeMs = threadPost.time?.times(1000L),
              images = parsePostImages(
                postImageInfo = postImageInfo,
                postImageDataJson = threadPost,
                boardCode = boardCode
              ),
              threadRepliesTotal = threadPost.replies,
              threadImagesTotal = threadPost.images,
              threadPostersTotal = threadPost.posters,
              parsedPostData = null
            )
          } else {
            return@mapIndexed PostData(
              postIndex = index,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.sub ?: "",
              postCommentUnparsed = threadPost.com ?: "",
              timeMs = threadPost.time?.times(1000L),
              images = parsePostImages(
                postImageInfo = postImageInfo,
                postImageDataJson = threadPost,
                boardCode = boardCode
              ),
              parsedPostData = null
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

        val request = Request.Builder()
          .url(catalogUrl)
          .get()
          .build()

        val catalogPagesDataJsonAdapter = moshi.adapter<List<CatalogPageDataJson>>(
          Types.newParameterizedType(List::class.java, CatalogPageDataJson::class.java),
        )

        val catalogPagesDataJsonResult = kurobaOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
          request,
          catalogPagesDataJsonAdapter
        )

        val catalogPagesDataJson = catalogPagesDataJsonResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert catalog json into CatalogDataJson object")

        val postDataList = mutableListWithCap<PostData>(initialCapacity = 150)

        catalogPagesDataJson.forEachIndexed { index, catalogPage ->
          postDataList += catalogPage.threads.map { catalogThread ->
            val postDescriptor = PostDescriptor.create(
              siteKey = site.siteKey,
              boardCode = boardCode,
              threadNo = catalogThread.no,
              postNo = catalogThread.no
            )

            return@map OriginalPostData(
              postIndex = index,
              postDescriptor = postDescriptor,
              postSubjectUnparsed = catalogThread.sub ?: "",
              postCommentUnparsed = catalogThread.com ?: "",
              timeMs = catalogThread.time?.times(1000L),
              images = parsePostImages(
                postImageInfo = postImageInfo,
                postImageDataJson = catalogThread,
                boardCode = boardCode
              ),
              threadRepliesTotal = catalogThread.replies,
              threadImagesTotal = catalogThread.images,
              threadPostersTotal = null,
              parsedPostData = null
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

  override suspend fun loadBoards(input: SiteKey): Result<BoardsData> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val site = siteManager.bySiteKey(input)
          ?: throw ChanDataSourceException("Unsupported site: ${input}")

        val boardsInfo = site.boardsInfo()
          ?: throw ChanDataSourceException("Site ${site.readableName} does not support boards list")

        val boardsUrl = boardsInfo.boardsUrl()

        val request = Request.Builder()
          .url(boardsUrl)
          .get()
          .build()

        val boardsDataJsonAdapter = moshi.adapter<BoardsDataJson>(BoardsDataJson::class.java)
        val boardsDataJsonAdapterResult = kurobaOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
          request,
          boardsDataJsonAdapter
        )

        val boardsDataJson = boardsDataJsonAdapterResult.unwrap()
          ?: throw ChanDataSourceException("Failed to convert boards json into BoardDataJson object")

        val chanBoards = boardsDataJson.boards.mapNotNull { boardDataJson ->
          val boardCode = boardDataJson.boardCode ?: return@mapNotNull null
          val boardTitle = boardDataJson.boardTitle
          val boardDescription = boardDataJson.boardDescription?.let { HtmlUnescape.unescape(it) }

          return@mapNotNull ChanBoard(
            catalogDescriptor = CatalogDescriptor(input, boardCode),
            boardTitle = boardTitle,
            boardDescription = boardDescription
          )
        }

        return@Try BoardsData(chanBoards)
      }
    }
  }

  private fun parsePostImages(
    postImageInfo: Site.PostImageInfo?,
    postImageDataJson: PostImageDataJson,
    boardCode: String
  ): List<PostImageData> {
    if (postImageInfo == null || !postImageDataJson.hasImage()) {
      return emptyList()
    }

    val extension = postImageDataJson.ext!!.removePrefix(".")

    val thumbnailUrl = postImageInfo.thumbnailUrl(
      boardCode = boardCode,
      tim = postImageDataJson.tim!!,
      extension = "jpg"
    ).toHttpUrlOrNull()
      ?: return emptyList()

    val serverFileName = postImageDataJson.tim.toString()
    val originalFileName = postImageDataJson.filename
      ?: serverFileName

    val postImageData = PostImageData(
      thumbnailUrl = thumbnailUrl,
      originalFileName = originalFileName,
      serverFileName = serverFileName,
      ext = extension,
      width = postImageDataJson.w!!,
      height = postImageDataJson.h!!,
      fileSize = postImageDataJson.fsize!!
    )

    return listOf(postImageData)
  }

  class ChanDataSourceException(message: String) : ClientException(message)

}