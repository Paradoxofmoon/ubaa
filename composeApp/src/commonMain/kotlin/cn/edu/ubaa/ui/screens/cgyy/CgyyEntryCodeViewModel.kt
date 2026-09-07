package cn.edu.ubaa.ui.screens.cgyy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.CgyyApi
import cn.edu.ubaa.api.local.CgyyCaptchaImageData
import cn.edu.ubaa.api.local.decodeCgyyCaptchaImage
import cn.edu.ubaa.api.local.sportVenueDirectApi
import cn.edu.ubaa.model.dto.CgyyEntryCodeView
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** 入场验票「预约码」：拉取 /api/vip/code/view，动态码按 dueDate 倒计时到期自动重拉（网页约 10s 刷新一次）。 */
class CgyyEntryCodeViewModel(
    private val cgyyApi: CgyyApi = sportVenueDirectApi(),
) : ViewModel() {

  data class UiState(
      val isLoading: Boolean = false,
      val error: String? = null,
      val code: CgyyEntryCodeView? = null,
      val qrImage: CgyyCaptchaImageData? = null,
      /** 当前码剩余有效毫秒（>0 表示动态码倒计时；0 = 静态码/未到期信息）。 */
      val remainMs: Long = 0L,
  )

  private val _uiState = MutableStateFlow(UiState())
  val uiState: StateFlow<UiState> = _uiState.asStateFlow()

  private var refreshJob: Job? = null

  init {
    load()
  }

  fun load() {
    refreshJob?.cancel()
    _uiState.update { it.copy(isLoading = true, error = null) }
    refreshJob =
        viewModelScope.launch {
          cgyyApi
              .getVenueEntryCode()
              .onSuccess { code ->
                val qrImage =
                    code.qrCode?.let {
                      runCatching { decodeCgyyCaptchaImage("data:image/png;base64,$it") }
                          .getOrNull()
                    }
                _uiState.update {
                  it.copy(isLoading = false, code = code, qrImage = qrImage, error = null)
                }
                scheduleCountdown(code)
              }
              .onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "预约码获取失败") }
              }
        }
  }

  /** 动态码：按 dueDate 倒计时，归零自动重新拉取新码（网页同款 10s 刷新）。 */
  private fun scheduleCountdown(code: CgyyEntryCodeView) {
    val dueDate = code.dueDate
    if (!code.isDynamicCode || dueDate.isNullOrBlank()) {
      _uiState.update { it.copy(remainMs = 0L) }
      return
    }
    refreshJob =
        viewModelScope.launch {
          var remain = dueDateRemainMs(dueDate)
          if (remain <= 0) {
            load()
            return@launch
          }
          while (remain > 0) {
            _uiState.update { it.copy(remainMs = remain) }
            delay(250)
            remain = dueDateRemainMs(dueDate)
          }
          load() // 到期重拉新码
        }
  }

  private fun dueDateRemainMs(dueDate: String): Long {
    val ldt =
        runCatching { LocalDateTime.parse(dueDate.replace(" ", "T")) }.getOrNull() ?: return 0L
    val due = ldt.toInstant(TimeZone.of("Asia/Shanghai")).toEpochMilliseconds()
    return due - Clock.System.now().toEpochMilliseconds()
  }
}
