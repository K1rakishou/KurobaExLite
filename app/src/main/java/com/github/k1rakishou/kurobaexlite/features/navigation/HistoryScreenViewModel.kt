package com.github.k1rakishou.kurobaexlite.features.navigation

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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class HistoryScreenViewModel : BaseViewModel() {
  private val siteManager: SiteManager by inject(SiteManager::class.java)
  private val appSettings: AppSettings by inject(AppSettings::class.java)
  private val loadNavigationHistory: LoadNavigationHistory by inject(LoadNavigationHistory::class.java)
  private val modifyNavigationHistory: ModifyNavigationHistory by inject(ModifyNavigationHistory::class.java)
  private val persistNavigationHistory: PersistNavigationHistory by inject(PersistNavigationHistory::class.java)
  private val navigationHistoryManager: NavigationHistoryManager by inject(NavigationHistoryManager::class.java)

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
    viewModelScope.launch {
      navigationHistoryManager.navigationUpdates.collectLatest { navigationUpdate ->
        processNavigationUpdates(navigationUpdate)
      }
    }

    loadNavigationHistory.loadFromDatabase(appSettings.navigationHistoryMaxSize.read())
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
          .indexOfFirst { uiNavigationElement -> uiNavigationElement == uiElement }
        if (index >= 0) {
          _navigationHistoryList.removeAt(index)
          _removedElementsFlow.tryEmit(Pair(index, uiElement))
        }
      }
      is NavigationUpdate.Moved -> {
        val movedElement = navigationUpdate.navigationElement.toUiElement()

        val prevUiElement = _navigationHistoryList.getOrNull(navigationUpdate.prevIndex)
        if (prevUiElement != null && prevUiElement == movedElement) {
          _navigationHistoryList.add(0, _navigationHistoryList.removeAt(navigationUpdate.prevIndex))
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