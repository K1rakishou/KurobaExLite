package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.data.CountryFlag
import com.github.k1rakishou.kurobaexlite.model.data.PostIcon
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class PostCellIconViewModel(
  private val siteManager: SiteManager
) : BaseViewModel() {

  fun formatIconUrl(postDescriptor: PostDescriptor, postIcon: PostIcon): String? {
    val site = siteManager.bySiteKey(postDescriptor.siteKey)
      ?: return null

    val iconId = when (postIcon) {
      is CountryFlag -> "country"
      is BoardFlag -> "board_flag"
      else -> {
        logcatError(TAG) { "postIcon not supported: ${postIcon::class.java.simpleName}" }
        return null
      }
    }

    val params: Map<String, String> = when (postIcon) {
      is CountryFlag -> {
        mapOf("country_code" to postIcon.flagId)
      }
      is BoardFlag -> {
        mapOf(
          "board_flag_code" to postIcon.flagId,
          "board_code" to postDescriptor.boardCode,
        )
      }
      else -> {
        logcatError(TAG) { "postIcon not supported: ${postIcon::class.java.simpleName}" }
        return null
      }
    }

    return site.iconUrl(iconId, params)
  }

  companion object {
    private const val TAG = "PostCellIconViewModel"
  }

}