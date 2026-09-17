package cn.edu.ubaa.api.storage

import cn.edu.ubaa.model.dto.Term
import cn.edu.ubaa.model.dto.TodayClass
import cn.edu.ubaa.model.dto.Week
import cn.edu.ubaa.model.dto.WeeklySchedule
import com.russhwolf.settings.Settings
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 课表本地缓存（stale-while-revalidate）。
 *
 * 每次启动先读取缓存立即展示（服务器慢/挂掉也不影响看到课表），随后在后台核对网络更新。 单一 JSON bundle 落盘：学期列表、各学期周次、各「学期|周次」的完整课表、今日课表。
 */
@Serializable
data class ScheduleCacheBundle(
    val savedAtEpochMs: Long = 0L,
    val terms: List<Term> = emptyList(),
    /** key = 学期代码（term.itemCode）。 */
    val weeksByTerm: Map<String, List<Week>> = emptyMap(),
    /** key = "学期代码|周次序号"（见 [ScheduleCacheStore.termWeekKey]）。 */
    val scheduleByTermWeek: Map<String, WeeklySchedule> = emptyMap(),
    val todayClasses: List<TodayClass> = emptyList(),
    /** 今日课表对应的日期（yyyy-MM-dd），只在日期匹配当天时使用缓存。 */
    val todayDate: String = "",
)

/** 课表本地缓存存储。 */
object ScheduleCacheStore {
  private const val KEY_BUNDLE = "schedule_cache_bundle_v1"

  /** 课表条目上限：只保留最近浏览过的 N 个「学期|周次」，防止缓存无限膨胀。 */
  private const val MAX_SCHEDULE_ENTRIES = 30

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  /** 读取缓存。无缓存或解析失败时返回空 bundle（非 null）。 */
  fun load(): ScheduleCacheBundle {
    val raw = settings.getStringOrNull(KEY_BUNDLE) ?: return ScheduleCacheBundle()
    return runCatching { json.decodeFromString<ScheduleCacheBundle>(raw) }.getOrNull()
        ?: ScheduleCacheBundle()
  }

  private fun save(bundle: ScheduleCacheBundle) {
    runCatching { settings.putString(KEY_BUNDLE, json.encodeToString(bundle)) }
  }

  /** 更新学期列表。 */
  fun updateTerms(terms: List<Term>, savedAtEpochMs: Long = now()) {
    if (terms.isEmpty()) return
    val bundle = load()
    save(bundle.copy(terms = terms, savedAtEpochMs = savedAtEpochMs))
  }

  /** 更新某学期的周次列表。 */
  fun updateWeeks(term: Term, weeks: List<Week>, savedAtEpochMs: Long = now()) {
    if (weeks.isEmpty()) return
    val bundle = load()
    val merged = bundle.weeksByTerm.toMutableMap()
    merged[term.itemCode] = weeks
    save(bundle.copy(weeksByTerm = merged, savedAtEpochMs = savedAtEpochMs))
  }

  /** 更新某「学期|周次」的完整课表，超限时丢弃最旧的条目。 */
  fun updateWeeklySchedule(
      term: Term,
      week: Week,
      schedule: WeeklySchedule,
      savedAtEpochMs: Long = now(),
  ) {
    val bundle = load()
    val merged = bundle.scheduleByTermWeek.toMutableMap()
    merged[termWeekKey(term, week)] = schedule
    while (merged.size > MAX_SCHEDULE_ENTRIES) {
      merged.remove(merged.keys.first())
    }
    save(bundle.copy(scheduleByTermWeek = merged, savedAtEpochMs = savedAtEpochMs))
  }

  /** 更新今日课表。@param todayDate yyyy-MM-dd，仅当天匹配时可用。 */
  fun updateToday(todayDate: String, classes: List<TodayClass>, savedAtEpochMs: Long = now()) {
    val bundle = load()
    save(
        bundle.copy(
            todayDate = todayDate,
            todayClasses = classes,
            savedAtEpochMs = savedAtEpochMs,
        )
    )
  }

  /** 课表缓存的键：学期代码 + 周次序号。 */
  fun termWeekKey(term: Term, week: Week): String = "${term.itemCode}|${week.serialNumber}"

  /** 清除缓存。 */
  fun clear() {
    runCatching { settings.remove(KEY_BUNDLE) }
  }

  private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
