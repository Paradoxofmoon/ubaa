package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.model.evaluation.EvaluationCourse
import cn.edu.ubaa.model.evaluation.EvaluationCoursesResponse
import cn.edu.ubaa.model.evaluation.EvaluationResult

interface EvaluationServiceBackend {
  suspend fun getAllEvaluations(): Result<EvaluationCoursesResponse>

  suspend fun submitEvaluations(courses: List<EvaluationCourse>): List<EvaluationResult>
}

class EvaluationService(
    private val backendProvider: () -> EvaluationServiceBackend = {
      ConnectionRuntime.apiFactory().evaluationService()
    }
) {
  internal constructor(backend: EvaluationServiceBackend) : this({ backend })

  private fun currentBackend(): EvaluationServiceBackend = backendProvider()

  /** 获取所有评教课程（包括已评教和未评教），附带进度信息。 */
  suspend fun getAllEvaluations(): Result<EvaluationCoursesResponse> {
    return currentBackend().getAllEvaluations()
  }

  /**
   * 获取待评教课程列表（仅未评教课程）。
   *
   * @deprecated 使用 getAllEvaluations() 获取完整信息。
   */
  suspend fun getPendingEvaluations(): Result<List<EvaluationCourse>> {
    return getAllEvaluations().map { response -> response.courses.filter { !it.isEvaluated } }
  }

  suspend fun submitEvaluations(courses: List<EvaluationCourse>): List<EvaluationResult> {
    return currentBackend().submitEvaluations(courses)
  }
}
