package com.github.k1rakishou.kurobaexlite.features.downloads

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.MediaSaver
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import kotlinx.coroutines.launch

class DownloadsScreenViewModel(
  private val mediaSaver: MediaSaver
) : BaseViewModel() {
  private val _activeDownloads = mutableStateListOf<ActiveDownloadUi>()
  val activeDownloads: List<ActiveDownloadUi>
    get() = _activeDownloads

  init {
    viewModelScope.launch {
      val allActiveDownloads = mediaSaver.allActiveDownloads()

      Snapshot.withMutableSnapshot {
        val allActiveDownloadsUi = allActiveDownloads.map { activeDownload ->
          return@map ActiveDownloadUi(
            uuid = activeDownload.uuid,
            chanDescriptor = activeDownload.chanDescriptor,
            downloaded = activeDownload.downloaded,
            failed = activeDownload.failed,
            total = activeDownload.total,
          )
        }

        _activeDownloads.clear()
        _activeDownloads.addAll(allActiveDownloadsUi)
      }

      mediaSaver.activeDownloadsInfoFlow.collect { activeDownloads ->
        Snapshot.withMutableSnapshot {
          if (activeDownloads == null) {
            _activeDownloads.clear()
            return@withMutableSnapshot
          }

          activeDownloads.forEach { activeDownload ->
            val index = _activeDownloads.indexOfFirst { activeDownloadUi -> activeDownloadUi.uuid == activeDownload.uuid }
            if (index < 0) {
              if (activeDownload.canceled) {
                return@forEach
              }

              val newActiveDownloadUi = ActiveDownloadUi(
                uuid = activeDownload.uuid,
                chanDescriptor = activeDownload.chanDescriptor,
                downloaded = activeDownload.downloaded,
                failed = activeDownload.failed,
                total = activeDownload.total,
              )

              _activeDownloads.add(0, newActiveDownloadUi)
            } else {
              if (activeDownload.canceled) {
                _activeDownloads.removeAt(index)
              } else {
                _activeDownloads[index].update(activeDownload)
              }
            }
          }
        }
      }
    }
  }

  fun cancelDownload(uuid: String) {
    viewModelScope.launch {
      mediaSaver.cancelDownloadByUuid(uuid)

      val index = _activeDownloads.indexOfFirst { activeDownloadUi -> activeDownloadUi.uuid == uuid }
      if (index >= 0) {
        _activeDownloads.removeAt(index)
      }
    }
  }

  @Stable
  class ActiveDownloadUi(
    val uuid: String,
    val chanDescriptor: ChanDescriptor,
    downloaded: Int,
    failed: Int,
    total: Int
  ) {
    val _downloaded = mutableStateOf(downloaded)
    val downloaded: State<Int>
      get() = _downloaded
    val _failed = mutableStateOf(failed)
    val failed: State<Int>
      get() = _failed
    val _total = mutableStateOf(total)
    val total: State<Int>
      get() = _total

    fun update(activeDownloadInfo: MediaSaver.ActiveDownloadInfo) {
      _downloaded.value = activeDownloadInfo.downloaded
      _failed.value = activeDownloadInfo.failed
      _total.value = activeDownloadInfo.total
    }

  }

}