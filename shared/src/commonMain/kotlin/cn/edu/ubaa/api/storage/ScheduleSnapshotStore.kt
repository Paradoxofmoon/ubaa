package cn.edu.ubaa.api.storage

import cn.edu.ubaa.model.dto.TodayClass
import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 今日课表快照存储。App 每次成功加载今日课表时写入，供桌面小组件离线读取显示。
 * 保存的是「当天日期的课程」，并记录快照日期，小组件只在日期匹配当天时使用。
 */
object ScheduleSnapshotStore {
  private const val KEY_CLASSES = "schedule_snapshot_classes"
  private const val KEY_DATE = "schedule_snapshot_date"

  private val json = Json { ignoreUnknownKeys = true }
  private val classesSerializer = ListSerializer(TodayClass.serializer())

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  /** 保存今日课表快照。@param today yyyy-MM-dd，用于标记快照对应的日期。 */
  fun save(today: String, classes: List<TodayClass>) {
    runCatching {
      settings.putString(KEY_CLASSES, json.encodeToString(classesSerializer, classes))
      settings.putString(KEY_DATE, today)
    }
  }

  /** 读取快照课程列表。可能为空（未保存或取数据异常）。 */
  fun loadClasses(): List<TodayClass> {
    val raw = settings.getStringOrNull(KEY_CLASSES) ?: return emptyList()
    return runCatching { json.decodeFromString(classesSerializer, raw) }.getOrNull() ?: emptyList()
  }

  /** 读取快照对应日期（yyyy-MM-dd）。 */
  fun loadSnapshotDate(): String? = settings.getStringOrNull(KEY_DATE)

  /** 清除快照。 */
  fun clear() {
    settings.remove(KEY_CLASSES)
    settings.remove(KEY_DATE)
  }
}
