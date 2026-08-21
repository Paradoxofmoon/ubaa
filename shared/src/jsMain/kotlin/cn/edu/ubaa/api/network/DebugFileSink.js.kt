package cn.edu.ubaa.api.network

/** 调试文件输出。JS（浏览器）无文件系统，no-op（与 iOS 一致）。 */
actual object DebugFileSink {
  actual fun write(fileName: String, content: String) {
    // no-op on JS
  }
}
