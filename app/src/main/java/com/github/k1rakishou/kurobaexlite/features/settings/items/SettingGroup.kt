package com.github.k1rakishou.kurobaexlite.features.settings.items

import com.github.k1rakishou.kurobaexlite.helpers.settings.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.EnumSetting
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem

class SettingGroup(
  val groupKey: String,
  val groupName: String?,
  val settingItems: List<SettingItem>
)

class SettingGroupBuilder(
  private val groupKey: String,
  private val groupName: String?
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

  fun <T : Enum<T>> list(
    title: String,
    delegate: EnumSetting<T>,
    showOptionsScreen: suspend (List<FloatingMenuItem>) -> String?,
    subtitle: String? = null,
    dependencies: List<BooleanSetting> = emptyList(),
    enabled: Boolean = true
  ): SettingGroupBuilder {
    settings += ListSettingItem(
      title = title,
      subtitle = subtitle,
      dependencies = dependencies,
      enabled = enabled,
      delegate = delegate,
      showOptionsScreen = showOptionsScreen
    )

    return this
  }

  fun build(): SettingGroup {
    return SettingGroup(
      groupKey = groupKey,
      groupName = groupName,
      settingItems = settings
    )
  }
}