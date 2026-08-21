package cn.edu.ubaa.model.dto

import kotlinx.serialization.Serializable

/** SPOC 作业提交状态。 */
@Serializable
enum class SpocSubmissionStatus {
  SUBMITTED,
  UNSUBMITTED,
  UNKNOWN,
}

/** SPOC 作业列表响应。 */
@Serializable
data class SpocAssignmentsResponse(
    val termCode: String,
    val termName: String? = null,
    val assignments: List<SpocAssignmentSummaryDto>,
)

/** SPOC 作业摘要信息。 */
@Serializable
data class SpocAssignmentSummaryDto(
    val assignmentId: String,
    val courseId: String,
    val courseName: String,
    val teacherName: String? = null,
    val title: String,
    val startTime: String? = null,
    val dueTime: String? = null,
    val score: String? = null,
    val submissionStatus: SpocSubmissionStatus,
    val submissionStatusText: String,
)

/** SPOC 作业详情。 */
@Serializable
data class SpocAssignmentDetailDto(
    val assignmentId: String,
    val courseId: String,
    val courseName: String,
    val teacherName: String? = null,
    val title: String,
    val startTime: String? = null,
    val dueTime: String? = null,
    val score: String? = null,
    val submissionStatus: SpocSubmissionStatus,
    val submissionStatusText: String,
    val contentPlainText: String? = null,
    val contentHtml: String? = null,
    val submittedAt: String? = null,
)
