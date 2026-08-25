package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.model.dto.*

interface SigninApiBackend {
  suspend fun getTodayClasses(): Result<SigninStatusResponse>

  suspend fun performSignin(courseId: String): Result<SigninActionResponse>
}

/** 课堂签到 API 服务。 用于查询今日可签到的课堂及执行签到动作。 */
class SigninApi(
    private val backendProvider: () -> SigninApiBackend = {
      ConnectionRuntime.apiFactory().signinApi()
    }
) {
  internal constructor(backend: SigninApiBackend) : this({ backend })

  private fun currentBackend(): SigninApiBackend = backendProvider()

  /**
   * 获取今日所有有签到任务的课堂列表。
   *
   * @return 签到状态响应，包含课堂列表。
   */
  suspend fun getTodayClasses(): Result<SigninStatusResponse> {
    return currentBackend().getTodayClasses()
  }

  /**
   * 执行课堂签到。
   *
   * @param courseId 课程 ID。
   * @return 签到操作执行结果。
   */
  suspend fun performSignin(courseId: String): Result<SigninActionResponse> {
    return currentBackend().performSignin(courseId)
  }
}
