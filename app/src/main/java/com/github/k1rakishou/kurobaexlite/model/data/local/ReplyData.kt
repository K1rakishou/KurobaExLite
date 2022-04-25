package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.features.reply.AttachedMedia
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor

data class ReplyData(
  val chanDescriptor: ChanDescriptor,
  val message: String,
  val attachedMediaList: List<AttachedMedia>,
  val captchaSolution: CaptchaSolution?,
  val subject: String? = null,
  val flag: String? = null,
  val password: String? = null,
  val postName: String? = null,
  val options: Map<String, String> = emptyMap()
) {
  fun isValid(): Boolean {
    if (message.isEmpty() && attachedMediaList.isEmpty()) {
      return false
    }

    return true
  }
}