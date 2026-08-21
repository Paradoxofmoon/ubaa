package cn.edu.ubaa.runtime

import cn.edu.ubaa.api.network.platformLogEnabled

/**
 * 应用运行期配置。供 androidApp 入口设置调试日志开关。
 * release 构建调用方会传入 isDebuggable，从而关闭 shared 层平台日志。
 */
object LogConfig {
  var enabled: Boolean
    get() = platformLogEnabled
    set(value) {
      platformLogEnabled = value
    }
}
