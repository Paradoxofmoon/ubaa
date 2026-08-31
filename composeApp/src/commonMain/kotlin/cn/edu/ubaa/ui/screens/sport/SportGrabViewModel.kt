package cn.edu.ubaa.ui.screens.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.CgyyApi
import cn.edu.ubaa.api.local.CgyyCaptchaImageData
import cn.edu.ubaa.api.local.decodeCgyyCaptchaImage
import cn.edu.ubaa.api.local.encryptCgyyClickWordCaptchaVerification
import cn.edu.ubaa.api.local.encryptCgyyClickWordPointJson
import cn.edu.ubaa.api.local.encryptCgyyOrderPin
import cn.edu.ubaa.api.local.sportVenueDirectApi
import cn.edu.ubaa.api.storage.CgyyReservationFormStore
import cn.edu.ubaa.api.storage.PreSelectionStore
import cn.edu.ubaa.api.storage.PriorityDraft
import cn.edu.ubaa.api.storage.PriorityOption
import cn.edu.ubaa.model.dto.CgyyClickWordCaptchaDto
import cn.edu.ubaa.model.dto.CgyyClickWordCheckResult
import cn.edu.ubaa.model.dto.CgyyDayInfoResponse
import cn.edu.ubaa.model.dto.CgyySportOrderSubmitRequest
import cn.edu.ubaa.model.dto.CgyyVenueSiteDto
import cn.edu.ubaa.ui.screens.cgyy.CgyySportCaptchaPoint
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** 抢场模式中单个意向的实时状态（index = 优先级排名）。 */
data class GrabOptionStatus(
    val index: Int,
    val spaceId: Int,
    val timeLabel: String,
    val displayLabel: String,
    /** 抢场日解析出的真实 timeId；null = 未开放/解析失败。 */
    val resolvedTimeId: Int? = null,
    val isReservable: Boolean = false,
    val isTaken: Boolean = false,
    val isUnavailable: Boolean = false,
    val isActive: Boolean = false,
    /** 连续失败次数（非网络错误），>=2 视为该意向不可用。 */
    val failCount: Int = 0,
)

data class SportGrabUiState(
    val targetDate: String = "",
    // ---- 预选模式 ----
    val drafts: List<PriorityDraft> = emptyList(),
    val referenceSiteId: Int = 0,
    val referenceDate: String = "",
    val referenceDayInfo: CgyyDayInfoResponse? = null,
    val isReferenceLoading: Boolean = false,
    val referenceError: String? = null,
    val editingDraft: PriorityDraft? = null,
    // ---- 抢场模式 ----
    val grabActive: Boolean = false,
    val grabDraft: PriorityDraft? = null,
    val grabDayInfo: CgyyDayInfoResponse? = null,
    val optionStatuses: List<GrabOptionStatus> = emptyList(),
    val windowOpened: Boolean = false,
    val activeOptionIndex: Int = -1,
    // ---- 验证码 ----
    val captcha: CgyyClickWordCaptchaDto? = null,
    val captchaImage: CgyyCaptchaImageData? = null,
    val captchaPoints: List<CgyySportCaptchaPoint> = emptyList(),
    val captchaCheck: CgyyClickWordCheckResult? = null,
    val isCaptchaLoading: Boolean = false,
    val captchaError: String? = null,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    /** 抢到的订单号（非空 = 成功结束）。 */
    val result: String? = null,
    /** 「开始抢场」按钮点击坐标（orderPin 数据源，自动提交复用）。 */
    val pinClientX: Int = 0,
    val pinClientY: Int = 0,
)

/** 抢场提交失败分类：决定降级/重试/停止。 */
internal enum class SubmitFailureKind {
  TAKEN,
  CAPTCHA_ERROR,
  TRANSIENT,
  UNKNOWN,
}

/** 智能抢场：提前保存优先意向 → 开抢日自动检测/锁定/提交/失败降级。 */
class SportGrabViewModel(
    private val cgyyApi: CgyyApi = sportVenueDirectApi(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(SportGrabUiState())
  val uiState: StateFlow<SportGrabUiState> = _uiState.asStateFlow()

  private var monitorJob: Job? = null

  /** 本次抢场已拉取验证码次数（自动 + 手动刷新），超上限即停止，防止触发服务端验证码风控。 */
  private var captchaPullCount = 0

  init {
    loadDrafts()
  }

  // ===================== 预选：草稿管理 =====================

  fun loadDrafts() {
    _uiState.update { it.copy(drafts = PreSelectionStore.loadAll()) }
  }

  /** 进入预选模式：目标日 = 最后一个可约日 + 1（第 4 天）；参考网格 = 当前可约日。 */
  fun openPreSelect(site: CgyyVenueSiteDto, referenceDate: String, availableDates: List<String>) {
    val target = addDays(availableDates.lastOrNull() ?: referenceDate, 1)
    _uiState.update {
      it.copy(
          targetDate = target,
          grabActive = false,
          grabDraft = null,
          editingDraft = null,
          referenceSiteId = site.id,
          referenceDate = referenceDate,
          referenceDayInfo = null,
          referenceError = null,
          result = null,
          message = null,
      )
    }
    loadReference(site.id, referenceDate)
  }

  fun retryLoadReference() {
    val s = _uiState.value
    if (s.referenceSiteId > 0 && s.referenceDate.isNotBlank()) {
      loadReference(s.referenceSiteId, s.referenceDate)
    }
  }

  private fun loadReference(siteId: Int, date: String) {
    _uiState.update { it.copy(isReferenceLoading = true, referenceError = null) }
    viewModelScope.launch {
      cgyyApi
          .getDayInfo(siteId, date)
          .onSuccess { info ->
            _uiState.update { it.copy(isReferenceLoading = false, referenceDayInfo = info) }
          }
          .onFailure { e ->
            _uiState.update {
              it.copy(isReferenceLoading = false, referenceError = e.message ?: "参考网格加载失败")
            }
          }
    }
  }

  /**
   * 预选编辑器用的网格：把参考日结构"抹平"成全部可抢。
   *
   * 目标日（第 4 天）结构未开放；今天已满的时段在目标日会重新放名额， 因此预选时不能按今天的预约状态过滤——全部时段都应可作为意向点选。
   */
  fun buildPreselectGrid(): CgyyDayInfoResponse? {
    val ref = _uiState.value.referenceDayInfo ?: return null
    return ref.copy(
        spaces =
            ref.spaces.map { space ->
              space.copy(
                  slots =
                      space.slots.map { slot ->
                        slot.copy(
                            reservationStatus = 1,
                            isReservable = true,
                            tradeNo = null,
                            orderId = null,
                            takeUp = false,
                        )
                      },
              )
            },
    )
  }

  /** 新建草稿（编辑模式）。 */
  fun newDraft(site: CgyyVenueSiteDto) {
    val target = _uiState.value.targetDate
    val stored = CgyyReservationFormStore.get()
    _uiState.update {
      it.copy(
          editingDraft =
              PriorityDraft(
                  id = "draft-${Clock.System.now().toEpochMilliseconds()}",
                  title = "${site.siteName} $target",
                  date = target,
                  venueSiteId = site.id,
                  venueSiteName = site.siteName,
                  phone = stored?.phone ?: "",
                  options = emptyList(),
                  createdAt = 0L,
              ),
          grabActive = false,
      )
    }
  }

  fun editDraft(id: String) {
    val draft = PreSelectionStore.get(id)
    if (draft != null) {
      _uiState.update { it.copy(editingDraft = draft, grabActive = false) }
    }
  }

  fun deleteDraft(id: String) {
    PreSelectionStore.delete(id)
    loadDrafts()
  }

  fun clearEditing() {
    _uiState.update { it.copy(editingDraft = null) }
  }

  /** 切换一个意向（按 场地+时段label 判重）。 */
  fun toggleOption(spaceId: Int, spaceLabel: String, timeLabel: String) {
    val editing = _uiState.value.editingDraft ?: return
    if (timeLabel.isBlank()) return
    val existing = editing.options
    val display = "$spaceLabel $timeLabel"
    val has = existing.any { it.spaceId == spaceId && it.timeLabel == timeLabel }
    val next =
        if (has) existing.filterNot { it.spaceId == spaceId && it.timeLabel == timeLabel }
        else existing + PriorityOption(spaceId, spaceLabel, timeLabel, display)
    _uiState.update { it.copy(editingDraft = editing.copy(options = next)) }
  }

  fun clearOptions() {
    val editing = _uiState.value.editingDraft ?: return
    _uiState.update { it.copy(editingDraft = editing.copy(options = emptyList())) }
  }

  /** 把第 from 个意向移到 to 位置（上移/下移）。 */
  fun moveOption(from: Int, to: Int) {
    val editing = _uiState.value.editingDraft ?: return
    val list = editing.options.toMutableList()
    if (from < 0 || from >= list.size || to < 0 || to >= list.size) return
    val item = list.removeAt(from)
    list.add(to, item)
    _uiState.update { it.copy(editingDraft = editing.copy(options = list)) }
  }

  fun updateDraftPhone(phone: String) {
    val editing = _uiState.value.editingDraft ?: return
    _uiState.update { it.copy(editingDraft = editing.copy(phone = phone)) }
  }

  fun saveDraft() {
    val editing = _uiState.value.editingDraft ?: return
    if (editing.options.isEmpty()) {
      _uiState.update { it.copy(message = "请先添加至少一个意向（场地+时段）") }
      return
    }
    val saved =
        editing.copy(
            createdAt =
                if (editing.createdAt == 0L) Clock.System.now().toEpochMilliseconds()
                else editing.createdAt,
        )
    PreSelectionStore.upsert(saved)
    loadDrafts()
    _uiState.update { it.copy(editingDraft = null, message = "已保存预选草稿「${saved.title}」") }
  }

  // ===================== 抢场引擎 =====================

  /** 开始抢场：pinX/pinY = 「开始抢场」按钮的点击坐标（orderPin 数据源）。 */
  fun startGrab(draft: PriorityDraft, pinX: Int, pinY: Int) {
    val statuses =
        draft.options.mapIndexed { i, o ->
          GrabOptionStatus(
              index = i,
              spaceId = o.spaceId,
              timeLabel = o.timeLabel,
              displayLabel = o.displayLabel,
          )
        }
    _uiState.update {
      it.copy(
          grabActive = true,
          grabDraft = draft,
          editingDraft = null,
          optionStatuses = statuses,
          windowOpened = false,
          grabDayInfo = null,
          activeOptionIndex = -1,
          captcha = null,
          captchaImage = null,
          captchaPoints = emptyList(),
          captchaCheck = null,
          isCaptchaLoading = false,
          isSubmitting = false,
          result = null,
          pinClientX = pinX,
          pinClientY = pinY,
          message = "抢场准备中：检测「${draft.date}」开抢…",
      )
    }
    captchaPullCount = 0
    startMonitor()
  }

  fun stopGrab() {
    monitorJob?.cancel()
    _uiState.update { it.copy(grabActive = false, message = "已停止抢场") }
  }

  /** 返回预选列表（退出抢场界面到列表）。 */
  fun exitGrab() {
    monitorJob?.cancel()
    _uiState.update {
      it.copy(
          grabActive = false,
          grabDraft = null,
          editingDraft = null,
          result = null,
          message = null,
      )
    }
  }

  /**
   * 监控主循环：
   * - 未开抢：慢轮询 dayInfo(X)（15~30s+抖动）检测窗口开放。
   * - 已开抢：3s±30% 辅助刷新各意向可抢性；仅在无进行中验证码/提交时自动锁定下一个可用意向。
   * - 主信号 = 服务器提交结果（handleSubmitFailure 里降级），轮询仅辅助。
   */
  private fun startMonitor() {
    monitorJob?.cancel()
    monitorJob =
        viewModelScope.launch {
          val started = Clock.System.now()
          while (true) {
            val s = _uiState.value
            if (!s.grabActive || s.result != null) break
            val draft = s.grabDraft ?: break
            if ((Clock.System.now() - started).inWholeMinutes >= GRAB_TIMEOUT_MINUTES) {
              _uiState.update { it.copy(message = "抢场超时（${GRAB_TIMEOUT_MINUTES} 分钟），已自动停止") }
              break
            }
            if (!s.windowOpened) {
              // 窗口检测：慢轮询
              val info =
                  runCatching { cgyyApi.getDayInfo(draft.venueSiteId, draft.date).getOrThrow() }
                      .getOrNull()
              if (info != null && info.spaces.isNotEmpty()) {
                _uiState.update {
                  it.copy(windowOpened = true, grabDayInfo = info, message = "已开抢！自动锁定最高优先级可用意向…")
                }
                resolveAndPick(info)
              } else {
                delay(randomDelay(15000L, 30000L))
              }
            } else {
              // 辅助轮询：刷新状态；仅在空闲时自动锁定
              if (
                  !s.isCaptchaLoading &&
                      !s.isSubmitting &&
                      s.captcha == null &&
                      s.activeOptionIndex < 0
              ) {
                val info =
                    runCatching { cgyyApi.getDayInfo(draft.venueSiteId, draft.date).getOrThrow() }
                        .getOrNull()
                if (info != null) {
                  _uiState.update { it.copy(grabDayInfo = info) }
                  resolveAndPick(info)
                }
              }
              delay(randomDelay(2000L, 4000L))
            }
          }
        }
  }

  /** 用最新 dayInfo 解析各意向（spaceId+时间label → 真实 timeId），并自动锁定最高优先级可用项。 */
  private fun resolveAndPick(info: CgyyDayInfoResponse) {
    val draft = _uiState.value.grabDraft ?: return
    val prev = _uiState.value.optionStatuses.associateBy { it.index }
    val statuses = buildGrabStatuses(info, draft.options, prev, _uiState.value.activeOptionIndex)
    _uiState.update { it.copy(optionStatuses = statuses) }

    val s = _uiState.value
    if (s.activeOptionIndex < 0 && !s.isCaptchaLoading && !s.isSubmitting && s.captcha == null) {
      val candidate =
          statuses.firstOrNull {
            it.isReservable && !it.isTaken && !it.isUnavailable && it.failCount < 2
          }
      if (candidate != null) {
        _uiState.update {
          it.copy(
              activeOptionIndex = candidate.index,
              optionStatuses =
                  _uiState.value.optionStatuses.map { st ->
                    st.copy(isActive = st.index == candidate.index)
                  },
          )
        }
        fetchCaptcha()
      } else if (statuses.all { it.isTaken || it.isUnavailable || it.failCount >= 2 }) {
        _uiState.update { it.copy(message = "所有意向均不可用或已被抢，已停止", grabActive = false) }
        monitorJob?.cancel()
      }
    }
  }

  /** 拉取新验证码（仅在需要时调用）。 */
  private fun fetchCaptcha() {
    if (captchaPullCount >= MAX_CAPTCHA_PULLS) {
      monitorJob?.cancel()
      _uiState.update {
        it.copy(
            grabActive = false,
            activeOptionIndex = -1,
            captcha = null,
            captchaImage = null,
            message = "验证码尝试次数过多（已达 ${MAX_CAPTCHA_PULLS} 次），已停止抢场防止触发风控",
        )
      }
      return
    }
    captchaPullCount++
    _uiState.update {
      it.copy(
          isCaptchaLoading = true,
          captchaError = null,
          captcha = null,
          captchaImage = null,
          captchaPoints = emptyList(),
          captchaCheck = null,
      )
    }
    viewModelScope.launch {
      cgyyApi
          .getClickWordCaptcha()
          .onSuccess { c ->
            _uiState.update {
              it.copy(
                  isCaptchaLoading = false,
                  captcha = c,
                  captchaImage =
                      runCatching { decodeCgyyCaptchaImage(c.originalImageBase64) }.getOrNull(),
                  captchaPoints = emptyList(),
                  captchaError = null,
              )
            }
          }
          .onFailure { e ->
            _uiState.update {
              it.copy(isCaptchaLoading = false, captchaError = e.message ?: "验证码加载失败")
            }
          }
    }
  }

  fun refreshCaptcha() {
    fetchCaptcha()
  }

  fun dismissCaptcha() {
    _uiState.update {
      it.copy(
          captcha = null,
          captchaImage = null,
          captchaPoints = emptyList(),
          captchaCheck = null,
          isCaptchaLoading = false,
          captchaError = null,
          activeOptionIndex = -1,
      )
    }
  }

  /** 用户点选验证码：按 wordList 顺序累计；点满自动校验。 */
  fun onCaptchaTap(x: Int, y: Int, displayWidth: Int, displayHeight: Int) {
    val s = _uiState.value
    val captcha = s.captcha ?: return
    if (captcha.wordList.isEmpty()) return
    if (s.captchaPoints.size >= captcha.wordList.size) return
    val next = s.captchaPoints + CgyySportCaptchaPoint(x, y)
    _uiState.update { it.copy(captchaPoints = next, captchaError = null) }
    if (next.size >= captcha.wordList.size) {
      verifyCaptcha(displayWidth, displayHeight)
    }
  }

  /** 校验点选（310×155 归一化 + AES-ECB + check），通过后自动提交当前锁定意向。 */
  fun verifyCaptcha(displayWidth: Int, displayHeight: Int) {
    val s = _uiState.value
    val captcha = s.captcha ?: return
    if (displayWidth <= 0 || displayHeight <= 0) return
    val pointJsonData =
        s.captchaPoints.joinToString(",", "[", "]") { p ->
          val ox = kotlin.math.round(310.0 * p.x / displayWidth).toInt()
          val oy = kotlin.math.round(155.0 * p.y / displayHeight).toInt()
          "{\"x\":$ox,\"y\":$oy}"
        }
    val pointJson =
        runCatching { encryptCgyyClickWordPointJson(pointJsonData, captcha.secretKey) }
            .getOrElse {
              _uiState.update { it.copy(captchaError = "验证码坐标加密失败") }
              return
            }
    _uiState.update { it.copy(isCaptchaLoading = true, captchaError = null) }
    viewModelScope.launch {
      cgyyApi
          .checkClickWordCaptcha(pointJson, captcha.token)
          .onSuccess { check ->
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
            _uiState.update {
              it.copy(isCaptchaLoading = false, captchaCheck = finalCheck, captchaError = null)
            }
            submitActive()
          }
          .onFailure {
            _uiState.update {
              it.copy(
                  isCaptchaLoading = false,
                  captchaError = it.message ?: "验证码校验失败",
                  captchaPoints = emptyList(),
              )
            }
          }
    }
  }

  /** 提交当前锁定意向。 */
  private fun submitActive() {
    val s = _uiState.value
    val draft = s.grabDraft ?: return
    val check = s.captchaCheck ?: return
    val active = s.optionStatuses.firstOrNull { it.index == s.activeOptionIndex } ?: return
    val timeId = active.resolvedTimeId
    if (timeId == null) {
      _uiState.update {
        it.copy(
            captcha = null,
            captchaImage = null,
            captchaPoints = emptyList(),
            captchaCheck = null,
            message = "意向${active.index + 1}时段解析失败，跳过",
            activeOptionIndex = -1,
        )
      }
      return
    }
    val slot =
        s.grabDayInfo
            ?.spaces
            ?.firstOrNull { it.spaceId == active.spaceId }
            ?.slots
            ?.firstOrNull { it.timeId == timeId }
    val orderFee = slot?.orderFee ?: 0.0
    val reservationOrderJson = "[{\"spaceId\":\"${active.spaceId}\",\"timeId\":\"$timeId\"}]"
    val orderPin =
        runCatching {
              encryptCgyyOrderPin(
                  s.pinClientX.takeIf { it > 0 } ?: 300,
                  s.pinClientY.takeIf { it > 0 } ?: 300,
              )
            }
            .getOrElse {
              _uiState.update { it.copy(message = "orderPin 生成失败") }
              return
            }
    val request =
        CgyySportOrderSubmitRequest(
            venueSiteId = draft.venueSiteId,
            reservationDate = draft.date,
            weekStartDate = venueMondayOfWeek(draft.date),
            reservationOrderJson = reservationOrderJson,
            orderPrice = orderFee,
            orderPin = orderPin,
            phone = draft.phone,
            buddyUids = "",
            buddyIds = "",
            captchaVerification = check.captchaVerification,
            captchaToken = check.captchaToken,
        )
    _uiState.update {
      it.copy(isSubmitting = true, message = "正在提交意向${active.index + 1}（${active.displayLabel}）…")
    }
    viewModelScope.launch {
      cgyyApi
          .submitSportOrder(request)
          .onSuccess { result ->
            if (result.success) {
              monitorJob?.cancel()
              _uiState.update {
                it.copy(
                    isSubmitting = false,
                    result = result.tradeNo ?: "成功",
                    grabActive = false,
                    message = "抢场成功！订单号 ${result.tradeNo ?: "-"}",
                )
              }
            } else {
              handleSubmitFailure(result.message ?: "预约失败")
            }
          }
          .onFailure { e -> handleSubmitFailure(e.message ?: "预约失败") }
    }
  }

  /**
   * 提交失败处理：
   * - 「已被定/已被预约/被抢」→ 标记该意向，自动降级到下一个可用。
   * - 网络/瞬态错误 → 保留当前意向，重拉验证码重试。
   * - 其他（未知拒绝）→ 连续 2 次失败视为该意向不可用，降级。
   */
  private fun handleSubmitFailure(rawMessage: String) {
    val s = _uiState.value
    val active = s.optionStatuses.firstOrNull { it.index == s.activeOptionIndex }
    val idx = active?.index ?: -1
    _uiState.update {
      it.copy(
          isSubmitting = false,
          captcha = null,
          captchaImage = null,
          captchaPoints = emptyList(),
          captchaCheck = null,
      )
    }
    val text = rawMessage
    val kind = classifySubmitFailure(text)
    when (kind) {
      SubmitFailureKind.CAPTCHA_ERROR -> {
        // 验证码/风控类失败：不再自动重拉验证码（会反复弹窗直至服务端验证码上限），直接停止并提示。
        monitorJob?.cancel()
        _uiState.update {
          it.copy(
              activeOptionIndex = -1,
              grabActive = false,
              captcha = null,
              captchaImage = null,
              message = "验证码异常（${text}），已停止抢场，请稍后重试",
          )
        }
      }
      SubmitFailureKind.TAKEN,
      SubmitFailureKind.UNKNOWN -> {
        // 被抢 或 未知拒绝 → 该意向记一次失败并降级（failCount 在 resolveAndPick 中保留，超 2 次视为不可用）
        val taken = kind == SubmitFailureKind.TAKEN
        _uiState.update {
          it.copy(
              optionStatuses =
                  it.optionStatuses.map { st ->
                    if (st.index == idx)
                        st.copy(
                            failCount = st.failCount + 1,
                            isTaken = if (taken) true else st.isTaken,
                        )
                    else st
                  },
              activeOptionIndex = -1,
              message = "意向${idx + 1}提交失败（${text}），自动切换下一个",
          )
        }
        recheckAndPick()
      }
      SubmitFailureKind.TRANSIENT -> {
        // 网络/瞬态：保留当前意向，重试
        _uiState.update { it.copy(message = "提交失败（${text}），重试当前意向") }
        fetchCaptcha()
      }
    }
  }

  private fun recheckAndPick() {
    val draft = _uiState.value.grabDraft ?: return
    viewModelScope.launch {
      val info =
          runCatching { cgyyApi.getDayInfo(draft.venueSiteId, draft.date).getOrThrow() }.getOrNull()
      if (info != null) {
        _uiState.update { it.copy(grabDayInfo = info) }
        resolveAndPick(info)
      } else {
        resolveAndPick(_uiState.value.grabDayInfo ?: return@launch)
      }
    }
  }

  // ===================== 工具 =====================

  private fun addDays(date: String, days: Int): String =
      runCatching { LocalDate.parse(date).plus(days, DateTimeUnit.DAY).toString() }
          .getOrDefault(date)

  /** 目标日所在周周一（下单 weekStartDate）。 */
  private fun venueMondayOfWeek(date: String): String =
      runCatching {
            val d = LocalDate.parse(date)
            d.minus(d.dayOfWeek.ordinal - 1, DateTimeUnit.DAY).toString()
          }
          .getOrDefault(date)

  private fun randomDelay(minMs: Long, maxMs: Long): Long =
      minMs + (Math.random() * (maxMs - minMs)).toLong()

  internal companion object {
    const val GRAB_TIMEOUT_MINUTES = 15L
    /** 单次抢场最多拉取验证码次数（自动+手动），超过即停止，避免触发服务端验证码风控上限。 */
    const val MAX_CAPTCHA_PULLS = 10

    /**
     * 重建意向状态（dayInfo 刷新后）。
     *
     * 关键：保留 [GrabOptionStatus.failCount] 与服务端已确认的 [GrabOptionStatus.isTaken]—— dayInfo
     * 刷新（或提交失败时用的旧快照）可能短暂仍显示可抢，若清零会导致同一意向被无限重选、 反复弹验证码直到服务端验证码上限。服务端判定优先级高于轮询快照。
     */
    internal fun buildGrabStatuses(
        info: CgyyDayInfoResponse,
        options: List<PriorityOption>,
        prev: Map<Int, GrabOptionStatus>,
        activeIndex: Int,
    ): List<GrabOptionStatus> =
        options.mapIndexed { i, o ->
          val space = info.spaces.firstOrNull { it.spaceId == o.spaceId }
          val slot =
              space?.slots?.firstOrNull { s ->
                info.timeSlots.firstOrNull { it.id == s.timeId }?.beginTime == o.timeLabel
              }
          val prevStatus = prev[i]
          val taken = prevStatus?.isTaken == true || (slot != null && !slot.isReservable)
          GrabOptionStatus(
              index = i,
              spaceId = o.spaceId,
              timeLabel = o.timeLabel,
              displayLabel = o.displayLabel,
              resolvedTimeId = slot?.timeId,
              isReservable = slot?.isReservable == true && !taken,
              isTaken = taken,
              isUnavailable = slot == null || prevStatus?.isUnavailable == true,
              isActive = i == activeIndex,
              failCount = prevStatus?.failCount ?: 0,
          )
        }

    /** 提交失败消息分类（中文字段匹配）：被占 / 验证码风控 / 网络瞬态 / 未知。 */
    internal fun classifySubmitFailure(text: String): SubmitFailureKind {
      val taken =
          listOf("已被定", "已被预约", "已预约", "已被抢", "已被他人", "已满", "已占用", "该时段", "重复", "冲突", "不可用").any {
            it in text
          }
      val network = listOf("网络", "超时", "timeout", "连接", "请稍后重试", "系统忙").any { it in text }
      val captchaError =
          listOf("验证码", "captcha", "频繁", "次数过多", "操作频繁", "上限", "风控").any { it in text }
      return when {
        captchaError -> SubmitFailureKind.CAPTCHA_ERROR
        taken -> SubmitFailureKind.TAKEN
        network -> SubmitFailureKind.TRANSIENT
        else -> SubmitFailureKind.UNKNOWN
      }
    }
  }
}
