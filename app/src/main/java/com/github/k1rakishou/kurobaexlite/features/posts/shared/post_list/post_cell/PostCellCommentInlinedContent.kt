package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.post_bind.processors.Chan4MathTagProcessor
import com.github.k1rakishou.kurobaexlite.helpers.post_bind.processors.IPostProcessor
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.substringSafe
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap


@Composable
internal fun inlinedContentForPostCellComment(postCellData: PostCellData): ImmutableMap<String, InlineTextContent> {
  val processedPostComment = postCellData.parsedPostData?.processedPostComment
  if (processedPostComment == null) {
    return persistentMapOf()
  }

  val context = LocalContext.current
  val chan4MathTagProcessor = koinRemember<Chan4MathTagProcessor>()

  var formulas by remember { mutableStateOf<Map<String, CachedFormulaUi>>(emptyMap()) }

  LaunchedEffect(
    key1 = processedPostComment,
    block = {
      val inlinedImages = processedPostComment.getStringAnnotations(
        tag = IPostProcessor.INLINE_CONTENT_TAG,
        start = 0,
        end = processedPostComment.length
      ).filter { range -> range.item.startsWith("${PostCommentApplier.ANNOTATION_INLINED_IMAGE}:") }

      if (inlinedImages.isEmpty()) {
        return@LaunchedEffect
      }

      val foundFormulas = mutableMapOf<String, CachedFormulaUi>()

      inlinedImages.forEach { inlinedImage ->
        val formulaRaw = processedPostComment.text.substringSafe(inlinedImage.start, inlinedImage.end)
        if (formulaRaw.isNullOrBlank()) {
          return@forEach
        }

        val cachedFormula = chan4MathTagProcessor.getCachedFormulaByRawFormulaWithSanitization(
          postDescriptor = postCellData.postDescriptor,
          formulaRaw = formulaRaw
        )

        if (cachedFormula == null) {
          return@forEach
        }

        foundFormulas[formulaRaw] = CachedFormulaUi(
          formulaRaw = cachedFormula.formulaRaw,
          formulaImageUrl = cachedFormula.formulaImageUrl,
          imageWidth = cachedFormula.imageWidth,
          imageHeight = cachedFormula.imageHeight,
        )
      }

      formulas = foundFormulas
    }
  )

  if (formulas.isEmpty()) {
    return persistentMapOf()
  }

  return remember(key1 = formulas) {
    val map = mutableMapOf<String, InlineTextContent>()

    formulas.entries.forEach { (_, cachedFormulaUi) ->
      val inlinedContentKey = cachedFormulaUi.inlinedContentKey()
      val inlineTextContent = InlineTextContent(
        placeholder = Placeholder(
          width = cachedFormulaUi.imageWidth.sp,
          height = cachedFormulaUi.imageHeight.sp,
          placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        ),
        children = { mathFormulaRaw ->
          FormulaInlinedContent(
            mathFormulaRaw = mathFormulaRaw,
            postCellData = postCellData,
          )
        }
      )

      map[inlinedContentKey] = inlineTextContent
    }

    return@remember map.toImmutableMap()
  }
}

@Composable
private fun FormulaInlinedContent(
  mathFormulaRaw: String,
  postCellData: PostCellData
) {
  val context = LocalContext.current
  val chan4MathTagProcessor = koinRemember<Chan4MathTagProcessor>()

  val imageRequest by produceState<ImageRequest?>(
    initialValue = null,
    key1 = mathFormulaRaw,
    producer = {
      val mathFormulaImageUrl = chan4MathTagProcessor.getCachedFormulaByRawFormulaWithSanitization(
        postDescriptor = postCellData.postDescriptor,
        formulaRaw = mathFormulaRaw
      )?.formulaImageUrl

      value = if (mathFormulaImageUrl == null) {
        null
      } else {
        ImageRequest.Builder(context)
          .data(mathFormulaImageUrl)
          .size(Size.ORIGINAL)
          .build()
      }
    }
  )

  if (imageRequest != null) {
    AsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = imageRequest,
      contentDescription = "Math formula image"
    )
  }
}