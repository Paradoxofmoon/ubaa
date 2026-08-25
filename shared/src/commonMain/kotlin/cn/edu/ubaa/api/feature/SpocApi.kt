package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.model.dto.SpocAssignmentDetailDto
import cn.edu.ubaa.model.dto.SpocAssignmentsResponse

interface SpocApiBackend {
  suspend fun getAssignments(): Result<SpocAssignmentsResponse>

  suspend fun getAssignmentDetail(assignmentId: String): Result<SpocAssignmentDetailDto>
}

/** SPOC 作业查询 API。 */
open class SpocApi(
    private val backendProvider: () -> SpocApiBackend = { ConnectionRuntime.apiFactory().spocApi() }
) {
  internal constructor(backend: SpocApiBackend) : this({ backend })

  private fun currentBackend(): SpocApiBackend = backendProvider()

  /** 获取当前默认学期的所有作业摘要。 */
  open suspend fun getAssignments(): Result<SpocAssignmentsResponse> {
    return currentBackend().getAssignments()
  }

  /** 获取指定作业的详细信息。 */
  open suspend fun getAssignmentDetail(assignmentId: String): Result<SpocAssignmentDetailDto> {
    return currentBackend().getAssignmentDetail(assignmentId)
  }
}
