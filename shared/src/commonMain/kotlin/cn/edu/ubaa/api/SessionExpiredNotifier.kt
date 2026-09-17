package cn.edu.ubaa.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局「会话已失效」事件总线。
 *
 * 本地业务 API（校园卡/课表/场馆等）探测到登录态失效并清理共享会话时，通过 [notifySessionExpired] 发射事件；App 层收集后触发认证恢复流程（UI 切回登录态 +
 * 尝试静默重登）。解决"登录态掉了但各种功能报错、不自动重登"的问题。
 *
 * 无收集者时事件直接丢弃（tryEmit 到无订阅的 SharedFlow 安全无害）。
 */
object SessionExpiredNotifier {
  private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
  val events: SharedFlow<Unit> = _events.asSharedFlow()

  fun notifySessionExpired() {
    _events.tryEmit(Unit)
  }
}
