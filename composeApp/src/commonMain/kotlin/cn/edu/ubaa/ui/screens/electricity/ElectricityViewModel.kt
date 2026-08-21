package cn.edu.ubaa.ui.screens.electricity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.local.ensureCcpaySession
import cn.edu.ubaa.api.local.extractCashierUrl
import cn.edu.ubaa.api.storage.MeterNumberStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 电费购电 UI 状态。 */
data class ElectricityUiState(
    // ---- 电表查询 tab ----
    val isLoadingTree: Boolean = false,
    val meters: List<ElectricityMeter> = emptyList(),
    val campuses: List<String> = emptyList(),
    val buildings: List<String> = emptyList(),
    val floors: List<String> = emptyList(),
    val rooms: List<String> = emptyList(),
    val meterOptions: List<ElectricityMeter> = emptyList(),
    val selectedCampus: String? = null,
    val selectedBuilding: String? = null,
    val selectedFloor: String? = null,
    val selectedRoom: String? = null,
    val selectedMeter: ElectricityMeter? = null,
    // ---- 电费缴费 tab ----
    val meterNumber: String = "",
    val meterHistory: List<String> = emptyList(),
    val isLoadingMeter: Boolean = false,
    val meterInfo: ElectricityMeterInfo? = null,
    val power: String = "",
    val computedPower: Int? = null,
    val computedMoney: Double? = null,
    val isSubmitting: Boolean = false,
    val payUrl: String? = null,
    // ---- cc-pay 收银台支付(复用校园卡隐藏WebView自动唤起) ----
    val pendingCashierUrl: String? = null,
    val pendingChannel: String? = null,
    val payWays: List<ElectricityPayWay> = emptyList(),
    val isLoadingPayWays: Boolean = false,
    // ---- 公共 ----
    val error: String? = null,
) {
  val hasPendingOrder: Boolean get() = meterInfo?.payUrl != null
}

/** 电费可选的移动端支付渠道(与校园卡一致)。 */
data class ElectricityPayWay(val id: String, val text: String, val channel: String)

/** 电费购电 ViewModel。直连 shsd.buaa.edu.cn，不依赖 shared ApiFactory。 */
class ElectricityViewModel(
    private val api: ElectricityApi = ElectricityApi(),
) : ViewModel() {
  private val _state = MutableStateFlow(ElectricityUiState())
  val state: StateFlow<ElectricityUiState> = _state.asStateFlow()

  init {
    _state.value = _state.value.copy(meterHistory = MeterNumberStore.getAll())
    loadMeterTree()
    loadPayWays()
  }

  fun clearError() {
    _state.value = _state.value.copy(error = null)
  }

  // ===== 查询 tab =====

  fun loadMeterTree() {
    _state.value = _state.value.copy(isLoadingTree = true, error = null)
    viewModelScope.launch {
      runCatching { api.fetchMeterTree() }
          .onSuccess { meters ->
            val campuses = meters.map { it.campus }.distinct().sorted()
            _state.value =
                _state.value.copy(
                    isLoadingTree = false,
                    meters = meters,
                    campuses = campuses,
                )
          }
          .onFailure { e ->
            _state.value =
                _state.value.copy(
                    isLoadingTree = false,
                    error = e.message ?: "用电查询数据加载失败",
                )
          }
    }
  }

  fun onCampusSelect(campus: String) {
    val buildings = _state.value.meters.filter { it.campus == campus }.map { it.building }.distinct().sorted()
    _state.value =
        _state.value.copy(
            selectedCampus = campus,
            buildings = buildings,
            floors = emptyList(),
            rooms = emptyList(),
            meterOptions = emptyList(),
            selectedBuilding = null,
            selectedFloor = null,
            selectedRoom = null,
            selectedMeter = null,
        )
  }

  fun onBuildingSelect(building: String) {
    val s = _state.value
    val floors =
        s.meters.filter { it.campus == s.selectedCampus && it.building == building }
            .map { it.floor }.distinct().sorted()
    _state.value =
        s.copy(
            selectedBuilding = building,
            floors = floors,
            rooms = emptyList(),
            meterOptions = emptyList(),
            selectedFloor = null,
            selectedRoom = null,
            selectedMeter = null,
        )
  }

  fun onFloorSelect(floor: String) {
    val s = _state.value
    val rooms =
        s.meters.filter {
              it.campus == s.selectedCampus && it.building == s.selectedBuilding && it.floor == floor
            }
            .map { it.room }.distinct().sorted()
    _state.value =
        s.copy(
            selectedFloor = floor,
            rooms = rooms,
            meterOptions = emptyList(),
            selectedRoom = null,
            selectedMeter = null,
        )
  }

  fun onRoomSelect(room: String) {
    val s = _state.value
    val meterOptions =
        s.meters.filter {
              it.campus == s.selectedCampus &&
                  it.building == s.selectedBuilding &&
                  it.floor == s.selectedFloor &&
                  it.room == room
            }
    _state.value = s.copy(selectedRoom = room, meterOptions = meterOptions, selectedMeter = null)
  }

  fun onMeterSelect(meter: ElectricityMeter) {
    _state.value = _state.value.copy(selectedMeter = meter)
  }

  /** 把查询到电表的 identityNo 填入缴费 tab 并自动查询。 */
  fun useSelectedMeterForPay() {
    val identityNo = _state.value.selectedMeter?.identityNo ?: return
    onMeterNumberChange(identityNo)
    queryMeter()
  }

  // ===== 缴费 tab =====

  fun onMeterNumberChange(value: String) {
    _state.value =
        _state.value.copy(
            meterNumber = value,
            meterInfo = null,
            payUrl = null,
            pendingCashierUrl = null,
            pendingChannel = null,
            power = "",
            computedPower = null,
            computedMoney = null,
        )
  }

  fun onHistorySelect(num: String) {
    onMeterNumberChange(num)
    queryMeter()
  }

  fun onHistoryRemove(num: String) {
    MeterNumberStore.remove(num)
    _state.value = _state.value.copy(meterHistory = MeterNumberStore.getAll())
  }

  /** 查询电表信息（余额、电价、倍率）。 */
  fun queryMeter() {
    val number = _state.value.meterNumber.trim()
    if (number.isBlank()) {
      _state.value = _state.value.copy(error = "请输入购电表号")
      return
    }
    _state.value = _state.value.copy(isLoadingMeter = true, error = null, meterInfo = null)
    viewModelScope.launch {
      runCatching { api.fetchMeterInfo(number) }
          .onSuccess { info ->
            MeterNumberStore.add(number)
            _state.value =
                _state.value.copy(
                    isLoadingMeter = false,
                    meterInfo = info,
                    meterHistory = MeterNumberStore.getAll(),
                )
          }
          .onFailure { e ->
            _state.value =
                _state.value.copy(isLoadingMeter = false, error = e.message ?: "查询电表失败")
          }
    }
  }

  fun onPowerChange(value: String) {
    val info = _state.value.meterInfo
    if (info == null) {
      _state.value = _state.value.copy(power = value)
      return
    }
    val pwr = value.toIntOrNull()
    if (pwr == null) {
      _state.value =
          _state.value.copy(power = value, computedPower = null, computedMoney = null)
      return
    }
    compute(pwr)
  }

  private fun compute(requestedPower: Int) {
    val info = _state.value.meterInfo ?: return
    val ct = if (info.ct > 0) info.ct else 1
    val writePower = requestedPower / ct
    val actualPower = writePower * ct
    val money = actualPower * info.price
    _state.value =
        _state.value.copy(
            power = actualPower.toString(),
            computedPower = writePower,
            computedMoney = money,
        )
  }

  /** 确认支付：先建 cc-pay 会话，再创建订单，解析收银台地址交给隐藏 WebView 自动唤起。 */
  fun submitPay(payWay: ElectricityPayWay? = null) {
    val s = _state.value
    val info = s.meterInfo ?: return
    val writePower = s.computedPower ?: s.power.toIntOrNull()
    if (writePower == null || writePower < 1) {
      _state.value = s.copy(error = "购电量必须是大于 0 的整数")
      return
    }

    _state.value = s.copy(isSubmitting = true, error = null, pendingCashierUrl = null, pendingChannel = null)
    viewModelScope.launch {
      runCatching {
        // 1. 建立 cc-pay 会话(复用校园卡同一套 CAS SSO)，避免跳出去重新登录
        ensureCcpaySession()
        // 2. 下单，拿到原始 payUrl(pass.cc-pay.cn/login?backUrl=...cashier?id=xxx)
        when (val result = api.submitPay(info.id, writePower)) {
          is ElectricityPayResult.Success -> {
            // 3. 解析真正的收银台地址
            val cashierUrl = extractCashierUrl(result.payUrl) ?: result.payUrl
            cashierUrl to (payWay?.channel ?: "wx")
          }
          is ElectricityPayResult.Failure -> throw ElectricityException(result.message)
        }
      }.onSuccess { (cashierUrl, channel) ->
        _state.value =
            _state.value.copy(
                isSubmitting = false,
                pendingCashierUrl = cashierUrl,
                pendingChannel = channel,
                error = if (cashierUrl.isBlank()) "未获取到收银台地址" else null,
            )
      }.onFailure { e ->
        _state.value =
            _state.value.copy(isSubmitting = false, error = e.message ?: "下单失败，请稍后重试")
      }
    }
  }

  /** 加载电费可选的移动支付渠道(与校园卡一致的微信/支付宝)。 */
  fun loadPayWays() {
    if (_state.value.isLoadingPayWays) return
    _state.value = _state.value.copy(isLoadingPayWays = true)
    _state.value =
        _state.value.copy(
            isLoadingPayWays = false,
            payWays =
                listOf(
                    ElectricityPayWay("wx", "微信支付", "wx"),
                    ElectricityPayWay("ali", "支付宝", "ali"),
                ),
        )
  }

  /** 清理隐藏 WebView 支付状态(支付已处理完成)。 */
  fun clearPendingPay() {
    _state.value = _state.value.copy(pendingCashierUrl = null, pendingChannel = null)
  }

  /** 继续支付未完成订单。 */
  fun continuePendingPay(payWay: ElectricityPayWay? = null) {
    val url = _state.value.meterInfo?.payUrl
    if (url == null) return
    _state.value = _state.value.copy(isSubmitting = true, error = null, pendingCashierUrl = null, pendingChannel = null)
    viewModelScope.launch {
      runCatching {
        // 建立 cc-pay 会话 + 解析收银台地址
        ensureCcpaySession()
        extractCashierUrl(url) ?: url
      }.onSuccess { cashierUrl ->
        _state.value =
            _state.value.copy(
                isSubmitting = false,
                pendingCashierUrl = cashierUrl,
                pendingChannel = payWay?.channel ?: "wx",
                error = if (cashierUrl.isBlank()) "未获取到收银台地址" else null,
            )
      }.onFailure { e ->
        _state.value =
            _state.value.copy(isSubmitting = false, error = e.message ?: "继续支付失败，请稍后重试")
      }
    }
  }

  /** 取消未完成订单。 */
  fun cancelPendingPay() {
    val info = _state.value.meterInfo ?: return
    val serial = info.serial ?: return
    viewModelScope.launch {
      runCatching { api.cancelPay(info.id, serial) }
          .onSuccess { _ ->
            _state.value = _state.value.copy(meterInfo = null)
            queryMeter()
          }
          .onFailure { e ->
            _state.value = _state.value.copy(error = e.message ?: "取消订单失败")
          }
    }
  }

  /** 支付完成 / 返回后刷新。 */
  fun dismissPayUrl() {
    _state.value = _state.value.copy(payUrl = null, pendingCashierUrl = null, pendingChannel = null)
    queryMeter()
  }

  override fun onCleared() {
    api.close()
  }
}
