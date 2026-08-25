package cn.edu.ubaa.ui.screens.bus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.BusApi
import cn.edu.ubaa.api.local.busDirectApi
import cn.edu.ubaa.api.local.ensureCcpaySession
import cn.edu.ubaa.api.local.extractCashierUrl
import cn.edu.ubaa.model.dto.BusSessionUserDto
import cn.edu.ubaa.model.dto.BusShiftDto
import cn.edu.ubaa.model.dto.BusTicketDetailDto
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class BusUiState(
    val isInitialLoading: Boolean = false,
    val initialError: String? = null,
    val dates: List<String> = emptyList(),
    val selectedDate: String = "",
    val origin: String = "学院路",
    val terminal: String = "沙河",
    val shifts: List<BusShiftDto> = emptyList(),
    val isSearchingShifts: Boolean = false,
    val shiftsError: String? = null,
    val selectedShift: BusShiftDto? = null,
    val ticketDetail: BusTicketDetailDto? = null,
    val isTicketDetailLoading: Boolean = false,
    val captchaImage: ByteArray? = null,
    val isCaptchaLoading: Boolean = false,
    val captchaInput: String = "",
    val isBuying: Boolean = false,
    val buyError: String? = null,
    val sessionUser: BusSessionUserDto? = null,
    val pendingCashierUrl: String? = null,
    val pendingChannel: String = "wx",
    /** 下单成功后是否等待用户选择支付方式（微信/支付宝），选择前不拉起收银台。 */
    val payChannelPending: Boolean = false,
    /** cc-pay 会话是否已就绪（后台预热，就绪后才渲染收银台 WebView）。 */
    val ccpayReady: Boolean = false,
    val payMessage: String? = null,
    val actionMessage: String? = null,
)

@OptIn(ExperimentalTime::class)
class BusViewModel(
    private val busApi: BusApi = busDirectApi(),
    private val currentDateProvider: () -> String = {
      val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      val month = (now.month.ordinal + 1).toString().padStart(2, '0')
      val day = now.day.toString().padStart(2, '0')
      "${now.year}-$month-$day"
    },
) : ViewModel() {
  private val _uiState = MutableStateFlow(BusUiState())
  val uiState = _uiState.asStateFlow()

  private var initialLoadedOnce = false

  fun loadInitialData(forceRefresh: Boolean = false) {
    if (!forceRefresh && initialLoadedOnce) return
    if (_uiState.value.isInitialLoading) return
    initialLoadedOnce = true
    _uiState.value = _uiState.value.copy(isInitialLoading = true, initialError = null)
    viewModelScope.launch {
      val index = async { busApi.getIndexPage() }
      val user = async { busApi.getSessionUser() }
      val indexResult = index.await()
      val userResult = user.await()
      val page =
          indexResult.getOrElse { error ->
            _uiState.value =
                _uiState.value.copy(isInitialLoading = false, initialError = error.message)
            return@launch
          }
      val userDto = userResult.getOrNull()
      val today = currentDateProvider()
      val defaultDate =
          page.shiftsDateList.firstOrNull { it == today }
              ?: page.shiftsDateList.firstOrNull()
              ?: today
      _uiState.value =
          _uiState.value.copy(
              isInitialLoading = false,
              dates = page.shiftsDateList,
              selectedDate = defaultDate,
              sessionUser = userDto,
          )
      if (page.shiftsDateList.isNotEmpty()) {
        searchShiftsInternal()
      }
    }
  }

  fun selectDate(date: String) {
    if (_uiState.value.selectedDate == date) return
    _uiState.value =
        _uiState.value.copy(
            selectedDate = date,
            selectedShift = null,
            ticketDetail = null,
            shiftsError = null,
        )
    searchShiftsInternal()
  }

  fun swapDirection() {
    val s = _uiState.value
    _uiState.value =
        s.copy(origin = s.terminal, terminal = s.origin, selectedShift = null, ticketDetail = null)
    searchShiftsInternal()
  }

  fun searchShifts() = searchShiftsInternal()

  private fun searchShiftsInternal() {
    val s = _uiState.value
    val date = s.selectedDate
    if (date.isBlank()) return
    if (s.isSearchingShifts) return
    _uiState.value = s.copy(isSearchingShifts = true, shiftsError = null)
    viewModelScope.launch {
      busApi
          .searchShifts(s.origin, s.terminal, date)
          .onSuccess { response ->
            if (!response.success) {
              _uiState.value =
                  _uiState.value.copy(
                      isSearchingShifts = false,
                      shifts = emptyList(),
                      shiftsError = response.message.ifBlank { "当前日期没有可预约班次" },
                  )
              return@onSuccess
            }
            _uiState.value =
                _uiState.value.copy(
                    isSearchingShifts = false,
                    shifts = response.list,
                    shiftsError = if (response.list.isEmpty()) "当前日期没有可预约班次" else null,
                )
          }
          .onFailure { e ->
            _uiState.value = _uiState.value.copy(isSearchingShifts = false, shiftsError = e.message)
          }
    }
  }

  fun selectShift(shift: BusShiftDto) {
    val s = _uiState.value
    _uiState.value = s.copy(selectedShift = shift, ticketDetail = null, buyError = null)
    loadTicketDetail(shift)
  }

  fun loadTicketDetail(shift: BusShiftDto) {
    val date = shift.shifts_date.ifBlank { _uiState.value.selectedDate }
    if (date.isBlank()) return
    _uiState.value = _uiState.value.copy(isTicketDetailLoading = true, ticketDetail = null)
    viewModelScope.launch {
      busApi
          .getTicketDetail(date, shift.shifts_number)
          .onSuccess { detail ->
            _uiState.value =
                _uiState.value.copy(isTicketDetailLoading = false, ticketDetail = detail)
            refreshCaptchaInternal()
          }
          .onFailure { e ->
            _uiState.value =
                _uiState.value.copy(isTicketDetailLoading = false, actionMessage = e.message)
          }
    }
  }

  fun refreshCaptcha() = refreshCaptchaInternal()

  private fun refreshCaptchaInternal() {
    val s = _uiState.value
    if (s.isCaptchaLoading) return
    _uiState.value = s.copy(isCaptchaLoading = true, captchaInput = "", buyError = null)
    viewModelScope.launch {
      busApi
          .getCaptchaImage()
          .onSuccess { bytes ->
            _uiState.value = _uiState.value.copy(isCaptchaLoading = false, captchaImage = bytes)
          }
          .onFailure { e ->
            _uiState.value =
                _uiState.value.copy(isCaptchaLoading = false, actionMessage = e.message)
          }
    }
  }

  fun setCaptchaInput(value: String) {
    _uiState.value = _uiState.value.copy(captchaInput = value)
  }

  /** 确认订票：提交验证码 → 成功返回 ccpay 收银台地址（price>0）或免费票成功。 */
  fun buyTicket() {
    val s = _uiState.value
    val detail = s.ticketDetail ?: return
    val checkStr = s.captchaInput.trim()
    if (checkStr.isEmpty()) {
      _uiState.value = s.copy(buyError = "请输入验证码")
      return
    }
    val date = detail.shiftsDate.ifBlank { s.selectedDate }
    _uiState.value = s.copy(isBuying = true, buyError = null, payMessage = null)
    viewModelScope.launch {
      runCatching {
            val result =
                busApi.buyTicket(date, detail.shiftsNumber, checkStr, detail.csrfToken).getOrThrow()
            if (result.url.isNotBlank()) {
              // 解析收银台地址（cc-pay 会话改为后台预热，不阻塞弹窗）
              extractCashierUrl(result.url) ?: result.url
            } else {
              // price<=0 免费票：直接成功
              ""
            }
          }
          .onSuccess { cashierUrl ->
            _uiState.value =
                _uiState.value.copy(
                    isBuying = false,
                    captchaInput = "",
                    pendingCashierUrl = cashierUrl.takeIf { it.isNotBlank() },
                    payChannelPending = cashierUrl.isNotBlank(),
                    payMessage = if (cashierUrl.isBlank()) "订票成功" else "请选择支付方式",
                )
            if (cashierUrl.isNotBlank()) {
              // cc-pay 会话后台预热：不阻塞弹窗，就绪后才允许渲染收银台
              viewModelScope.launch {
                runCatching { ensureCcpaySession() }
                _uiState.value = _uiState.value.copy(ccpayReady = true)
              }
            }
          }
          .onFailure { e ->
            _uiState.value =
                _uiState.value.copy(isBuying = false, buyError = e.message ?: "订票失败，请稍后重试")
            refreshCaptchaInternal()
          }
    }
  }

  fun clearPendingPay() {
    _uiState.value =
        _uiState.value.copy(
            pendingCashierUrl = null,
            payMessage = null,
            pendingChannel = "wx",
            payChannelPending = false,
            ccpayReady = false,
        )
  }

  /** 用户选定支付方式后拉起 ccpay 收银台（channel: wx / ali）。 */
  fun choosePayChannel(channel: String) {
    val s = _uiState.value
    if (s.pendingCashierUrl.isNullOrBlank()) return
    _uiState.value =
        s.copy(
            pendingChannel = if (channel == "ali") "ali" else "wx",
            payChannelPending = false,
            payMessage = "正在唤起支付…",
        )
  }

  /** 用户取消支付：关闭选择框并放弃本次收银台。 */
  fun dismissPayChannel() {
    clearPendingPay()
  }

  fun setPendingChannel(channel: String) {
    _uiState.value = _uiState.value.copy(pendingChannel = channel)
  }

  fun clearActionMessage() {
    _uiState.value = _uiState.value.copy(actionMessage = null)
  }
}
