package cn.edu.ubaa.ui.screens.zfw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.ZfwApi
import cn.edu.ubaa.api.feature.ZfwLoginResult
import cn.edu.ubaa.api.feature.ZfwPayPageData
import cn.edu.ubaa.api.feature.ZfwPayResult
import cn.edu.ubaa.api.storage.CredentialStore
import io.ktor.http.Cookie
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 校园网充值界面 UI 状态。 */
data class ZfwUiState(
    val isLoadingCaptcha: Boolean = false,
    val isLoggingIn: Boolean = false,
    val username: String = "",
    val password: String = "",
    val captcha: String = "",
    val captchaImageBase64: String? = null,
    val needsSms: Boolean = false,
    val smsCode: String = "",
    val smsMessage: String? = null,
    val smsRemainSeconds: Int? = null,
    val loginSuccess: Boolean = false,
    val cookies: List<Cookie> = emptyList(),
    val error: String? = null,
    // ---- 充值表单状态 ----
    val cardNo: String = "",
    val productId: String = "1",
    val amount: String = "",
    val payCaptcha: String = "",
    val payCaptchaImageBase64: String? = null,
    val isLoadingPayCaptcha: Boolean = false,
    val isSubmittingPay: Boolean = false,
    val isLoadingPayPage: Boolean = false,
    val payQrcodeBase64: String? = null,
    val payCashierUrl: String? = null,
)

/** 校园网充值（深澜自助服务门户）ViewModel。 */
class ZfwViewModel(
    private val zfwApi: ZfwApi = ZfwApi(),
) : ViewModel() {
  private val _state = MutableStateFlow(ZfwUiState())
  val state: StateFlow<ZfwUiState> = _state.asStateFlow()

  private var payPageData: ZfwPayPageData? = null

  init {
    prefillCredentials()
    loadCaptcha()
  }

  /** 使用已保存的凭据预填充账号（密码按需填充）。 */
  private fun prefillCredentials() {
    val savedUsername = CredentialStore.getUsername().orEmpty()
    val savedPassword = CredentialStore.getPassword().orEmpty()
    _state.value =
        _state.value.copy(
            username = savedUsername,
            password = savedPassword,
        )
  }

  /** 刷新验证码。 */
  fun refreshCaptcha() {
    loadCaptcha()
  }

  /** 重新进入登录流程。 */
  fun reset() {
    _state.value = ZfwUiState()
    prefillCredentials()
    loadCaptcha()
  }

  fun onUsernameChange(value: String) {
    _state.value = _state.value.copy(username = value)
  }

  fun onPasswordChange(value: String) {
    _state.value = _state.value.copy(password = value)
  }

  fun onCaptchaChange(value: String) {
    _state.value = _state.value.copy(captcha = value)
  }

  fun onSmsCodeChange(value: String) {
    _state.value = _state.value.copy(smsCode = value)
  }

  /** 执行登录（首次或短信验证）。 */
  fun login() {
    performLogin()
  }

  /** 提交短信验证码。 */
  fun submitSms() {
    val current = _state.value
    if (current.smsCode.isBlank()) {
      _state.value = current.copy(error = "请输入短信验证码")
      return
    }
    performLogin(current.smsCode)
  }

  /** 清空错误提示。 */
  fun clearError() {
    _state.value = _state.value.copy(error = null)
  }

  private fun performLogin(smsCode: String? = null) {
    val current = _state.value
    if (current.username.isBlank() || current.password.isBlank()) {
      _state.value = current.copy(error = "请输入账号和密码")
      return
    }
    if (current.captcha.isBlank()) {
      _state.value = current.copy(error = "请输入验证码")
      return
    }

    _state.value =
        current.copy(
            isLoggingIn = true,
            error = null,
            needsSms = false,
            smsMessage = null,
            smsRemainSeconds = null,
        )

    viewModelScope.launch {
      zfwApi
          .login(
              username = current.username,
              password = current.password,
              captcha = current.captcha,
              smsCode = smsCode,
          )
          .onSuccess { result ->
            when (result) {
              is ZfwLoginResult.Success -> {
                _state.value =
                    _state.value.copy(
                        isLoggingIn = false,
                        loginSuccess = true,
                        cookies = result.cookies,
                        error = null,
                    )
                // 登录成功后加载缴费页面与验证码
                loadPayPage()
              }
              is ZfwLoginResult.NeedSms -> {
                _state.value =
                    _state.value.copy(
                        isLoggingIn = false,
                        needsSms = true,
                        smsMessage = result.message,
                        smsRemainSeconds = result.remainSeconds,
                        error = null,
                    )
              }
            }
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(
                    isLoggingIn = false,
                    error = error.message ?: "登录失败，请稍后重试",
                )
          }
    }
  }

  @OptIn(ExperimentalEncodingApi::class)
  private fun loadCaptcha() {
    _state.value = _state.value.copy(isLoadingCaptcha = true)
    viewModelScope.launch {
      runCatching { zfwApi.fetchCaptcha() }
          .onSuccess { (bytes, _) ->
            _state.value =
                _state.value.copy(
                    isLoadingCaptcha = false,
                    captchaImageBase64 = Base64.encode(bytes),
                    captcha = "",
                )
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(
                    isLoadingCaptcha = false,
                    error = error.message ?: "验证码加载失败",
                )
          }
    }
  }

  // ===== 充值表单 =====

  fun onAmountChange(value: String) {
    _state.value = _state.value.copy(amount = value)
  }

  fun onPayCaptchaChange(value: String) {
    _state.value = _state.value.copy(payCaptcha = value)
  }

  /** 登录成功后加载缴费页面（账号 + CSRF）与缴费验证码。 */
  private fun loadPayPage() {
    _state.value = _state.value.copy(isLoadingPayPage = true, error = null)
    viewModelScope.launch {
      zfwApi
          .fetchPayPage()
          .onSuccess { pageData ->
            payPageData = pageData
            _state.value =
                _state.value.copy(
                    isLoadingPayPage = false,
                    cardNo = pageData.cardNo,
                    productId = pageData.productId,
                )
            loadPayCaptcha()
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(
                    isLoadingPayPage = false,
                    error = error.message ?: "加载缴费页面失败",
                )
          }
    }
  }

  /** 刷新缴费验证码。 */
  fun refreshPayCaptcha() {
    loadPayCaptcha()
  }

  @OptIn(ExperimentalEncodingApi::class)
  private fun loadPayCaptcha() {
    _state.value = _state.value.copy(isLoadingPayCaptcha = true)
    viewModelScope.launch {
      zfwApi
          .fetchPayCaptcha()
          .onSuccess { bytes ->
            _state.value =
                _state.value.copy(
                    isLoadingPayCaptcha = false,
                    payCaptchaImageBase64 = Base64.encode(bytes),
                    payCaptcha = "",
                )
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(
                    isLoadingPayCaptcha = false,
                    error = error.message ?: "缴费验证码加载失败",
                )
          }
    }
  }

  /** 提交充值。 */
  @OptIn(ExperimentalEncodingApi::class)
  fun submitPay() {
    val current = _state.value
    val pageData = payPageData
    if (pageData == null) {
      _state.value = current.copy(error = "缴费信息未加载，请重新登录")
      return
    }
    if (current.amount.isBlank()) {
      _state.value = current.copy(error = "请输入充值金额")
      return
    }
    val amountValue = current.amount.toDoubleOrNull()
    if (amountValue == null || amountValue <= 0) {
      _state.value = current.copy(error = "金额必须是大于 0 的数字")
      return
    }
    if (current.payCaptcha.isBlank()) {
      _state.value = current.copy(error = "请输入缴费验证码")
      return
    }

    _state.value = current.copy(isSubmittingPay = true, error = null)
    viewModelScope.launch {
      zfwApi
          .submitPay(
              amount = current.amount,
              captcha = current.payCaptcha,
              payPageData = pageData,
          )
          .onSuccess { result ->
            when (result) {
              is ZfwPayResult.Success -> {
                _state.value =
                    _state.value.copy(
                        isSubmittingPay = false,
                        payQrcodeBase64 = result.qrcodeBytes?.let { Base64.encode(it) },
                        payCashierUrl = result.cashierUrl,
                        error = null,
                    )
              }
              is ZfwPayResult.Failure -> {
                _state.value =
                    _state.value.copy(
                        isSubmittingPay = false,
                        error = result.message,
                    )
                // 验证码可能已失效，刷新验证码与缴费页面令牌
                loadPayCaptcha()
              }
            }
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(
                    isSubmittingPay = false,
                    error = error.message ?: "充值提交失败，请稍后重试",
                )
          }
    }
  }

  /** 关闭二维码展示，返回充值表单。 */
  fun dismissQrcode() {
    _state.value =
        _state.value.copy(
            payQrcodeBase64 = null,
            payCashierUrl = null,
            payCaptcha = "",
        )
    loadPayCaptcha()
  }
}
