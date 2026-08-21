package cn.edu.ubaa.api.storage

import com.russhwolf.settings.Settings

/** 电费缴费表号历史记录。最近使用的排在最前，最多保留 5 条。 */
object MeterNumberStore {
  private const val KEY_METERS = "electricity_meter_numbers"
  private const val MAX_HISTORY = 5

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  /** 记录一个表号到历史。已存在的会移到最前。 */
  fun add(meterNumber: String) {
    val trimmed = meterNumber.trim()
    if (trimmed.isBlank()) return
    val current = getAll().filter { it != trimmed }
    val updated = (listOf(trimmed) + current).take(MAX_HISTORY)
    settings.putString(KEY_METERS, updated.joinToString("\u0001"))
  }

  /** 获取历史表号列表，最近使用的排最前。 */
  fun getAll(): List<String> =
      settings.getStringOrNull(KEY_METERS)?.split("\u0001")?.filter { it.isNotBlank() }.orEmpty()

  /** 删除一条历史记录。 */
  fun remove(meterNumber: String) {
    val updated = getAll().filter { it != meterNumber }
    if (updated.isEmpty()) {
      settings.remove(KEY_METERS)
    } else {
      settings.putString(KEY_METERS, updated.joinToString("\u0001"))
    }
  }

  fun clear() {
    settings.remove(KEY_METERS)
  }
}
