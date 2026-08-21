package cn.edu.ubaa.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import cn.edu.ubaa.android.R
import cn.edu.ubaa.runtime.ScheduleSnapshotBridge

/**
 * 桌面小组件：显示今日下节课(+再下节课)内容与剩余时间。
 * 数据来自 App 同步课表时写入的本地快照(shared 的 ScheduleSnapshotStore)。
 */
class NextClassWidgetProvider : AppWidgetProvider() {

  companion object {
    const val ACTION_REFRESH = "cn.edu.ubaa.android.widget.REFRESH"

    /** 渲染并更新小组件。供 onUpdate 与 WorkManager 周期刷新调用。 */
    fun updateAll(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val ids =
          manager.getAppWidgetIds(
              ComponentName(context, NextClassWidgetProvider::class.java))
      if (ids.isEmpty()) return
      val views = buildRemoteViews(context)
      ids.forEach { manager.updateAppWidget(it, views) }
    }

    /** 启动周期刷新(WorkManager, 最短15分钟)。首次添加 widget 时调用，幂等。 */
    fun ensurePeriodicRefresh(context: Context) {
      val request =
          androidx.work.PeriodicWorkRequestBuilder<NextClassRefreshWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
              .build()
      androidx.work.WorkManager.getInstance(context)
          .enqueueUniquePeriodicWork(
              "next_class_widget_refresh",
              androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
              request)
    }

    /**
     * 构建小组件视图。逻辑：
     * - 若快照非今天(或为空)：显示占位"尚未同步今日课表，打开App同步"
     * - 有课：按 NextClassCalculator.nextClasses 取两节，分别赋值标题/详情；隐藏空行
     * - 今天无课：显示"今天没有课程安排"
     */
    fun buildRemoteViews(context: Context): RemoteViews {
      val views = RemoteViews(context.packageName, R.layout.next_class_widget)

      // 点击刷新
      val refreshIntent =
          Intent(context, NextClassWidgetProvider::class.java).setAction(ACTION_REFRESH)
      val pi =
          PendingIntent.getBroadcast(
              context, 0, refreshIntent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      views.setOnClickPendingIntent(R.id.widget_root, pi)

      val today = ScheduleSnapshotBridge.snapshotDate()
      val isToday = today == NextClassCalculator.todayString()
      val classes = if (isToday) ScheduleSnapshotBridge.todayClasses() else emptyList()

      val next = NextClassCalculator.nextClasses(classes)

      views.setTextViewText(R.id.widget_title, "今日下节课")

      if (!isToday) {
        // 快照不是今天：提示去 App 同步
        views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_empty, "打开 App 同步今日课表")
        setRowVisibility(views, R.id.class1_row, false)
        setRowVisibility(views, R.id.class2_row, false)
      } else if (next.isEmpty()) {
        views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_empty, "今天没有课程安排")
        setRowVisibility(views, R.id.class1_row, false)
        setRowVisibility(views, R.id.class2_row, false)
      } else {
        views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
        // 第 1 节
        setRowVisibility(views, R.id.class1_row, true)
        populate(views, R.id.class1_row, R.id.class1_title, R.id.class1_meta, next[0])
        // 第 2 节
        if (next.size >= 2) {
          setRowVisibility(views, R.id.class2_row, true)
          populate(views, R.id.class2_row, R.id.class2_title, R.id.class2_meta, next[1])
        } else {
          setRowVisibility(views, R.id.class2_row, false)
        }
      }
      return views
    }

    private fun populate(
        views: RemoteViews,
        rowId: Int,
        titleId: Int,
        metaId: Int,
        cv: ClassView,
    ) {
      views.setTextViewText(titleId, cv.name)
      val timeStr = "${cv.begin} - ${cv.end}"
      val countdown = describe(cv.minutesUntil)
      val meta = listOfNotNull(cv.place?.takeIf { it.isNotBlank() }, timeStr, countdown)
          .joinToString("  ")
      views.setTextViewText(metaId, meta)
    }

    private fun describe(minutes: Int): String =
        when {
          minutes <= 0 -> "即将开始"
          minutes < 60 -> "还有 $minutes 分钟开始"
          else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "还有 $h 小时" else "还有 ${h}h${m}m"
          }
        }

    private fun setRowVisibility(views: RemoteViews, rowId: Int, visible: Boolean) {
      views.setViewVisibility(
          rowId, if (visible) android.view.View.VISIBLE else android.view.View.GONE)
    }
  }

  override fun onUpdate(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
  ) {
    updateAll(context)
    ensurePeriodicRefresh(context)
  }

  override fun onReceive(context: Context, intent: Intent?) {
    super.onReceive(context, intent)
    if (intent?.action == ACTION_REFRESH) {
      updateAll(context)
    }
  }
}
