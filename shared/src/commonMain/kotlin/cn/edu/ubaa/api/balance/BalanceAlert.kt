package cn.edu.ubaa.api.balance

import com.russhwolf.settings.Settings

/**
 * 余额提醒阈值存储（multiplatform-settings 持久化）。
 *
 * 语义：
 * - 阈值未设置 = 提醒功能关闭；
 * - [getLastAlertDate] 记录最后一次自动提醒的日期（每天最多自动提醒一次）；
 * - [getDismissDate] 记录用户手动关闭横幅的日期（当天不再显示）。
 */
interface BalanceAlertStorage {
  fun getThresholdYuan(): Double?

  fun setThresholdYuan(value: Double?)

  fun getLastAlertDate(): String?

  fun markAlerted(date: String)

  fun getDismissDate(): String?

  fun markDismissed(date: String)
}

object BalanceAlertStore : BalanceAlertStorage {
  private const val KEY_THRESHOLD = "balance_alert_threshold_yuan"
  private const val KEY_LAST_ALERT_DATE = "balance_alert_last_alert_date"
  private const val KEY_DISMISS_DATE = "balance_alert_dismiss_date"

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  override fun getThresholdYuan(): Double? = settings.getDoubleOrNull(KEY_THRESHOLD)

  override fun setThresholdYuan(value: Double?) {
    if (value == null) {
      settings.remove(KEY_THRESHOLD)
    } else {
      settings.putDouble(KEY_THRESHOLD, value)
    }
  }

  override fun getLastAlertDate(): String? = settings.getStringOrNull(KEY_LAST_ALERT_DATE)

  override fun markAlerted(date: String) {
    settings.putString(KEY_LAST_ALERT_DATE, date)
  }

  override fun getDismissDate(): String? = settings.getStringOrNull(KEY_DISMISS_DATE)

  override fun markDismissed(date: String) {
    settings.putString(KEY_DISMISS_DATE, date)
  }
}

/** 余额提醒判定（纯逻辑，可单测）。 */
object BalanceAlertPolicy {

  /** 是否需要发起余额查询：阈值已设置由调用方判断，这里只看去重—— 今天还没自动提醒过、且今天没被用户手动关闭横幅。 */
  fun shouldFetch(today: String, lastAlertDate: String?, dismissDate: String?): Boolean =
      lastAlertDate != today && dismissDate != today

  /** 是否应触发提醒：余额（解析成功）低于阈值，且今天还没自动提醒过。 余额/阈值为 null（解析失败、未设置）一律 false，绝不把解析失败当 0 元误报。 */
  fun shouldAlert(
      balanceYuan: Double?,
      thresholdYuan: Double?,
      lastAlertDate: String?,
      today: String,
  ): Boolean =
      thresholdYuan != null &&
          balanceYuan != null &&
          balanceYuan < thresholdYuan &&
          lastAlertDate != today
}
