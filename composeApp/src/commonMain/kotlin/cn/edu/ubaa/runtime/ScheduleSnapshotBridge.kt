package cn.edu.ubaa.runtime

import cn.edu.ubaa.api.storage.ScheduleSnapshotStore
import cn.edu.ubaa.model.dto.TodayClass

/**
 * 供 androidApp 桌面小组件读取课表快照的桥接。
 * 小组件(androidApp)不直接依赖 shared，通过此桥接读取今日课表快照。
 */
object ScheduleSnapshotBridge {
  fun todayClasses(): List<TodayClass> = ScheduleSnapshotStore.loadClasses()

  /** 快照对应日期(yyyy-MM-dd)。小组件用它判断快照是否属于今天。 */
  fun snapshotDate(): String? = ScheduleSnapshotStore.loadSnapshotDate()

  fun saveSnapshot(today: String, classes: List<TodayClass>) =
      ScheduleSnapshotStore.save(today, classes)
}
