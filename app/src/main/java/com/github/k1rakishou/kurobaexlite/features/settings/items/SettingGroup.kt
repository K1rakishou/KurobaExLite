package com.github.k1rakishou.kurobaexlite.features.settings.items

import com.github.k1rakishou.kurobaexlite.helpers.settings.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.EnumSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.StringSetting
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem

class SettingGroup(
  val groupKey: String,
  val groupName: String?,
  val groupDescription: String?,
  val settingItems: List<SettingItem>
)

class SettingGroupBuilder(
  private val groupKey: String,
  private val groupName: String?,
  private val groupDescription: String?
) {
  private val settings = mutableListOf<SettingItem>()

  fun boolean(
    title: String,
    delegate: BooleanSetting,
    subtitle: String? = null,
    enabled: Boolean = true,
    dependencies: List<BooleanSetting> = emptyList()
  ): SettingGroupBuilder {
    settings += BooleanSettingItem(
      title = title,
      subtitle = subtitle,
      enabled = enabled,
      dependencies = dependencies,
      delegate = delegate
    )

    return this
  }

  fun <T : Enum<T>> enum(
    title: String,
    delegate: EnumSetting<T>,
    showOptionsScreen: suspend (List<FloatingMenuItem>) -> String?,
    onSettingUpdated: (suspend () -> Unit)? = null,
    settingNameMapper: (Enum<T>) -> String = { enum -> enum.name },
    subtitle: String? = null,
    dependencies: List<BooleanSetting> = emptyList(),
    enabled: Boolean = true
  ): SettingGroupBuilder {
    settings += EnumSettingItem(
      title = title,
      subtitle = subtitle,
      dependencies = dependencies,
      enabled = enabled,
      delegate = delegate,
      settingNameMapper = settingNameMapper,
      showOptionsScreen = showOptionsScreen,
      onSettingUpdated = onSettingUpdated
    )

    return this
  }

  fun link(
    key: String,
    title: String,
    enabled: Boolean = true,
    subtitle: String? = null,
    onClicked: () -> Unit
  ): SettingGroupBuilder {
    settings += LinkSettingItem(
      key = key,
      title = title,
      subtitle = subtitle,
      enabled = enabled,
      onClicked = onClicked,
    )

    return this
  }

  fun string(
    title: String,
    enabled: Boolean,
    delegate: StringSetting,
    subtitle: String? = null,
    showDialogScreen: suspend (DialogScreen.Params) -> Unit,
    dependencies: List<BooleanSetting> = emptyList(),
    settingNameMapper: (String) -> String = { it },
    onSettingUpdated: (suspend () -> Unit)? = null
  ): SettingGroupBuilder {
    settings += StringSettingItem(
      title = title,
      subtitle = subtitle,
      dependencies = dependencies,
      enabled = enabled,
      delegate = delegate,
      showDialogScreen = showDialogScreen,
      settingValueMapper = settingNameMapper,
      onSettingUpdated = onSettingUpdated
    )

    return this
  }

  fun build(): SettingGroup {
    return SettingGroup(
      groupKey = groupKey,
      groupName = groupName,
      groupDescription = groupDescription,
      settingItems = settings
    )
  }
}