package com.github.k1rakishou.kurobaexlite.model

import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.*
import com.github.k1rakishou.kurobaexlite.model.data.remote.CatalogPageDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.ThreadDataJson
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

class ChanDataSource(
  private val siteManager: SiteManager,
  private val kurobaOkHttpClient: ProxiedOkHttpClient,
  private val moshi: Moshi
) {

  suspend fun loadThread(
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

        postDataList += threadDataJson.posts.map { threadPost ->
          val postDescriptor = PostDescriptor.create(
            siteKey = site.siteKey,
            boardCode = boardCode,
            threadNo = threadNo,
            postNo = threadPost.no,
            postSubNo = null
          )

          if (postDescriptor.isOP) {
            return@map OriginalPostData(
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.sub ?: "",
              postCommentUnparsed = threadPost.com ?: "",
              images = parsePostImages(
                postImageInfo = postImageInfo,
                ext = threadPost.ext,
                tim = threadPost.tim,
                boardCode = boardCode
              ),
              threadRepliesTotal = threadPost.replies,
              threadImagesTotal = threadPost.images,
              _parsedPostData = null
            )
          } else {
            return@map PostData(
              postDescriptor = postDescriptor,
              postSubjectUnparsed = threadPost.sub ?: "",
              postCommentUnparsed = threadPost.com ?: "",
              images = parsePostImages(
                postImageInfo = postImageInfo,
                ext = threadPost.ext,
                tim = threadPost.tim,
                boardCode = boardCode
              ),
              _parsedPostData = null
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

  suspend fun loadCatalog(
    catalogDescriptor: CatalogDescriptor,
    page: Int? = null
  ): Result<CatalogData> {
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

        catalogPagesDataJson.forEach { catalogPage ->
          postDataList += catalogPage.threads.map { catalogThread ->
            val postDescriptor = PostDescriptor.create(
              siteKey = site.siteKey,
              boardCode = boardCode,
              threadNo = catalogThread.no,
              postNo = catalogThread.no,
              postSubNo = null
            )

            return@map OriginalPostData(
              postDescriptor = postDescriptor,
              postSubjectUnparsed = catalogThread.sub ?: "",
              postCommentUnparsed = catalogThread.com ?: "",
              images = parsePostImages(
                postImageInfo = postImageInfo,
                ext = catalogThread.ext,
                tim = catalogThread.tim,
                boardCode = boardCode
              ),
              threadRepliesTotal = catalogThread.replies,
              threadImagesTotal = catalogThread.images,
              _parsedPostData = null
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

  private fun parsePostImages(
    postImageInfo: Site.PostImageInfo?,
    ext: String?,
    tim: Long?,
    boardCode: String
  ): List<PostImageData> {
    if (postImageInfo == null || !ext.isNotNullNorBlank() || tim == null) {
      return emptyList()
    }

    val thumbnailUrl = postImageInfo.thumbnailUrl(
      boardCode = boardCode,
      tim = tim,
      extension = "jpg"
    ).toHttpUrlOrNull()
      ?: return emptyList()

    return listOf(PostImageData(thumbnailUrl))
  }

  class ChanDataSourceException(message: String) : ClientException(message)

}