package cn.edu.ubaa.android.widget

import cn.edu.ubaa.model.dto.TodayClass
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 一节课的视图模型：名称、地点、时间范围、距开始剩余分钟。 */
data class ClassView(
    val name: String,
    val place: String?,
    val begin: String,
    val end: String,
    val minutesUntil: Int, // 距开始剩余分钟（负数=已开始/正在进行）
)

/** 简化时间表示：HH:mm 转自当天零点起的分钟数。 */
internal data class TimeOfDay(val hour: Int, val minute: Int) {
  val minutes: Int get() = hour * 60 + minute

  fun toLabel(): String {
    val h = hour.toString().padStart(2, '0')
    val m = minute.toString().padStart(2, '0')
    return "$h:$m"
  }
}

/**
 * 从今日课表快照计算「下节课 + 再下节课」。
 * TodayClass.time 格式为 "HH:mm-HH:mm"（如 "13:50-15:30"）。
 */
object NextClassCalculator {

  /** 解析 "HH:mm" 字符串为 TimeOfDay；失败返回 null。 */
  private fun parseTime(s: String): TimeOfDay? {
    val parts = s.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return TimeOfDay(h, m)
  }

  internal data class ParsedRange(val begin: TimeOfDay, val end: TimeOfDay)

  /** 解析 "HH:mm-HH:mm" 为 (开始,结束)。解析失败返回 null。 */
  internal fun parseRange(time: String?): ParsedRange? {
    if (time.isNullOrBlank()) return null
    val parts = time.split("-")
    if (parts.size < 2) return null
    val b = parseTime(parts[0].trim()) ?: return null
    val e = parseTime(parts[1].trim()) ?: return null
    return ParsedRange(b, e)
  }

  /** 当前时间(今天)的小时与分钟。 */
  private fun nowTimeOfDay(): TimeOfDay {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return TimeOfDay(now.hour, now.minute)
  }

  /**
   * @param classes 今日课程（来自快照）
   * @param nowMinutes 当前时间距 0 点的分钟数
   * @return 0-2 节：尚未开始且时间最早的两节课。
   */
  fun nextClasses(classes: List<TodayClass>): List<ClassView> {
    val now = nowTimeOfDay()
    val nowMin = now.minutes
    val sorted =
        classes.mapNotNull { c -> parseRange(c.time)?.let { it to c } }
            .filter { it.first.begin.minutes >= nowMin } // 尚未开始
            .sortedBy { it.first.begin.minutes }
            .take(2)

    return sorted.map { (range, c) ->
      val mins = range.begin.minutes - nowMin
      ClassView(
          name = c.bizName,
          place = c.place,
          begin = range.begin.toLabel(),
          end = range.end.toLabel(),
          minutesUntil = mins,
      )
    }
  }

  /** 今日日期(yyyy-MM-dd)，用于判断快照是否属于今天。 */
  fun todayString(): String =
      Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}
