package cn.edu.ubaa.api.network

/**
 * 平台日志输出开关。release 构建下由应用入口(Debuggable 判断)关闭以提升性能。
 * Android 走 Log.d/e，其他平台走 println。
 */
var platformLogEnabled: Boolean = true

/** 平台日志输出。Android 走 Log.d，JVM 走 println。 */
expect fun platformLog(tag: String, message: String)
