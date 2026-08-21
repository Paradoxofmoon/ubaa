package cn.edu.ubaa.api.network

/** 平台日志输出。JS 走 console（Kotlin/JS 的 println 输出到浏览器 console）。 */
actual fun platformLog(tag: String, message: String) {
  if (!platformLogEnabled) return
  println("[$tag] $message")
}
