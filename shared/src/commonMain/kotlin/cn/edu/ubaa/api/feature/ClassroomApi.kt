package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.model.dto.ClassroomQueryResponse

interface ClassroomApiBackend {
  suspend fun queryClassrooms(xqid: Int, date: String): Result<ClassroomQueryResponse>
}

/** 教室查询相关 API。 用于查询指定校区和日期的空闲教室分布情况。 */
open class ClassroomApi(
    private val backendProvider: () -> ClassroomApiBackend = {
      ConnectionRuntime.apiFactory().classroomApi()
    }
) {
  internal constructor(backend: ClassroomApiBackend) : this({ backend })

  private fun currentBackend(): ClassroomApiBackend = backendProvider()

  /**
   * 查询空闲教室列表。
   *
   * @param xqid 校区 ID（如 1:学院路, 2:沙河, 3:杭州）。
   * @param date 查询日期（yyyy-MM-dd）。
   * @return 包含各楼层教室空闲情况的响应体。
   */
  open suspend fun queryClassrooms(xqid: Int, date: String): Result<ClassroomQueryResponse> {
    return currentBackend().queryClassrooms(xqid, date)
  }
}
