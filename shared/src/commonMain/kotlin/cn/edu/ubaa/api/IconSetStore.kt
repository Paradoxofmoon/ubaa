package cn.edu.ubaa.api

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 全局图标风格偏好。 */
enum class IconSet(val storageKey: String, val displayName: String, val description: String) {
  MATERIAL_ROUNDED(
      storageKey = "material_rounded",
      displayName = "Material Rounded",
      description = "Material 家族圆润字重（默认）",
  ),
  TABLER(
      storageKey = "tabler",
      displayName = "Tabler 线性",
      description = "细线条极简线性风（MIT License）",
  );

  companion object {
    fun fromStorageKey(value: String?): IconSet? =
        entries.firstOrNull { it.storageKey == value?.trim()?.lowercase() }
  }
}

/** 图标风格存储（持久化 + 可观察 StateFlow，切换即时生效）。 */
object IconSetStore {
  private const val KEY_ICON_SET = "icon_set"

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  private val _current = MutableStateFlow(load())
  val current: StateFlow<IconSet> = _current.asStateFlow()

  fun get(): IconSet = _current.value

  fun set(iconSet: IconSet) {
    runCatching { settings.putString(KEY_ICON_SET, iconSet.storageKey) }
    _current.value = iconSet
  }

  fun clear() {
    runCatching { settings.remove(KEY_ICON_SET) }
    _current.value = IconSet.MATERIAL_ROUNDED
  }

  private fun load(): IconSet =
      IconSet.fromStorageKey(runCatching { settings.getStringOrNull(KEY_ICON_SET) }.getOrNull())
          ?: IconSet.MATERIAL_ROUNDED
}
