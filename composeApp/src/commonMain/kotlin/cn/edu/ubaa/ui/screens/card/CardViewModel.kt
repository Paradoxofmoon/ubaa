package cn.edu.ubaa.ui.screens.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.CardApi
import cn.edu.ubaa.api.feature.CardPayWay
import cn.edu.ubaa.api.feature.CardRechargeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 校园卡界面 UI 状态。 */
data class CardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val balance: String = "",
    val error: String? = null,
    // ---- 充值状态 ----
    val amount: String = "",
    val payWays: List<CardPayWay> = emptyList(),
    val isLoadingPayWays: Boolean = false,
    val isRecharging: Boolean = false,
    // 待用的真实收银台地址 + 用户选定的支付渠道(用于注入脚本自动点支付)
    val pendingCashierUrl: String? = null,
    val pendingChannel: String? = null,
)

/** 校园卡余额查询 + 充值 ViewModel。 */
class CardViewModel(
    private val cardApi: CardApi = CardApi(),
) : ViewModel() {
  private var loadedOnce = false

  private val _state = MutableStateFlow(CardUiState())
  val state: StateFlow<CardUiState> = _state.asStateFlow()

  /** 首次加载或按需刷新余额。 */
  fun ensureLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && loadedOnce) return
    loadBalance()
  }

  /** 下拉刷新入口。 */
  fun refresh() {
    loadBalance()
    loadPayWays()
  }

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    loadedOnce = false
    _state.value = CardUiState()
  }

  private fun loadBalance() {
    loadedOnce = true
    viewModelScope.launch {
      val current = _state.value
      _state.value =
          current.copy(
              isLoading = current.balance.isBlank() && !current.isRefreshing,
              isRefreshing = current.balance.isNotBlank(),
              error = null,
          )

      cardApi
          .getBalance()
          .onSuccess { data ->
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    balance = formatMoney(data.balance),
                    error = null,
                )
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = error.message ?: "加载校园卡余额失败",
                )
          }
    }
  }

  // ===== 充值 =====

  /** 加载充值可用的支付方式（移动端 App 直接支付方式）。 */
  fun loadPayWays() {
    if (_state.value.isLoadingPayWays) return
    _state.value = _state.value.copy(isLoadingPayWays = true, error = null)
    viewModelScope.launch {
      cardApi
          .getRechargePayWays()
          .onSuccess { ways ->
            _state.value =
                _state.value.copy(isLoadingPayWays = false, payWays = ways)
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(
                    isLoadingPayWays = false,
                    error = error.message ?: "加载支付方式失败",
                )
          }
    }
  }

  fun onAmountChange(value: String) {
    _state.value = _state.value.copy(amount = value)
  }

  /** 发起充值：下单拿到收银台地址，交隐藏 WebView 加载真实收银台页并自动点支付唤起。 */
  fun beginRecharge(payWayId: String) {
    val amount = _state.value.amount
    if (amount.isBlank()) {
      _state.value = _state.value.copy(error = "请输入充值金额")
      return
    }
    val amountValue = amount.toDoubleOrNull()
    if (amountValue == null || amountValue < 1 || amountValue > 90000) {
      _state.value = _state.value.copy(error = "充值金额需在 1~90000 元之间")
      return
    }
    _state.value = _state.value.copy(isRecharging = true, error = null, pendingCashierUrl = null, pendingChannel = null)
    viewModelScope.launch {
      cardApi
          .beginRecharge(amount, payWayId)
          .onSuccess { result ->
            val cashierUrl = result.cashierUrl?.takeIf { it.isNotBlank() }
            _state.value =
                _state.value.copy(
                    isRecharging = false,
                    pendingCashierUrl = cashierUrl,
                    pendingChannel = channelOf(payWayId),
                    error = if (cashierUrl.isNullOrBlank()) "未获取到收银台地址" else null,
                )
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(isRecharging = false, error = error.message ?: "充值失败，请稍后重试")
          }
    }
  }

  /** 支付已处理完成后清理。 */
  fun clearPendingPay() {
    _state.value = _state.value.copy(pendingCashierUrl = null, pendingChannel = null)
  }

  /** 依据 payWayId 推断支付渠道（微信=5acada61..., 支付宝=5acada60..., 数字人民币=ecpay...）。 */
  private fun channelOf(payWayId: String): String =
      when {
        payWayId.startsWith("5acada61") || payWayId.contains("wx") || payWayId.contains("weixin") -> "wx"
        payWayId.startsWith("5acada60") || payWayId.contains("ali") -> "ali"
        else -> "wx"
      }

  /** 清空错误提示。 */
  fun clearError() {
    _state.value = _state.value.copy(error = null)
  }
}

private fun formatMoney(amount: String): String {
  val value = amount.toDoubleOrNull() ?: 0.0
  return "¥ ${formatTwoDecimals(value)}"
}

private fun formatTwoDecimals(value: Double): String {
  val scaled = (value * 100).toLong()
  val whole = scaled / 100
  val fraction = kotlin.math.abs(scaled % 100)
  return "$whole.${fraction.toString().padStart(2, '0')}"
}
