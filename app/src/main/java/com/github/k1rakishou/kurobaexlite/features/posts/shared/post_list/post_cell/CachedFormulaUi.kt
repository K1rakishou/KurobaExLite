package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell

import androidx.compose.runtime.Stable
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import okhttp3.HttpUrl

@Stable
data class CachedFormulaUi(
  val formulaRaw: String,
  val formulaImageUrl: HttpUrl,
  val imageWidth: Int,
  val imageHeight: Int
) {

  fun inlinedContentKey(): String {
    return "${PostCommentApplier.ANNOTATION_INLINED_IMAGE}:${formulaImageUrl}"
  }

}