package com.github.k1rakishou.kurobaexlite.model.data.local

import android.os.Parcelable
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import kotlinx.parcelize.Parcelize

data class CatalogsData(
  val chanCatalogs: List<ChanCatalog>
)

data class ChanCatalog(
  val catalogDescriptor: CatalogDescriptor,
  val boardTitle: String?,
  val boardDescription: String?,
  val workSafe: Boolean,
  val maxAttachFilesPerPost: Int,
  val flags: List<BoardFlag>,
  val bumpLimit: Int?
)

@Parcelize
data class BoardFlag(
  val key: String,
  val name: String,
  val flagId: Int?,
) : Parcelable {

  fun asUserReadableString(): String {
    if (key.contains("/") || key.contains(".")) {
      return name
    }

    return "[${key}] ${name}"
  }

  companion object {
    fun defaultEntry() = BoardFlag(
      key = "0",
      name = "Default",
      flagId = null
    )
  }

}