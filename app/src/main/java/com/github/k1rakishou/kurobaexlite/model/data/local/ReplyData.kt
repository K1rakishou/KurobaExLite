package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.features.reply.AttachedMedia
import com.github.k1rakishou.kurobaexlite.helpers.util.ConversionUtils
import com.github.k1rakishou.kurobaexlite.managers.CaptchaSolution
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import java.security.SecureRandom

data class ReplyData(
  val chanDescriptor: ChanDescriptor,
  val message: String,
  val attachedMediaList: List<AttachedMedia>,
  val captchaSolution: CaptchaSolution?,
  val password: String = generateDefaultPassword(),
  val subject: String? = null,
  val name: String? = null,
  val flag: String? = null,
  val options: String? = null
) {
  fun isValid(): Boolean {
    if (message.isEmpty() && attachedMediaList.isEmpty()) {
      return false
    }

    return true
  }

  companion object {
    private val random = SecureRandom.getInstance("SHA1PRNG")

    fun generateDefaultPassword(): String = ConversionUtils.bytesToHex(random.generateSeed(16))
  }
}