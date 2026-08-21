package cn.edu.ubaa.api.network

/** 平台日志输出。wasmJs 走 console（println 输出到浏览器 console）。 */
actual fun platformLog(tag: String, message: String) {
  if (!platformLogEnabled) return
  println("[$tag] $message")
}
