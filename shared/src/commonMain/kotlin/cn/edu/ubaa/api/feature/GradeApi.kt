package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.model.dto.GradeData

interface GradeApiBackend {
  suspend fun getGrades(termCode: String): Result<GradeData>
}

class GradeApi(
    private val backendProvider: () -> GradeApiBackend = {
      ConnectionRuntime.apiFactory().gradeApi()
    }
) {
  internal constructor(backend: GradeApiBackend) : this({ backend })

  private fun currentBackend(): GradeApiBackend = backendProvider()

  suspend fun getGrades(termCode: String): Result<GradeData> = currentBackend().getGrades(termCode)
}
