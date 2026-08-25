package cn.edu.ubaa.api.storage

import cn.edu.ubaa.model.dto.UserData
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/** 用户基本身份缓存：启动时乐观进入主界面用（不等 auth 网络往返）。 */
object UserDataStore {
  private const val KEY_USER_DATA = "auth_user_data"
  private val json = Json { ignoreUnknownKeys = true }

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  fun save(user: UserData) {
    settings.putString(KEY_USER_DATA, json.encodeToString(user))
  }

  fun get(): UserData? {
    val raw = settings.getStringOrNull(KEY_USER_DATA) ?: return null
    return runCatching { json.decodeFromString<UserData>(raw) }.getOrNull()
  }

  fun clear() {
    settings.remove(KEY_USER_DATA)
  }
}
