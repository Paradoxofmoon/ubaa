package cn.edu.ubaa.api.core

import cn.edu.ubaa.api.ConnectionMode
import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.auth.AuthServiceBackend
import cn.edu.ubaa.api.auth.UserServiceBackend
import cn.edu.ubaa.api.feature.BusApiBackend
import cn.edu.ubaa.api.feature.BykcApiBackend
import cn.edu.ubaa.api.feature.CardApiBackend
import cn.edu.ubaa.api.feature.CgyyApiBackend
import cn.edu.ubaa.api.feature.ClassroomApiBackend
import cn.edu.ubaa.api.feature.EvaluationServiceBackend
import cn.edu.ubaa.api.feature.GradeApiBackend
import cn.edu.ubaa.api.feature.JudgeApiBackend
import cn.edu.ubaa.api.feature.LibBookApiBackend
import cn.edu.ubaa.api.feature.NetworkApiBackend
import cn.edu.ubaa.api.feature.ScheduleApiBackend
import cn.edu.ubaa.api.feature.SigninApiBackend
import cn.edu.ubaa.api.feature.SpocApiBackend
import cn.edu.ubaa.api.feature.YgdkApiBackend
import cn.edu.ubaa.api.feature.ZfwApiBackend
import cn.edu.ubaa.api.local.LocalAuthServiceBackend
import cn.edu.ubaa.api.local.LocalBusApiBackend
import cn.edu.ubaa.api.local.LocalBykcApiBackend
import cn.edu.ubaa.api.local.LocalCardApiBackend
import cn.edu.ubaa.api.local.LocalCgyyApiBackend
import cn.edu.ubaa.api.local.LocalClassroomApiBackend
import cn.edu.ubaa.api.local.LocalEvaluationServiceBackend
import cn.edu.ubaa.api.local.LocalGradeApiBackend
import cn.edu.ubaa.api.local.LocalJudgeApiBackend
import cn.edu.ubaa.api.local.LocalLibBookApiBackend
import cn.edu.ubaa.api.local.LocalNetworkApiBackend
import cn.edu.ubaa.api.local.LocalScheduleApiBackend
import cn.edu.ubaa.api.local.LocalSigninApiBackend
import cn.edu.ubaa.api.local.LocalSpocApiBackend
import cn.edu.ubaa.api.local.LocalUserServiceBackend
import cn.edu.ubaa.api.local.LocalYgdkApiBackend
import cn.edu.ubaa.api.local.LocalZfwApiBackend

interface ApiFactory {
  fun authService(): AuthServiceBackend

  fun userService(): UserServiceBackend

  fun scheduleApi(): ScheduleApiBackend

  fun signinApi(): SigninApiBackend

  fun spocApi(): SpocApiBackend

  fun judgeApi(): JudgeApiBackend

  fun bykcApi(): BykcApiBackend

  fun cgyyApi(): CgyyApiBackend

  /** 运动场（venue-server）预约后端，复用 CgyyApiBackend 接口。 */
  fun sportVenueApi(): CgyyApiBackend

  /** 智慧校车订票后端（直达直连 zhihuixiaoche.buaa.edu.cn）。 */
  fun busApi(): BusApiBackend

  fun ygdkApi(): YgdkApiBackend

  fun classroomApi(): ClassroomApiBackend

  fun evaluationService(): EvaluationServiceBackend

  fun gradeApi(): GradeApiBackend

  fun libBookApi(): LibBookApiBackend

  fun cardApi(): CardApiBackend

  fun networkApi(): NetworkApiBackend

  fun zfwApi(): ZfwApiBackend
}

internal object DefaultApiFactory : ApiFactory {
  private val directBackends = LocalBackendSet()
  private val webVpnBackends = LocalBackendSet()

  private fun mode(): ConnectionMode = ConnectionRuntime.currentMode() ?: ConnectionMode.DIRECT

  fun clearCachedBackends() {
    directBackends.clearCache()
    webVpnBackends.clearCache()
  }

  override fun authService(): AuthServiceBackend = localBackends(mode()).authService

  override fun userService(): UserServiceBackend = localBackends(mode()).userService

  override fun scheduleApi(): ScheduleApiBackend = localBackends(mode()).scheduleApi

  override fun signinApi(): SigninApiBackend = localBackends(mode()).signinApi

  override fun spocApi(): SpocApiBackend = localBackends(mode()).spocApi

  override fun judgeApi(): JudgeApiBackend = localBackends(mode()).judgeApi

  override fun bykcApi(): BykcApiBackend = localBackends(mode()).bykcApi

  override fun cgyyApi(): CgyyApiBackend = localBackends(mode()).cgyyApi

  override fun sportVenueApi(): CgyyApiBackend = localBackends(mode()).sportVenueApi

  override fun busApi(): BusApiBackend = localBackends(mode()).busApi

  override fun ygdkApi(): YgdkApiBackend = localBackends(mode()).ygdkApi

  override fun classroomApi(): ClassroomApiBackend = localBackends(mode()).classroomApi

  override fun evaluationService(): EvaluationServiceBackend =
      localBackends(mode()).evaluationService

  override fun gradeApi(): GradeApiBackend = localBackends(mode()).gradeApi

  override fun libBookApi(): LibBookApiBackend = localBackends(mode()).libBookApi

  override fun cardApi(): CardApiBackend = localBackends(mode()).cardApi

  override fun networkApi(): NetworkApiBackend = localBackends(mode()).networkApi

  override fun zfwApi(): ZfwApiBackend = localBackends(mode()).zfwApi

  private fun localBackends(mode: ConnectionMode): LocalBackendSet =
      when (mode) {
        ConnectionMode.DIRECT -> directBackends
        ConnectionMode.WEBVPN -> webVpnBackends
      }

  private class LocalBackendSet {
    val authService = LocalAuthServiceBackend()
    val userService = LocalUserServiceBackend()
    val scheduleApi = LocalScheduleApiBackend()
    val signinApi = LocalSigninApiBackend()
    val spocApi = LocalSpocApiBackend()
    val judgeApi = LocalJudgeApiBackend()
    val bykcApi = LocalBykcApiBackend()
    val cgyyApi = LocalCgyyApiBackend()
    val sportVenueApi = LocalCgyyApiBackend(sportVenue = true)
    val busApi = LocalBusApiBackend()
    val ygdkApi = LocalYgdkApiBackend()
    val classroomApi = LocalClassroomApiBackend()
    val evaluationService = LocalEvaluationServiceBackend()
    val gradeApi = LocalGradeApiBackend()
    val libBookApi = LocalLibBookApiBackend()
    val cardApi = LocalCardApiBackend()
    val networkApi = LocalNetworkApiBackend()
    val zfwApi = LocalZfwApiBackend()

    fun clearCache() {
      signinApi.clearCache()
      spocApi.clearCache()
      judgeApi.clearCache()
      bykcApi.clearCache()
      cgyyApi.clearCache()
      sportVenueApi.clearCache()
      ygdkApi.clearCache()
      classroomApi.clearCache()
      evaluationService.clearCache()
      libBookApi.clearCache()
    }
  }
}
