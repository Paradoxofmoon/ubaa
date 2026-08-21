package cn.edu.ubaa.api.network

/** 调试文件输出，供诊断使用。iOS 暂不实现。 */
actual object DebugFileSink {
  actual fun write(fileName: String, content: String) {
    // no-op on iOS
  }
}
