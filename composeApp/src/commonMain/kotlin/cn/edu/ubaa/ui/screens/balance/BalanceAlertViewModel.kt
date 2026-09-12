package cn.edu.ubaa.ui.screens.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.balance.BalanceAlertPolicy
import cn.edu.ubaa.api.balance.BalanceAlertStorage
import cn.edu.ubaa.api.balance.BalanceAlertStore
import cn.edu.ubaa.api.feature.CardApi
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 余额提醒 UI 状态。 */
data class BalanceAlertUiState(
    val thresholdYuan: Double? = null,
    val bannerVisible: Boolean = false,
    val bannerBalanceYuan: Double? = null,
)

/**
 * 余额提醒 ViewModel：进入首页后后台静默检测一次校园卡余额，低于阈值时在首页显示横幅（每天最多一次，可手动关闭）。
 *
 * 所有失败路径静默：未登录/会话过期/网络失败均不打扰用户。
 */
class BalanceAlertViewModel(
    private val cardApi: CardApi = CardApi(),
    private val storage: BalanceAlertStorage = BalanceAlertStore,
    private val todayProvider: () -> String = { defaultToday() },
) : ViewModel() {
  private val _state =
      MutableStateFlow(BalanceAlertUiState(thresholdYuan = storage.getThresholdYuan()))
  val state: StateFlow<BalanceAlertUiState> = _state.asStateFlow()

  /** 进入首页后调用：后台静默检测一次。未设阈值/今天已提醒/今天已关闭 → 不查。 */
  fun checkOnHomeEntered() {
    val today = todayProvider()
    val threshold = storage.getThresholdYuan() ?: return
    if (
        !BalanceAlertPolicy.shouldFetch(today, storage.getLastAlertDate(), storage.getDismissDate())
    ) {
      return
    }
    viewModelScope.launch {
      cardApi
          .getBalance()
          .onSuccess { data ->
            // 解析失败(null)视为本次查询无效，不误报（不能把失败当 0 元）
            val balance = data.balance.toDoubleOrNull()
            if (
                BalanceAlertPolicy.shouldAlert(
                    balance,
                    threshold,
                    storage.getLastAlertDate(),
                    today,
                )
            ) {
              storage.markAlerted(today)
              _state.update { it.copy(bannerVisible = true, bannerBalanceYuan = balance) }
            }
          }
          .onFailure { /* 静默：网络/会话问题不打扰 */ }
    }
  }

  /** 用户手动关闭横幅：当天不再显示。 */
  fun dismissBanner() {
    storage.markDismissed(todayProvider())
    _state.update { it.copy(bannerVisible = false) }
  }

  /** 更新阈值（null = 关闭提醒）。由校园卡页调用。 */
  fun setThresholdYuan(value: Double?) {
    storage.setThresholdYuan(value)
    _state.update { it.copy(thresholdYuan = value, bannerVisible = false) }
  }
}

private fun defaultToday(): String =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
