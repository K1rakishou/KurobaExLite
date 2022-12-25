package com.github.k1rakishou.kurobaexlite.helpers.post_bind.processors

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPart
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

interface IPostProcessor {

  suspend fun isCached(postDescriptor: PostDescriptor): Boolean

  suspend fun removeCached(chanDescriptor: ChanDescriptor)

  suspend fun applyData(
    textPart: TextPart,
    postDescriptor: PostDescriptor
  ): AppliedDataResult?

  suspend fun process(
    isCatalogMode: Boolean,
    postDescriptor: PostDescriptor,
    onFoundContentToProcess: () -> Unit,
    onEndedProcessing: () -> Unit,
  ): Boolean

  data class AppliedDataResult(
    val textPart: TextPart,
    val inlinedContentList: List<InlinedContent>
  ) {

    fun applyToAnnotatedString(textPartText: String, builder: AnnotatedString.Builder) {
      with(builder) {
        append(textPartText)

        inlinedContentList.forEach { inlinedContent ->
          addStringAnnotation(
            tag = INLINE_CONTENT_TAG,
            annotation = packFormulaIntoAnnotation(inlinedContent.annotation, inlinedContent.alternateText),
            start = inlinedContent.startIndex,
            end = inlinedContent.endIndex
          )
        }
      }
    }

    companion object {
      fun packFormulaIntoAnnotation(annotation: String, formulaRaw: String): String {
        return "${annotation}:${formulaRaw}"
      }
    }

  }

  data class InlinedContent(
    val annotation: String,
    val alternateText: String,
    val startIndex: Int,
    val endIndex: Int,
  )

  companion object {
    // Copied from Jetpack Compose InlineTextContent.kt
    const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"
  }

}