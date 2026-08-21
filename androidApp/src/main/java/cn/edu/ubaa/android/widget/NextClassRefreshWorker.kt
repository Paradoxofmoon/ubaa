package cn.edu.ubaa.android.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * 周期刷新桌面小组件的 Worker。WorkManager 周期任务(最短15分钟)刷新课表小组件，
 * 锁屏时受系统 Doze 节能限制自然延长，总体较省电。
 */
class NextClassRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

  override fun doWork(): Result {
    return try {
      NextClassWidgetProvider.updateAll(applicationContext)
      Result.success()
    } catch (e: Exception) {
      Result.retry()
    }
  }
}
