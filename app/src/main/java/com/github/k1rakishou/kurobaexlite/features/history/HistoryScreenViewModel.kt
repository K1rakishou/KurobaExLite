package com.github.k1rakishou.kurobaexlite.features.history

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.interactors.navigation.LoadNavigationHistory
import com.github.k1rakishou.kurobaexlite.interactors.navigation.ModifyNavigationHistory
import com.github.k1rakishou.kurobaexlite.interactors.navigation.PersistNavigationHistory
import com.github.k1rakishou.kurobaexlite.managers.NavigationHistoryManager
import com.github.k1rakishou.kurobaexlite.managers.NavigationUpdate
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.NavigationElement
import com.github.k1rakishou.kurobaexlite.model.data.ui.UiNavigationElement
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class HistoryScreenViewModel(
  private val siteManager: SiteManager,
  private val appSettings: AppSettings,
  private val loadNavigationHistory: LoadNavigationHistory,
  private val modifyNavigationHistory: ModifyNavigationHistory,
  private val persistNavigationHistory: PersistNavigationHistory,
  private val navigationHistoryManager: NavigationHistoryManager,
) : BaseViewModel() {
  private val _navigationHistoryList = mutableStateListOf<UiNavigationElement>()
  val navigationHistoryList: List<UiNavigationElement>
    get() = _navigationHistoryList

  private val _removedElementsFlow = MutableSharedFlow<Pair<Int, UiNavigationElement>>(
    extraBufferCapacity = Channel.UNLIMITED
  )
  val removedElementsFlow: SharedFlow<Pair<Int, UiNavigationElement>>
    get() = _removedElementsFlow.asSharedFlow()

  private val _scrollNavigationHistoryToTopEvents = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )
  val scrollNavigationHistoryToTopEvents: SharedFlow<Unit>
    get() = _scrollNavigationHistoryToTopEvents.asSharedFlow()

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    viewModelScope.launch {
      navigationHistoryManager.navigationUpdates.collect { navigationUpdate ->
        processNavigationUpdates(navigationUpdate)
      }
    }

    viewModelScope.launch {
      loadNavigationHistory.loadFromDatabase(appSettings.navigationHistoryMaxSize.read())
    }
  }

  fun removeNavigationElement(uiNavigationElement: UiNavigationElement) {
    viewModelScope.launch {
      modifyNavigationHistory.remove(uiNavigationElement.chanDescriptor)
    }
  }

  fun undoNavElementDeletion(prevIndex: Int, uiNavigationElement: UiNavigationElement) {
    viewModelScope.launch {
      modifyNavigationHistory.undoDeletion(prevIndex, uiNavigationElement.toNavigationElement())
    }
  }

  fun reorderNavigationElement(uiNavigationElement: UiNavigationElement) {
    viewModelScope.launch {
      modifyNavigationHistory.moveToTop(uiNavigationElement.chanDescriptor)
    }
  }

  private fun processNavigationUpdates(navigationUpdate: NavigationUpdate) {
    if (navigationUpdate !is NavigationUpdate.Loaded) {
      persistNavigationHistory.persist()
    }

    when (navigationUpdate) {
      is NavigationUpdate.Loaded -> {
        val uiElements = navigationUpdate.navigationElements
          .map { navigationElement -> navigationElement.toUiElement() }

        if (_navigationHistoryList.isNotEmpty()) {
          _navigationHistoryList.clear()
        }

        _navigationHistoryList.addAll(uiElements)
      }
      is NavigationUpdate.Added -> {
        val index = navigationUpdate.index
        val uiElement = navigationUpdate.navigationElement.toUiElement()

        if (index < 0 || index > _navigationHistoryList.lastIndex) {
          _navigationHistoryList.add(0, uiElement)
        } else {
          _navigationHistoryList.add(index, uiElement)
        }
      }
      is NavigationUpdate.Removed -> {
        val uiElement = navigationUpdate.navigationElement.toUiElement()

        val index = _navigationHistoryList
          .indexOfFirst { uiNavigationElement -> uiNavigationElement.chanDescriptor == uiElement.chanDescriptor }
        if (index >= 0) {
          _navigationHistoryList.removeAt(index)
          _removedElementsFlow.tryEmit(Pair(index, uiElement))
        }
      }
      is NavigationUpdate.Moved -> {
        val movedElement = navigationUpdate.navigationElement.toUiElement()

        val movedElementPrevIndex = _navigationHistoryList
          .indexOfFirst { uiNavigationElement -> uiNavigationElement.chanDescriptor == movedElement.chanDescriptor }

        // We use greater than zero and not greater or equals to zero here because if it's already
        // at 0th position then there is no point in moving it to 0th position again
        if (movedElementPrevIndex > 0) {
          _navigationHistoryList.add(0, _navigationHistoryList.removeAt(movedElementPrevIndex))
        }
      }
    }

    when (navigationUpdate) {
      is NavigationUpdate.Removed -> {
        // no-op
      }
      is NavigationUpdate.Loaded,
      is NavigationUpdate.Moved,
      is NavigationUpdate.Added -> {
        _scrollNavigationHistoryToTopEvents.tryEmit(Unit)
      }
    }
  }

  private fun NavigationElement.toUiElement(): UiNavigationElement {
    return when (this) {
      is NavigationElement.Catalog -> {
        val iconUrl = siteManager.bySiteKey(this.chanDescriptor.siteKey)?.icon()?.toString()
        UiNavigationElement.Catalog(chanDescriptor, iconUrl)
      }
      is NavigationElement.Thread -> {
        UiNavigationElement.Thread(chanDescriptor, title, iconUrl)
      }
    }
  }

  private fun UiNavigationElement.toNavigationElement(): NavigationElement {
    return when (this) {
      is UiNavigationElement.Catalog -> NavigationElement.Catalog(chanDescriptor)
      is UiNavigationElement.Thread -> NavigationElement.Thread(chanDescriptor, title, iconUrl)
    }
  }

}