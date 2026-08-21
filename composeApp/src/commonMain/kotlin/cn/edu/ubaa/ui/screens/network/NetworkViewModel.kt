package cn.edu.ubaa.ui.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.ZfwApi
import cn.edu.ubaa.model.dto.TrafficData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 校园网流量界面 UI 状态。 */
data class NetworkUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val trafficData: TrafficData = TrafficData(),
    val error: String? = null,
    /** 是否因未登录深澜门户而需要先登录（跳转到充值页登录）。 */
    val needsZfwLogin: Boolean = false,
)

/** 校园网流量查询的 ViewModel（数据源为深澜自助服务门户 zfw.buaa.edu.cn）。 */
class NetworkViewModel(
    private val zfwApi: ZfwApi = ZfwApi(),
) : ViewModel() {
  private var loadedOnce = false

  private val _state = MutableStateFlow(NetworkUiState())
  val state: StateFlow<NetworkUiState> = _state.asStateFlow()

  /** 首次加载或按需刷新流量。 */
  fun ensureLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && loadedOnce) return
    loadTraffic()
  }

  /** 下拉刷新入口。 */
  fun refresh() {
    loadTraffic()
  }

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    loadedOnce = false
    _state.value = NetworkUiState()
  }

  private fun loadTraffic() {
    loadedOnce = true
    viewModelScope.launch {
      val current = _state.value
      _state.value =
          current.copy(
              isLoading = !current.trafficData.hasAnyData() && !current.isRefreshing,
              isRefreshing = current.trafficData.hasAnyData(),
              error = null,
          )

      zfwApi
          .getTraffic()
          .onSuccess { data ->
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    trafficData = data,
                    error = null,
                    needsZfwLogin = false,
                )
          }
          .onFailure { error ->
            val message = error.message ?: "加载校园网流量失败"
            // 未登录/会话过期时提示先去充值页登录
            val needsLogin =
                message.contains("登录") || message.contains("会话") || message.contains("过期")
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = if (needsLogin) "请先在「校园网充值」中登录，再查询流量" else message,
                    needsZfwLogin = needsLogin,
                )
          }
    }
  }

  /** 清空错误提示。 */
  fun clearError() {
    _state.value = _state.value.copy(error = null)
  }

  private fun TrafficData.hasAnyData(): Boolean {
    return freeTrafficTotal > 0.0 ||
        freeTrafficRemaining > 0.0 ||
        giftTrafficTotal != null ||
        giftTrafficRemaining != null ||
        paidTraffic != null ||
        paidTrafficRemaining != null ||
        usedTraffic != null ||
        settleDate != null
  }
}
