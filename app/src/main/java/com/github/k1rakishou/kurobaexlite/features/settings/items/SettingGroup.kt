package com.github.k1rakishou.kurobaexlite.features.settings.items

import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.BooleanSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.EnumSetting
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.StringSetting
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
    subtitleBuilder: (AnnotatedString.Builder.() -> Unit)? = null,
    enabled: Boolean = true,
    dependencies: List<BooleanSetting> = emptyList(),
    onSettingUpdated: (suspend () -> Unit)? = null
  ): SettingGroupBuilder {
    settings += BooleanSettingItem(
      title = title,
      subtitle = buildSubtitle(subtitleBuilder),
      enabled = enabled,
      dependencies = dependencies,
      delegate = delegate,
      onSettingUpdated = onSettingUpdated
    )

    return this
  }

  fun <T : Enum<T>> enum(
    title: String,
    delegate: EnumSetting<T>,
    filterFunc: (T) -> Boolean = { true },
    showOptionsScreen: suspend (List<FloatingMenuItem>) -> String?,
    onSettingUpdated: (suspend () -> Unit)? = null,
    settingNameMapper: (Enum<T>) -> String = { enum -> enum.name },
    subtitleBuilder: (AnnotatedString.Builder.() -> Unit)? = null,
    dependencies: List<BooleanSetting> = emptyList(),
    enabled: Boolean = true
  ): SettingGroupBuilder {
    settings += EnumSettingItem(
      title = title,
      subtitle = buildSubtitle(subtitleBuilder),
      dependencies = dependencies,
      enabled = enabled,
      delegate = delegate,
      settingNameMapper = settingNameMapper,
      filterFunc = filterFunc,
      showOptionsScreen = showOptionsScreen,
      onSettingUpdated = onSettingUpdated
    )

    return this
  }

  fun link(
    key: String,
    title: String,
    enabled: Boolean = true,
    subtitleBuilder: (AnnotatedString.Builder.() -> Unit)? = null,
    onClicked: () -> Unit
  ): SettingGroupBuilder {
    settings += LinkSettingItem(
      key = key,
      title = title,
      subtitle = buildSubtitle(subtitleBuilder),
      enabled = enabled,
      onClicked = onClicked,
    )

    return this
  }

  fun string(
    title: String,
    enabled: Boolean,
    delegate: StringSetting,
    subtitleBuilder: (AnnotatedString.Builder.() -> Unit)? = null,
    showDialogScreen: suspend (DialogScreen.Params) -> Unit,
    dependencies: List<BooleanSetting> = emptyList(),
    settingNameMapper: (String) -> String = { it },
    onSettingUpdated: (suspend () -> Unit)? = null
  ): SettingGroupBuilder {
    settings += StringSettingItem(
      title = title,
      subtitle = buildSubtitle(subtitleBuilder),
      dependencies = dependencies,
      enabled = enabled,
      delegate = delegate,
      showDialogScreen = showDialogScreen,
      settingDisplayFormatter = settingNameMapper,
      onSettingUpdated = onSettingUpdated
    )

    return this
  }

  private fun buildSubtitle(
    subtitleBuilder: (AnnotatedString.Builder.() -> Unit)?
  ): AnnotatedString? {
    if (subtitleBuilder == null) {
      return null
    }

    val annotatedStringBuilder = AnnotatedString.Builder()
    subtitleBuilder(annotatedStringBuilder)
    return annotatedStringBuilder.toAnnotatedString()
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