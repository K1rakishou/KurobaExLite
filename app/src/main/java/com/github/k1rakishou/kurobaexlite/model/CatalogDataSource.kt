package com.github.k1rakishou.kurobaexlite.model

import com.github.k1rakishou.kurobaexlite.helpers.*
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.CatalogData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.data.local.PostImageData
import com.github.k1rakishou.kurobaexlite.model.data.remote.CatalogPageDataJson
import com.github.k1rakishou.kurobaexlite.model.data.remote.CatalogThreadDataJson
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

class CatalogDataSource(
  private val siteManager: SiteManager,
  private val kurobaOkHttpClient: ProxiedOkHttpClient,
  private val moshi: Moshi
) {

  suspend fun loadCatalog(
    catalogDescriptor: CatalogDescriptor,
    page: Int? = null
  ): Result<CatalogData> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val siteKey = catalogDescriptor.siteKey
        val boardCode = catalogDescriptor.boardCode

        val site = siteManager.bySiteKey(siteKey)
          ?: throw CatalogException("Unsupported site: ${siteKey}")

        val catalogInfo = site.catalogInfo()
          ?: throw CatalogException("Site ${site.readableName} does not support catalog")
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
          ?: throw CatalogException("Failed to convert catalog json into CatalogDataJson object")

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

            return@map PostData(
              postDescriptor = postDescriptor,
              postCommentUnparsed = catalogThread.com ?: "",
              images = parsePostImages(postImageInfo, catalogThread, boardCode)
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
    catalogThread: CatalogThreadDataJson,
    boardCode: String
  ): List<PostImageData> {
    if (postImageInfo == null || !catalogThread.ext.isNotNullNorBlank() || catalogThread.tim == null) {
      return emptyList()
    }

    val thumbnailUrl = postImageInfo.thumbnailUrl(
      boardCode = boardCode,
      tim = catalogThread.tim,
      extension = catalogThread.ext
    ).toHttpUrlOrNull()
      ?: return emptyList()

    return listOf(PostImageData(thumbnailUrl))
  }

  class CatalogException(message: String) : ClientException(message)

}