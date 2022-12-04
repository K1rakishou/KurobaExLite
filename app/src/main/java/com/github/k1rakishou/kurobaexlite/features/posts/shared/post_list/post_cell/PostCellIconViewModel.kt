package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell

import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.PostIcon
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

class PostCellIconViewModel(
  private val siteManager: SiteManager
) : BaseViewModel() {

  fun formatIconUrl(postDescriptor: PostDescriptor, postIcon: PostIcon): String? {
    val site = siteManager.bySiteKey(postDescriptor.siteKey)
      ?: return null

    val iconId = when (postIcon) {
      is PostIcon.CountryFlag -> "country"
      is PostIcon.BoardFlag -> "board_flag"
    }

    val params: Map<String, String> = when (postIcon) {
      is PostIcon.CountryFlag -> {
        mapOf("country_code" to postIcon.flagId)
      }
      is PostIcon.BoardFlag -> {
        mapOf(
          "board_flag_code" to postIcon.flagId,
          "board_code" to postDescriptor.boardCode,
        )
      }
    }

    return site.iconUrl(iconId, params)
  }

  companion object {
    private const val TAG = "PostCellIconViewModel"
  }

}