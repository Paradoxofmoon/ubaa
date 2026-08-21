package cn.edu.ubaa.signin

import cn.edu.ubaa.model.dto.SigninActionResponse
import cn.edu.ubaa.model.dto.SigninStatusResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/** 课堂签到业务服务。 管理签到系统的独立客户端会话缓存。 */
object SigninService {
  private const val DEFAULT_MAX_IDLE_MILLIS = 30 * 60 * 1000L

  private data class CachedClient(
      val client: SigninClient,
      @Volatile var lastAccessAt: Long,
  )

  private val clientCache = ConcurrentHashMap<String, CachedClient>()

  /** 获取或创建指定学生的签到客户端。 */
  private fun getClient(studentId: String): SigninClient {
    val now = System.currentTimeMillis()
    val cached =
        clientCache.compute(studentId) { _, existing ->
          existing?.also { it.lastAccessAt = now } ?: CachedClient(SigninClient(studentId), now)
        }!!
    return cached.client
  }

  /** 获取今日的签到状态列表。 */
  suspend fun getTodayClasses(studentId: String): SigninStatusResponse {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    val classes = getClient(studentId).getClasses(today)
    return SigninStatusResponse(code = 200, message = "获取成功", data = classes)
  }

  /** 执行签到动作。 */
  suspend fun performSignin(studentId: String, courseId: String): SigninActionResponse {
    val (success, message) = getClient(studentId).signIn(courseId)
    return SigninActionResponse(
        code = if (success) 200 else 400,
        success = success,
        message = message,
    )
  }

  fun cleanupExpiredClients(maxIdleMillis: Long = DEFAULT_MAX_IDLE_MILLIS): Int {
    val cutoff = System.currentTimeMillis() - maxIdleMillis
    var removed = 0
    for ((studentId, cached) in clientCache.entries.toList()) {
      if (cached.lastAccessAt >= cutoff) continue
      if (!clientCache.remove(studentId, cached)) continue
      cached.client.close()
      removed++
    }
    return removed
  }

  fun cacheSize(): Int = clientCache.size

  fun closeAll() {
    clientCache.values.forEach { it.client.close() }
    clientCache.clear()
  }
}
