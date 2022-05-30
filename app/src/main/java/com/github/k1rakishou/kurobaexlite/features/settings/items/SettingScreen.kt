package com.github.k1rakishou.kurobaexlite.features.settings.items


class SettingScreen(
  val key: SettingScreens,
  val groups: List<SettingGroup>
)

enum class SettingScreens {
  Main
}

class SettingScreenBuilder(
  private val key: SettingScreens
) {
  private val groups = mutableListOf<SettingGroup>()

  fun group(
    groupKey: String,
    groupName: String?,
    groupDescription: String? = null,
    builder: SettingGroupBuilder.() -> Unit
  ): SettingScreenBuilder {
    val settingGroupBuilder = SettingGroupBuilder(groupKey, groupName, groupDescription)
    builder(settingGroupBuilder)
    groups += settingGroupBuilder.build()

    return this
  }

  fun build(): SettingScreen {
    return SettingScreen(key, groups)
  }

}