package cn.edu.ubaa.ui.screens.cgyy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.CgyyApi
import cn.edu.ubaa.api.local.CgyyCaptchaImageData
import cn.edu.ubaa.api.local.decodeCgyyCaptchaImage
import cn.edu.ubaa.api.local.encryptCgyyClickWordCaptchaVerification
import cn.edu.ubaa.api.local.encryptCgyyClickWordPointJson
import cn.edu.ubaa.api.local.encryptCgyyOrderPin
import cn.edu.ubaa.api.local.ensureCcpaySession
import cn.edu.ubaa.api.storage.CgyyReservationFormStore
import cn.edu.ubaa.api.storage.StoredCgyyReservationForm
import cn.edu.ubaa.model.dto.CgyyBuddyListResponse
import cn.edu.ubaa.model.dto.CgyyClickWordCaptchaDto
import cn.edu.ubaa.model.dto.CgyyClickWordCheckResult
import cn.edu.ubaa.model.dto.CgyyDayInfoResponse
import cn.edu.ubaa.model.dto.CgyyLockCodeResponse
import cn.edu.ubaa.model.dto.CgyyOrderPayResult
import cn.edu.ubaa.model.dto.CgyyOrdersPageResponse
import cn.edu.ubaa.model.dto.CgyyPurposeTypeDto
import cn.edu.ubaa.model.dto.CgyyReservationSelectionDto
import cn.edu.ubaa.model.dto.CgyyReservationSubmitRequest
import cn.edu.ubaa.model.dto.CgyySportOrderSubmitRequest
import cn.edu.ubaa.model.dto.CgyyVenueSiteDto
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class CgyyReservationSummary(
    val siteLabel: String,
    val reservationDate: String,
    val spaceName: String,
    val slotLabels: List<String>,
)

/** 运动场点选验证码：用户按 wordList 顺序点选的显示坐标（未缩放）。 */
data class CgyySportCaptchaPoint(val x: Int, val y: Int)

data class CgyyUiState(
    val isInitialLoading: Boolean = false,
    val isDayInfoLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val isOrdersLoading: Boolean = false,
    val isLockCodeLoading: Boolean = false,
    val sites: List<CgyyVenueSiteDto> = emptyList(),
    // 运动场全量可预约站点（未按分类过滤），用于切换分类时重新过滤
    val allSites: List<CgyyVenueSiteDto> = emptyList(),
    val purposeTypes: List<CgyyPurposeTypeDto> = emptyList(),
    val dayInfo: CgyyDayInfoResponse? = null,
    val selectedCampus: String = "",
    val reserveSearchQuery: String = "",
    val selectedSiteId: Int? = null,
    val selectedDate: String = "",
    val selections: List<CgyyReservationSelectionDto> = emptyList(),
    val reservationSummary: CgyyReservationSummary? = null,
    val phone: String = "",
    val theme: String = "",
    val purposeType: Int? = null,
    val joinerNum: String = "1",
    val activityContent: String = "",
    val joiners: String = "",
    val isPhilosophySocialSciences: Boolean = false,
    val isOffSchoolJoiner: Boolean = false,
    val hasTriedSubmitReservation: Boolean = false,
    val orders: CgyyOrdersPageResponse = CgyyOrdersPageResponse(),
    val lockCode: CgyyLockCodeResponse? = null,
    val initialError: String? = null,
    val dayInfoError: String? = null,
    val ordersError: String? = null,
    val lockCodeError: String? = null,
    val actionMessage: String? = null,
    val clickWordCaptcha: CgyyClickWordCaptchaDto? = null,
    val captchaImage: CgyyCaptchaImageData? = null,
    val captchaPoints: List<CgyySportCaptchaPoint> = emptyList(),
    val captchaCheck: CgyyClickWordCheckResult? = null,
    val isCaptchaLoading: Boolean = false,
    val captchaError: String? = null,
    val orderPinClientX: Int? = null,
    val orderPinClientY: Int? = null,
    // 运动场同伴
    val buddies: CgyyBuddyListResponse = CgyyBuddyListResponse(),
    val selectedBuddyIds: Set<Int> = emptySet(),
    val isBuddiesLoading: Boolean = false,
    val buddyError: String? = null,
    val addBuddyUid: String = "",
    val isAddingBuddy: Boolean = false,
    /** 同伴添加成功计数（UI 用它判断添加成功后自动关闭弹窗）。 */
    val buddyAddTick: Int = 0,
    // 运动场支付
    val payResult: CgyyOrderPayResult? = null,
    val isPaying: Boolean = false,
    val payError: String? = null,
    // 运动场支付：cc-pay 收银台（复用校车/电费同款隐藏 WebView 自动唤起微信/支付宝）
    val payCashierUrl: String? = null,
    val payChannel: String = "wx",
    val payChannelPending: Boolean = false,
    /** cc-pay 会话是否已就绪（后台预热，就绪后才渲染收银台 WebView）。 */
    val ccpayReady: Boolean = false,
)

@OptIn(ExperimentalTime::class)
class CgyyViewModel(
    private val cgyyApi: CgyyApi = CgyyApi(),
    private val currentDateProvider: () -> String = {
      val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      val month = (now.month.ordinal + 1).toString().padStart(2, '0')
      val day = now.day.toString().padStart(2, '0')
      "${now.year}-$month-$day"
    },
    val venueLabel: String = "研讨室",
    val spaceLabel: String = "教室",
    val isSportVenue: Boolean = false,
) : ViewModel() {
  companion object {
    const val ALL_CAMPUSES = "全部"
  }

  private var initialLoadedOnce = false
  private var ordersLoadedOnce = false
  private var lockCodeLoadedOnce = false
  private var buddiesLoadedOnce = false
  private val _uiState = MutableStateFlow(createInitialState())
  val uiState: StateFlow<CgyyUiState> = _uiState.asStateFlow()

  fun ensureInitialDataLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && initialLoadedOnce) return
    loadInitialData()
  }

  fun loadInitialData() {
    initialLoadedOnce = true
    viewModelScope.launch {
      _uiState.value =
          _uiState.value.copy(
              isInitialLoading = true,
              initialError = null,
              dayInfoError = null,
          )

      val sitesResult = cgyyApi.getVenueSites()
      val purposeTypesResult = cgyyApi.getPurposeTypes()

      val sites = sitesResult.getOrNull().orEmpty().filterReservable()
      val purposeTypes = purposeTypesResult.getOrNull().orEmpty()
      val currentPurposeType = _uiState.value.purposeType
      val resolvedPurposeType =
          when {
            purposeTypes.isEmpty() -> currentPurposeType
            currentPurposeType != null && purposeTypes.any { it.key == currentPurposeType } ->
                currentPurposeType
            else -> purposeTypes.firstOrNull()?.key
          }
      // 运动场：默认选中第一个运动分类（如羽毛球），列表只显示该分类下的可预约场馆
      val visibleSites =
          if (isSportVenue && resolvedPurposeType != null) {
            sites.filter { it.sportType == resolvedPurposeType }
          } else {
            sites
          }
      val siteId = _uiState.value.selectedSiteId ?: visibleSites.firstOrNull()?.id

      _uiState.value =
          _uiState.value.copy(
              isInitialLoading = false,
              sites = visibleSites,
              allSites = sites,
              selectedCampus = _uiState.value.selectedCampus.ifBlank { ALL_CAMPUSES },
              purposeTypes = purposeTypes,
              selectedSiteId = siteId,
              purposeType = resolvedPurposeType,
              initialError =
                  sitesResult.exceptionOrNull()?.message
                      ?: purposeTypesResult.exceptionOrNull()?.message,
          )

      if (siteId != null) {
        loadDayInfo(siteId, _uiState.value.selectedDate.ifBlank { currentDateProvider() })
      }
      if (isSportVenue) {
        loadBuddies()
      }
    }
  }

  fun ensureOrdersLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && ordersLoadedOnce) return
    loadOrders()
  }

  internal fun hasOrdersLoaded(): Boolean = ordersLoadedOnce

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    initialLoadedOnce = false
    ordersLoadedOnce = false
    lockCodeLoadedOnce = false
    buddiesLoadedOnce = false
    _uiState.value = createInitialState()
  }

  // ---- 运动场同伴 ----

  fun loadBuddies(forceRefresh: Boolean = false) {
    if (!forceRefresh && buddiesLoadedOnce) return
    buddiesLoadedOnce = true
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isBuddiesLoading = true, buddyError = null)
      cgyyApi
          .getBuddies()
          .onSuccess { list ->
            _uiState.value = _uiState.value.copy(isBuddiesLoading = false, buddies = list)
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isBuddiesLoading = false,
                    buddyError = it.message ?: "同伴列表加载失败",
                )
          }
    }
  }

  fun toggleBuddy(buddyId: Int) {
    val current = _uiState.value.selectedBuddyIds
    _uiState.value =
        _uiState.value.copy(
            selectedBuddyIds = if (buddyId in current) current - buddyId else current + buddyId
        )
  }

  fun updateAddBuddyUid(value: String) {
    _uiState.value = _uiState.value.copy(addBuddyUid = value)
  }

  fun addBuddyByUid() {
    val uid = _uiState.value.addBuddyUid.trim()
    if (uid.isBlank()) return
    _uiState.value = _uiState.value.copy(isAddingBuddy = true, buddyError = null)
    viewModelScope.launch {
      cgyyApi
          .addBuddy(uid)
          .onSuccess { list ->
            _uiState.value =
                _uiState.value.copy(
                    isAddingBuddy = false,
                    buddies = list,
                    addBuddyUid = "",
                    buddyAddTick = _uiState.value.buddyAddTick + 1,
                )
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(isAddingBuddy = false, buddyError = it.message ?: "同伴添加失败")
          }
    }
  }

  fun deleteBuddy(buddyId: Int) {
    viewModelScope.launch {
      cgyyApi
          .deleteBuddy(buddyId)
          .onSuccess { list ->
            _uiState.value =
                _uiState.value.copy(
                    buddies = list,
                    selectedBuddyIds = _uiState.value.selectedBuddyIds - buddyId,
                )
          }
          .onFailure { _uiState.value = _uiState.value.copy(buddyError = it.message ?: "同伴删除失败") }
    }
  }

  // ---- 运动场支付（优先 cc-pay 收银台直接唤起微信/支付宝；无收银台退回航财通扫码） ----

  fun dismissPayResult() {
    _uiState.value = _uiState.value.copy(payResult = null, payError = null)
  }

  private fun paySportOrder(tradeNo: String) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isPaying = true, payError = null)
      cgyyApi
          .paySportOrder(tradeNo)
          .onSuccess { pay ->
            _uiState.value = _uiState.value.copy(isPaying = false)
            val cashierUrl = pay.schoolPayUrl?.takeIf { "cashier.cc-pay.cn" in it }
            if (!cashierUrl.isNullOrBlank()) {
              // cc-pay 收银台 → 立即弹渠道选择，cc-pay 会话后台预热，不阻塞用户决策
              _uiState.value =
                  _uiState.value.copy(
                      payCashierUrl = cashierUrl,
                      payChannelPending = true,
                      payResult = null,
                  )
              viewModelScope.launch {
                // 无论成功失败都标记就绪（失败时页面可能仍可工作或提示重试）
                runCatching { ensureCcpaySession() }
                _uiState.value = _uiState.value.copy(ccpayReady = true)
              }
            } else if (!pay.payCode.isNullOrBlank()) {
              // 无收银台 → 退回航财通·校园付扫码
              _uiState.value = _uiState.value.copy(payResult = pay)
            } else {
              setActionMessage("支付信息为空，请稍后在订单列表查看")
            }
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(isPaying = false, payError = it.message ?: "支付发起失败")
          }
    }
  }

  /** 用户选定支付渠道（wx / ali）后拉起 cc-pay 收银台。 */
  fun chooseVenuePayChannel(channel: String) {
    val s = _uiState.value
    if (s.payCashierUrl.isNullOrBlank()) return
    _uiState.value =
        s.copy(
            payChannel = if (channel == "ali") "ali" else "wx",
            payChannelPending = false,
        )
  }

  /** 用户取消/收银台流程结束：清理支付状态。 */
  fun clearVenuePay() {
    _uiState.value =
        _uiState.value.copy(
            payCashierUrl = null,
            payChannel = "wx",
            payChannelPending = false,
            ccpayReady = false,
        )
  }

  fun ensureLockCodeLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && lockCodeLoadedOnce) return
    loadLockCode()
  }

  fun setDefaultPhone(phone: String?) {
    if (phone.isNullOrBlank()) return
    if (_uiState.value.phone.isBlank()) {
      _uiState.value = _uiState.value.copy(phone = phone)
    }
  }

  fun setReserveCampus(campus: String) {
    val current = _uiState.value
    val campusSites =
        if (campus == ALL_CAMPUSES) current.sites
        else current.sites.filter { it.campusName == campus }
    val nextSiteId =
        current.selectedSiteId?.takeIf { selectedId -> campusSites.any { it.id == selectedId } }
            ?: campusSites.firstOrNull()?.id
    _uiState.value =
        current.copy(
            selectedCampus = campus,
            selectedSiteId = nextSiteId,
            selections = emptyList(),
            reservationSummary = null,
            actionMessage = null,
        )
    if (nextSiteId != null) {
      loadDayInfo(nextSiteId, current.selectedDate.ifBlank { currentDateProvider() })
    }
  }

  fun updateReserveSearchQuery(query: String) {
    _uiState.value = _uiState.value.copy(reserveSearchQuery = query)
  }

  fun selectSite(siteId: Int) {
    _uiState.value =
        _uiState.value.copy(
            selectedSiteId = siteId,
            selections = emptyList(),
            reservationSummary = null,
            actionMessage = null,
        )
    loadDayInfo(siteId, _uiState.value.selectedDate.ifBlank { currentDateProvider() })
  }

  fun selectDate(date: String) {
    val siteId = _uiState.value.selectedSiteId ?: return
    _uiState.value =
        _uiState.value.copy(
            selectedDate = date,
            selections = emptyList(),
            reservationSummary = null,
            actionMessage = null,
        )
    loadDayInfo(siteId, date)
  }

  fun toggleSlot(spaceId: Int, timeId: Int, venueSpaceGroupId: Int?) {
    val currentState = _uiState.value
    val tappedSelection =
        CgyyReservationSelectionDto(
            spaceId = spaceId,
            timeId = timeId,
            venueSpaceGroupId = venueSpaceGroupId,
        )
    val orderedTimeIds =
        currentState.dayInfo?.timeSlots?.mapIndexed { index, slot -> slot.id to index }?.toMap()
    val existingSelections =
        currentState.selections.sortedBy { orderedTimeIds?.get(it.timeId) ?: Int.MAX_VALUE }
    val nextSelections =
        when {
          existingSelections.any { it.spaceId == spaceId && it.timeId == timeId } ->
              existingSelections.filterNot { it.spaceId == spaceId && it.timeId == timeId }
          existingSelections.isEmpty() -> listOf(tappedSelection)
          existingSelections.any { it.spaceId != spaceId } -> listOf(tappedSelection)
          existingSelections.size == 1 &&
              areAdjacent(existingSelections.first().timeId, timeId, orderedTimeIds) ->
              listOf(existingSelections.first(), tappedSelection).sortedBy {
                orderedTimeIds?.get(it.timeId) ?: Int.MAX_VALUE
              }
          else -> listOf(tappedSelection)
        }
    val nextState = _uiState.value.copy(selections = nextSelections, actionMessage = null)
    _uiState.value = nextState.copy(reservationSummary = buildReservationSummary(nextState))
  }

  fun updatePhone(value: String) {
    _uiState.value = _uiState.value.copy(phone = value)
    // 电话记忆：非空时持久化，研讨室/场馆两侧共用同一个电话
    if (value.isNotBlank()) {
      val stored =
          CgyyReservationFormStore.get()
              ?: StoredCgyyReservationForm(
                  phone = "",
                  theme = "",
                  purposeType = null,
                  joinerNum = "1",
                  activityContent = "",
                  joiners = "",
                  isPhilosophySocialSciences = false,
                  isOffSchoolJoiner = false,
              )
      CgyyReservationFormStore.save(stored.copy(phone = value))
    }
  }

  /** 运动场专用：按项目分类（sportType 键；null = 全部场地）从全量可预约站点过滤并刷新时段。 */
  fun selectSportCategory(categoryKey: Int?) {
    if (!isSportVenue) return
    val current = _uiState.value
    val filtered =
        if (categoryKey == null) current.allSites
        else current.allSites.filter { it.sportType == categoryKey }
    val nextSiteId =
        current.selectedSiteId?.takeIf { id -> filtered.any { it.id == id } }
            ?: filtered.firstOrNull()?.id
    _uiState.value =
        current.copy(
            purposeType = categoryKey,
            sites = filtered,
            selectedSiteId = nextSiteId,
            selections = emptyList(),
            reservationSummary = null,
            actionMessage = null,
        )
    if (nextSiteId != null) {
      loadDayInfo(nextSiteId, current.selectedDate.ifBlank { currentDateProvider() })
    }
  }

  fun updateTheme(value: String) {
    _uiState.value = _uiState.value.copy(theme = value)
  }

  fun updatePurposeType(value: Int) {
    _uiState.value = _uiState.value.copy(purposeType = value)
  }

  fun updateJoinerNum(value: String) {
    _uiState.value = _uiState.value.copy(joinerNum = value)
  }

  fun updateActivityContent(value: String) {
    _uiState.value = _uiState.value.copy(activityContent = value)
  }

  fun updateJoiners(value: String) {
    _uiState.value = _uiState.value.copy(joiners = value)
  }

  fun setPhilosophySocialSciences(enabled: Boolean) {
    _uiState.value = _uiState.value.copy(isPhilosophySocialSciences = enabled)
  }

  fun setOffSchoolJoiner(enabled: Boolean) {
    _uiState.value = _uiState.value.copy(isOffSchoolJoiner = enabled)
  }

  fun canAdvanceToReservationForm(): Boolean = _uiState.value.reservationSummary != null

  fun selectionHint(): String =
      when {
        _uiState.value.selectedSiteId == null -> "请先选择$venueLabel"
        _uiState.value.selectedDate.isBlank() -> "请先选择预约日期"
        _uiState.value.selections.isEmpty() -> "请至少选择一个可预约时段"
        else -> "已完成选择，可以进入下一步"
      }

  fun submitReservation(
      onSuccess: (() -> Unit)? = null,
      clientX: Int? = null,
      clientY: Int? = null,
  ) {
    val current = _uiState.value
    val siteId = current.selectedSiteId ?: return setActionMessage("请先选择场地")
    _uiState.value = current.copy(hasTriedSubmitReservation = true, actionMessage = null)
    if (current.selections.isEmpty()) return setActionMessage("请至少选择一个时段")
    if (current.phone.isBlank()) return setActionMessage("请填写联系电话")

    if (isSportVenue) {
      // 运动场下单：记录提交按钮点击坐标（orderPin 明文 clientX,clientY），先出点选验证码
      _uiState.value =
          _uiState.value.copy(
              orderPinClientX = clientX,
              orderPinClientY = clientY,
          )
      startSportCheckout()
      return
    }

    // 运动场订场：无需活动主题/内容/参与人数等审批字段
    val purposeType = current.purposeType
    if (!isSportVenue) {
      if (purposeType == null) return setActionMessage("请选择活动类型")
      val joinerNum = current.joinerNum.toIntOrNull()
      if (joinerNum == null || joinerNum <= 0) return setActionMessage("参与人数必须大于 0")
      if (current.theme.isBlank()) return setActionMessage("请填写活动主题")
      if (current.activityContent.isBlank()) return setActionMessage("请填写活动内容")
      if (current.joiners.isBlank()) return setActionMessage("请填写参与人说明")
    }

    viewModelScope.launch {
      _uiState.value =
          _uiState.value.copy(
              isSubmitting = true,
              actionMessage = null,
              hasTriedSubmitReservation = true,
          )
      val result =
          cgyyApi.submitReservation(
              CgyyReservationSubmitRequest(
                  venueSiteId = siteId,
                  reservationDate = current.selectedDate,
                  selections = current.selections,
                  phone = current.phone,
                  theme = current.theme,
                  purposeType = purposeType ?: 0,
                  joinerNum = current.joinerNum.toIntOrNull() ?: 1,
                  activityContent = current.activityContent,
                  joiners = current.joiners,
                  isPhilosophySocialSciences = current.isPhilosophySocialSciences,
                  isOffSchoolJoiner = current.isOffSchoolJoiner,
              )
          )
      result
          .onSuccess {
            val storedForm =
                StoredCgyyReservationForm(
                    phone = current.phone,
                    theme = current.theme,
                    purposeType = purposeType,
                    joinerNum = current.joinerNum,
                    activityContent = current.activityContent,
                    joiners = current.joiners,
                    isPhilosophySocialSciences = current.isPhilosophySocialSciences,
                    isOffSchoolJoiner = current.isOffSchoolJoiner,
                )
            CgyyReservationFormStore.save(storedForm)
            _uiState.value =
                _uiState.value.copy(
                    isSubmitting = false,
                    selections = emptyList(),
                    reservationSummary = null,
                    phone = storedForm.phone,
                    theme = storedForm.theme,
                    purposeType = storedForm.purposeType,
                    joinerNum = storedForm.joinerNum,
                    activityContent = storedForm.activityContent,
                    joiners = storedForm.joiners,
                    isPhilosophySocialSciences = storedForm.isPhilosophySocialSciences,
                    isOffSchoolJoiner = storedForm.isOffSchoolJoiner,
                    hasTriedSubmitReservation = false,
                    actionMessage = it.message,
                )
            loadDayInfo(siteId, current.selectedDate)
            loadOrders()
            onSuccess?.invoke()
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isSubmitting = false,
                    actionMessage = it.message ?: "预约失败",
                )
          }
    }
  }

  fun loadOrders(page: Int = 0, size: Int = 20) {
    ordersLoadedOnce = true
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isOrdersLoading = true, ordersError = null)
      cgyyApi
          .getMyOrders(page, size)
          .onSuccess {
            _uiState.value =
                _uiState.value.copy(
                    isOrdersLoading = false,
                    orders = it,
                    ordersError = null,
                )
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isOrdersLoading = false,
                    ordersError = it.message ?: "加载预约列表失败",
                )
          }
    }
  }

  fun cancelOrder(orderId: Int) {
    if (isSportVenue) {
      val tradeNo = _uiState.value.orders.content.firstOrNull { it.id == orderId }?.tradeNo
      if (tradeNo.isNullOrBlank()) {
        _uiState.value = _uiState.value.copy(actionMessage = "该订单缺少支付流水号，无法取消")
        return
      }
      cancelSportOrder(tradeNo)
      return
    }
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(actionMessage = null)
      cgyyApi
          .cancelOrder(orderId)
          .onSuccess {
            _uiState.value = _uiState.value.copy(actionMessage = it.message)
            loadOrders(_uiState.value.orders.number, _uiState.value.orders.size)
          }
          .onFailure {
            _uiState.value = _uiState.value.copy(actionMessage = it.message ?: "取消预约失败")
          }
    }
  }

  /** 运动场订单取消：POST /api/venue/finances/order/cancel（venueTradeNo）。 */
  private fun cancelSportOrder(tradeNo: String) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(actionMessage = null)
      cgyyApi
          .cancelSportOrder(tradeNo)
          .onSuccess {
            _uiState.value = _uiState.value.copy(actionMessage = it.message ?: "已取消预约")
            loadOrders(_uiState.value.orders.number, _uiState.value.orders.size)
          }
          .onFailure {
            _uiState.value = _uiState.value.copy(actionMessage = it.message ?: "取消预约失败")
          }
    }
  }

  fun loadLockCode() {
    if (_uiState.value.isLockCodeLoading) return
    lockCodeLoadedOnce = true
    _uiState.value = _uiState.value.copy(isLockCodeLoading = true, lockCodeError = null)
    viewModelScope.launch {
      cgyyApi
          .getLockCode()
          .onSuccess {
            _uiState.value =
                _uiState.value.copy(
                    isLockCodeLoading = false,
                    lockCode = it,
                    lockCodeError = null,
                )
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isLockCodeLoading = false,
                    lockCodeError = it.message ?: "加载门锁密码失败",
                )
          }
    }
  }

  fun clearActionMessage() {
    _uiState.value = _uiState.value.copy(actionMessage = null)
  }

  fun refreshReserveData() {
    val current = _uiState.value
    val selectedSiteId = current.selectedSiteId
    if (selectedSiteId == null) {
      loadInitialData()
      return
    }
    loadDayInfo(selectedSiteId, current.selectedDate.ifBlank { currentDateProvider() })
  }

  private fun loadDayInfo(siteId: Int, date: String) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isDayInfoLoading = true, dayInfoError = null)
      cgyyApi
          .getDayInfo(siteId, date)
          .onSuccess { response ->
            val filteredSelections =
                _uiState.value.selections.filter { selection ->
                  response.spaces.any { space ->
                    space.spaceId == selection.spaceId &&
                        space.slots.any { it.timeId == selection.timeId && it.isReservable }
                  }
                }
            val nextState =
                _uiState.value.copy(
                    isDayInfoLoading = false,
                    dayInfo = response,
                    selectedDate = response.reservationDate,
                    selections = filteredSelections,
                )
            val resolvedPhone =
                if (isSportVenue && nextState.phone.isBlank()) {
                  response.orderParamViewPhone.orEmpty()
                } else {
                  nextState.phone
                }
            _uiState.value =
                nextState.copy(
                    phone = resolvedPhone,
                    reservationSummary = buildReservationSummary(nextState),
                )
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isDayInfoLoading = false,
                    dayInfoError = it.message ?: "加载可预约信息失败",
                )
          }
    }
  }

  /** 运动场下单第一步：加载点选验证码（clickWord）。 */
  fun startSportCheckout() {
    if (!isSportVenue) return
    _uiState.value =
        _uiState.value.copy(isCaptchaLoading = true, captchaError = null, actionMessage = null)
    viewModelScope.launch {
      cgyyApi
          .getClickWordCaptcha()
          .onSuccess { captcha ->
            val image =
                runCatching { decodeCgyyCaptchaImage(captcha.originalImageBase64) }.getOrNull()
            _uiState.value =
                _uiState.value.copy(
                    isCaptchaLoading = false,
                    clickWordCaptcha = captcha,
                    captchaImage = image,
                    captchaPoints = emptyList(),
                    captchaCheck = null,
                    captchaError = null,
                )
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isCaptchaLoading = false,
                    captchaError = it.message ?: "验证码加载失败",
                    actionMessage = it.message ?: "验证码加载失败",
                )
          }
    }
  }

  /** 刷新验证码（重新获取一张）。 */
  fun refreshClickWordCaptcha() {
    _uiState.value =
        _uiState.value.copy(
            clickWordCaptcha = null,
            captchaImage = null,
            captchaPoints = emptyList(),
            captchaCheck = null,
        )
    startSportCheckout()
  }

  /** 关闭验证码面板（取消下单）。 */
  fun dismissClickWordCaptcha() {
    _uiState.value =
        _uiState.value.copy(
            clickWordCaptcha = null,
            captchaImage = null,
            captchaPoints = emptyList(),
            captchaCheck = null,
            isCaptchaLoading = false,
            captchaError = null,
            orderPinClientX = null,
            orderPinClientY = null,
        )
  }

  /** 用户点击验证码图片：记录显示坐标；点满 wordList 后自动校验。 */
  fun onCaptchaTap(x: Int, y: Int, displayWidth: Int, displayHeight: Int) {
    val captcha = _uiState.value.clickWordCaptcha ?: return
    if (captcha.wordList.isEmpty()) return
    if (_uiState.value.captchaPoints.size >= captcha.wordList.size) return
    val nextPoints = _uiState.value.captchaPoints + CgyySportCaptchaPoint(x, y)
    _uiState.value =
        _uiState.value.copy(captchaPoints = nextPoints, captchaError = null, actionMessage = null)
    if (nextPoints.size >= captcha.wordList.size) {
      verifyClickWordCaptcha(displayWidth, displayHeight)
    }
  }

  /** 校验点选：显示坐标归一化到 310×155 空间（网页 pointTransfrom 同款）→ AES-ECB 加密 → captcha/check。 */
  fun verifyClickWordCaptcha(displayWidth: Int, displayHeight: Int) {
    val captcha = _uiState.value.clickWordCaptcha ?: return
    if (displayWidth <= 0 || displayHeight <= 0) return
    val pointJsonData =
        _uiState.value.captchaPoints.joinToString(",", "[", "]") { p ->
          val ox = kotlin.math.round(310.0 * p.x / displayWidth).toInt()
          val oy = kotlin.math.round(155.0 * p.y / displayHeight).toInt()
          "{\"x\":$ox,\"y\":$oy}"
        }
    val pointJson =
        runCatching { encryptCgyyClickWordPointJson(pointJsonData, captcha.secretKey) }
            .getOrElse {
              _uiState.value = _uiState.value.copy(captchaError = "验证码坐标加密失败")
              return
            }
    _uiState.value = _uiState.value.copy(isCaptchaLoading = true, captchaError = null)
    viewModelScope.launch {
      cgyyApi
          .checkClickWordCaptcha(pointJson, captcha.token)
          .onSuccess { check ->
            // captchaVerification 服务器不返回，需按网页同款自算：AES-ECB(token+"---"+pointJsonData, secretKey)
            val verification =
                runCatching {
                      encryptCgyyClickWordCaptchaVerification(
                          captcha.token,
                          pointJsonData,
                          captcha.secretKey,
                      )
                    }
                    .getOrNull()
            val finalCheck =
                CgyyClickWordCheckResult(
                    captchaVerification = verification ?: check.captchaVerification,
                    captchaToken = check.captchaToken,
                )
            _uiState.value =
                _uiState.value.copy(
                    isCaptchaLoading = false,
                    captchaCheck = finalCheck,
                    captchaError = null,
                )
            performSportOrderSubmit()
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isCaptchaLoading = false,
                    captchaError = it.message ?: "验证码校验失败",
                    captchaPoints = emptyList(),
                )
          }
    }
  }

  /** 验证通过后提交场馆订单（网页同款字段 + orderPin）。 */
  fun performSportOrderSubmit() {
    val current = _uiState.value
    val siteId = current.selectedSiteId ?: return
    val dayInfo = current.dayInfo ?: return setActionMessage("可预约信息缺失，请重试")
    val captcha = current.clickWordCaptcha ?: return
    val check = current.captchaCheck ?: return
    val reservationOrderJson =
        current.selections.joinToString(",", "[", "]") { sel ->
          buildString {
            append("{\"spaceId\":\"${sel.spaceId}\",\"timeId\":\"${sel.timeId}\"")
            sel.venueSpaceGroupId?.let { append(",\"venueSpaceGroupId\":\"$it\"") }
            append("}")
          }
        }
    val orderPrice =
        current.selections.sumOf { sel ->
          dayInfo.spaces
              .firstOrNull { it.spaceId == sel.spaceId }
              ?.slots
              ?.firstOrNull { it.timeId == sel.timeId }
              ?.orderFee ?: 0.0
        }
    val pinX = current.orderPinClientX ?: 0
    val pinY = current.orderPinClientY ?: 0
    val orderPin =
        runCatching { encryptCgyyOrderPin(pinX, pinY) }
            .getOrElse {
              _uiState.value = _uiState.value.copy(actionMessage = "orderPin 生成失败")
              return
            }
    val request =
        CgyySportOrderSubmitRequest(
            venueSiteId = siteId,
            reservationDate = current.selectedDate,
            weekStartDate = venueMondayOfWeek(current.selectedDate),
            reservationOrderJson = reservationOrderJson,
            orderPrice = orderPrice,
            orderPin = orderPin,
            phone = current.phone,
            buddyIds = current.selectedBuddyIds.joinToString(","),
            captchaVerification = check.captchaVerification,
            captchaToken = check.captchaToken,
        )
    _uiState.value = _uiState.value.copy(isSubmitting = true, actionMessage = null)
    viewModelScope.launch {
      cgyyApi
          .submitSportOrder(request)
          .onSuccess { result ->
            _uiState.value =
                _uiState.value.copy(
                    isSubmitting = false,
                    selections = emptyList(),
                    reservationSummary = null,
                    clickWordCaptcha = null,
                    captchaImage = null,
                    captchaPoints = emptyList(),
                    captchaCheck = null,
                    hasTriedSubmitReservation = false,
                    orderPinClientX = null,
                    orderPinClientY = null,
                    actionMessage = "预约成功（订单号 ${result.tradeNo ?: "-"}）",
                )
            loadDayInfo(siteId, current.selectedDate)
            loadOrders()
            val tradeNo = result.tradeNo
            if (tradeNo != null) {
              paySportOrder(tradeNo)
            }
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isSubmitting = false,
                    actionMessage = it.message ?: "预约失败",
                )
          }
    }
  }

  private fun setActionMessage(message: String) {
    _uiState.value = _uiState.value.copy(actionMessage = message)
  }

  private fun createInitialState(): CgyyUiState {
    val storedForm = CgyyReservationFormStore.get()
    return CgyyUiState(
        phone = storedForm?.phone.orEmpty(),
        theme = storedForm?.theme.orEmpty(),
        purposeType = storedForm?.purposeType,
        joinerNum = storedForm?.joinerNum ?: "1",
        activityContent = storedForm?.activityContent.orEmpty(),
        joiners = storedForm?.joiners.orEmpty(),
        isPhilosophySocialSciences = storedForm?.isPhilosophySocialSciences ?: false,
        isOffSchoolJoiner = storedForm?.isOffSchoolJoiner ?: false,
    )
  }

  private fun areAdjacent(
      firstTimeId: Int,
      secondTimeId: Int,
      orderedTimeIds: Map<Int, Int>?,
  ): Boolean {
    val firstIndex = orderedTimeIds?.get(firstTimeId) ?: return false
    val secondIndex = orderedTimeIds[secondTimeId] ?: return false
    return abs(firstIndex - secondIndex) == 1
  }

  private fun buildReservationSummary(state: CgyyUiState): CgyyReservationSummary? {
    val selectedSiteId = state.selectedSiteId ?: return null
    if (state.selectedDate.isBlank() || state.selections.isEmpty()) return null
    val selectedSpaceId = state.selections.firstOrNull()?.spaceId ?: return null
    if (state.selections.any { it.spaceId != selectedSpaceId }) return null
    val site = state.sites.firstOrNull { it.id == selectedSiteId } ?: return null
    val dayInfo = state.dayInfo ?: return null
    val space = dayInfo.spaces.firstOrNull { it.spaceId == selectedSpaceId } ?: return null
    val orderedTimeIds = dayInfo.timeSlots.mapIndexed { index, slot -> slot.id to index }.toMap()
    val selectedTimeIds = state.selections.map { it.timeId }.toSet()
    val slotLabels =
        space.slots
            .filter { it.timeId in selectedTimeIds }
            .sortedBy { orderedTimeIds[it.timeId] ?: Int.MAX_VALUE }
            .mapNotNull { slot ->
              dayInfo.timeSlots.firstOrNull { it.id == slot.timeId }?.label
                  ?: slot.startDate?.substringAfter(" ")?.let { start ->
                    slot.endDate?.substringAfter(" ")?.let { end -> "$start-$end" }
                  }
            }
    if (slotLabels.isEmpty()) return null
    return CgyyReservationSummary(
        siteLabel =
            listOf(site.venueName, site.siteName).filter { it.isNotBlank() }.joinToString(" "),
        reservationDate = state.selectedDate,
        spaceName = space.spaceName,
        slotLabels = slotLabels,
    )
  }
}

/** 运动场下单 weekStartDate = 该日期所在周的周一（yyyy-MM-dd）。 */
private fun venueMondayOfWeek(date: String): String =
    runCatching {
          val d = LocalDate.parse(date)
          d.minus(d.dayOfWeek.ordinal - 1, DateTimeUnit.DAY).toString()
        }
        .getOrDefault(date)

/** 仅保留支持预约的场馆（isSupportReservation 为 null 或 true；丢弃明确 false 的）。 */
private fun List<CgyyVenueSiteDto>.filterReservable(): List<CgyyVenueSiteDto> = filter {
  it.isSupportReservation != false
}
