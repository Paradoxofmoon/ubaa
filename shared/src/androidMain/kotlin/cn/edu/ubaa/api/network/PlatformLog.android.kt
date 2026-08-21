package cn.edu.ubaa.api.network

import android.util.Log

actual fun platformLog(tag: String, message: String) {
  if (!platformLogEnabled) return
  // 华为 EMUI 会过滤 D 级日志，用 E 级保证 logcat 可见
  Log.e(tag, message)
}
