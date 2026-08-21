package cn.edu.ubaa.api.network

import java.io.File

/** 调试文件输出目录，供 adb pull 拉取。 */
actual object DebugFileSink {
  actual fun write(fileName: String, content: String) {
    runCatching {
      // Android 上 java.io.tmpdir 指向 /data/user/0/<pkg>/cache，无需 Context
      val dir = File(System.getProperty("java.io.tmpdir") ?: ".", "ubaa_debug")
      dir.mkdirs()
      File(dir, fileName).writeText(content)
    }
  }
}
