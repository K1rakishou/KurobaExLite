package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import java.io.File

data class ReplyData(
  val chanDescriptor: ChanDescriptor,
  val message: String,
  val attachedImages: List<File>,
  val captchaSolution: CaptchaSolution?,
  val subject: String? = null,
  val flag: String? = null,
  val password: String? = null,
  val postName: String? = null,
  val options: Map<String, String> = emptyMap()
) {
  fun isValid(): Boolean {
    if (message.isEmpty() && attachedImages.isEmpty()) {
      return false
    }

    return true
  }
}