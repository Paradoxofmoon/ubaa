package cn.edu.ubaa.api.network

import java.io.File

/** 调试文件输出目录，供 adb pull 拉取。 */
actual object DebugFileSink {
  actual fun write(fileName: String, content: String) {
    runCatching {
      val dir = File(System.getProperty("java.io.tmpdir"), "ubaa_debug")
      dir.mkdirs()
      File(dir, fileName).writeText(content)
    }
  }
}
