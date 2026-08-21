package cn.edu.ubaa.api.network

/** 调试文件输出，供诊断使用。 */
expect object DebugFileSink {
  fun write(fileName: String, content: String)
}
