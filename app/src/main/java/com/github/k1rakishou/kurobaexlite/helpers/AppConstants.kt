package com.github.k1rakishou.kurobaexlite.helpers

object AppConstants {
  const val TEXT_SEPARATOR = " • "
  const val deleteNavHistoryTimeoutMs = 5000L
  const val deleteBookmarkTimeoutMs = 5000L

  object RequestCodes {
    const val LOCAL_FILE_PICKER_LAST_SELECTION_REQUEST_CODE = 1
  }

  object WorkerTags {
    private const val BOOKMARK_WATCHER_TAG = "com.github.k1rakishou.kurobaexlite.helpers.BookmarkWatcher"

    fun getUniqueTag(flavorType: AndroidHelpers.FlavorType): String {
      return "${BOOKMARK_WATCHER_TAG}_${flavorType.name}"
    }
  }
}