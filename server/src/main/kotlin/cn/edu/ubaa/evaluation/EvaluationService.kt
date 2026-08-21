package cn.edu.ubaa.evaluation

import cn.edu.ubaa.model.evaluation.EvaluationCourse
import cn.edu.ubaa.model.evaluation.EvaluationCoursesResponse
import cn.edu.ubaa.model.evaluation.EvaluationProgress
import cn.edu.ubaa.model.evaluation.EvaluationResult
import cn.edu.ubaa.utils.withUpstreamDeadline
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/** 评教业务服务。 负责聚合评教所需数据、执行自动填表逻辑并调用客户端提交。 实现了与 Python 脚本一致的自动评教算法。 */
class EvaluationService {
  private val log = LoggerFactory.getLogger(EvaluationService::class.java)

  /**
   * 获取指定用户的所有评教课程列表（包括已评教和未评教）。
   *
   * 执行流程：
   * 1. 初始化评教系统会话（CAS认证）
   * 2. 获取所有评教任务
   * 3. 对每个任务，获取问卷列表
   * 4. 对每个问卷，按上游默认条件获取最新待评课程
   * 6. 返回汇总后的课程列表和进度信息
   *
   * @param username 用户学号。
   * @return 包含所有课程和评教进度的响应对象。
   */
  suspend fun getAllCourses(username: String): EvaluationCoursesResponse {
    return withUpstreamDeadline(11.seconds, "评教列表加载超时", "evaluation_timeout") {
      val client = EvaluationClient(username)
      if (!client.initSession()) {
        log.warn("Failed to init SPOC session for user: $username")
        return@withUpstreamDeadline EvaluationCoursesResponse(
            courses = emptyList(),
            progress = EvaluationProgress(0, 0, 0),
        )
      }

      val tasks = client.fetchTasks()
      // 使用 map 以课程唯一键去重，若任一来源标记已评教，则合并结果也标记已评教
      val courseMap = mutableMapOf<String, EvaluationCourse>()

      for (task in tasks) {
        val questionnaires = client.fetchQuestionnaires(task.rwid)
        for (wj in questionnaires) {
          val msid = wj.msid ?: "1"

          val pendingCourses = client.fetchCourses(task.rwid, wj.wjid)
          mergeCourses(courseMap, pendingCourses, task.rwid, wj.wjid, null, msid, false)
        }
      }

      val allCourses = courseMap.values.toList()
      val pendingCount = allCourses.count { !it.isEvaluated }
      val evaluatedCount = allCourses.count { it.isEvaluated }

      // 排序：未评教的在前，已评教的在后
      val sortedCourses = allCourses.sortedBy { it.isEvaluated }

      EvaluationCoursesResponse(
          courses = sortedCourses,
          progress =
              EvaluationProgress(
                  totalCourses = allCourses.size,
                  evaluatedCourses = evaluatedCount,
                  pendingCourses = pendingCount,
              ),
      )
    }
  }

  /**
   * 获取指定用户的待评教课程列表（仅未评教课程）。
   *
   * 执行流程：
   * 1. 初始化评教系统会话（CAS认证）
   * 2. 通过 queryDaiBan 接口检查用户是否有待评教任务
   *
   * ```
   *    - 若没有待评教任务，则直接返回空列表（用户已完成所有评教）
   * ```
   * 3. 获取所有评教任务
   * 4. 对每个任务，获取问卷列表
   * 5. 对每个问卷，调用 getRequiredReviewsData 的默认查询获取最新待评课程
   * 6. 返回汇总后的待评教课程列表
   *
   * **重要**：返回的课程列表已经是未评教的课程，前端可直接展示。
   *
   * @param username 用户学号。
   * @return 待评教课程列表（未评教的课程）。
   */
  suspend fun getPendingCourses(username: String): List<EvaluationCourse> {
    val response = getAllCourses(username)
    return response.courses.filter { !it.isEvaluated }
  }

  /**
   * 对选定的课程列表执行自动评教。
   *
   * @param username 用户学号。
   * @param courses 需要进行自动评教的课程列表。
   * @return 每个课程的评教结果（成功或失败消息）。
   */
  suspend fun autoEvaluate(
      username: String,
      courses: List<EvaluationCourse>,
  ): List<EvaluationResult> {
    val client = EvaluationClient(username)
    client.initSession()

    val results = mutableListOf<EvaluationResult>()

    for (course in courses) {
      try {
        // 0. 激活问卷模式（关键步骤！必须在获取题目和提交前调用）
        client.reviseQuestionnairePattern(course.rwid, course.wjid, course.msid)

        // 1. 获取题目
        val spocCourse =
            SpocCourse(
                rwid = course.rwid,
                wjid = course.wjid,
                kcdm = course.kcdm,
                kcmc = course.kcmc,
                bpdm = course.bpdm,
                bpmc = course.bpmc,
                pjrdm = course.pjrdm,
                pjrmc = course.pjrmc,
                xnxq = course.xnxq,
                zdmc = course.zdmc,
                ypjcs = course.ypjcs,
                xypjcs = course.xypjcs,
                sxz = course.sxz,
                rwh = course.rwh,
                xn = course.xn,
                xq = course.xq,
                pjlxid = course.pjlxid,
                sfksqbpj = course.sfksqbpj,
                yxsfktjst = course.yxsfktjst,
                msid = course.msid,
            )

        val topicData = client.fetchQuestionnaireTopic(spocCourse)
        if (topicData == null) {
          results.add(EvaluationResult(false, "无法获取问卷题目", course.kcmc))
          continue
        }

        // 2. 模拟填表逻辑
        val wjEntity = topicData["pjxtWjWjbReturnEntity"]?.jsonObject
        val wjzblist = wjEntity?.get("wjzblist")?.jsonArray ?: emptyJsonArray

        // 展平所有问题
        val allQuestions = mutableListOf<JsonObject>()
        wjzblist.forEach { zb ->
          zb.jsonObject["tklist"]?.jsonArray?.forEach { q -> allQuestions.add(q.jsonObject) }
        }

        if (allQuestions.isEmpty()) {
          results.add(EvaluationResult(false, "问卷没有题目", course.kcmc))
          continue
        }

        val randomIndex = Random.nextInt(allQuestions.size)

        val pjjglist = mutableListOf<JsonObject>()
        val pjjgckb = topicData["pjxtPjjgPjjgckb"]?.jsonArray ?: emptyJsonArray
        val pjmap = topicData["pjmap"] // JsonElement? (可能为 null)

        pjjgckb.forEach { pjjgRaw ->
          val pjjg = pjjgRaw.jsonObject
          val pjxxlist = mutableListOf<JsonObject>()

          allQuestions.forEachIndexed { i, q ->
            val tmlx = q["tmlx"]?.jsonPrimitive?.content ?: "1"
            val isChoiceQuestion = tmlx == "1"
            val tmxxlist = q["tmxxlist"]?.jsonArray ?: emptyJsonArray

            val firstOptionId = tmxxlist.firstOrNull()?.jsonObject?.get("tmxxid")
            val selectedOptionId =
                if (isChoiceQuestion && i == randomIndex && tmxxlist.size > 1) {
                  tmxxlist[1].jsonObject["tmxxid"]
                } else if (isChoiceQuestion) {
                  firstOptionId
                } else {
                  null
                }

            pjxxlist.add(
                buildJsonObject {
                  put("sjly", "1")
                  put("stlx", if (isChoiceQuestion) "1" else "6")
                  put("wjid", course.wjid)
                  put("wjssrwid", pjjg["wjssrwid"] ?: JsonNull)
                  put(
                      "wjstctid",
                      if (isChoiceQuestion) JsonPrimitive("")
                      else firstOptionId ?: JsonPrimitive(""),
                  )
                  put("wjstid", q["tmid"] ?: JsonNull)

                  putJsonArray("xxdalist") { selectedOptionId?.let { add(it) } }
                }
            )
          }

          pjjglist.add(
              buildJsonObject {
                put("bprdm", pjjg["bprdm"] ?: JsonNull)
                put("bprmc", pjjg["bprmc"] ?: JsonNull)
                put("kcdm", pjjg["kcdm"] ?: JsonNull)
                put("kcmc", pjjg["kcmc"] ?: JsonNull)
                put("pjdf", 93)
                put("pjfs", pjjg["pjfs"] ?: JsonPrimitive("1"))
                put("pjid", pjjg["pjid"] ?: JsonNull)
                put("pjlx", pjjg["pjlx"] ?: JsonNull)
                put("pjmap", pjmap ?: JsonNull)
                put("pjrdm", pjjg["pjrdm"] ?: JsonNull)
                put("pjrjsdm", pjjg["pjrjsdm"] ?: JsonNull)
                put("pjrxm", pjjg["pjrxm"] ?: JsonNull)
                put("pjsx", 1)
                putJsonArray("pjxxlist") { pjxxlist.forEach { add(it) } }
                put("rwh", pjjg["rwh"] ?: JsonNull)
                put("stzjid", "xx")
                put("wjid", course.wjid)
                put("wjssrwid", pjjg["wjssrwid"] ?: JsonNull)
                put("wtjjy", "")

                // 显式写入 null 值以匹配 Python 脚本的 None 行为
                put("xhgs", JsonNull)
                put("xnxq", pjjg["xnxq"] ?: JsonNull)
                put("sfxxpj", pjjg["sfxxpj"] ?: JsonPrimitive("1"))
                put("sqzt", JsonNull)
                put("yxfz", JsonNull)

                put("zsxz", pjjg["pjrjsdm"] ?: JsonPrimitive(""))
                put("sfnm", "1")
              }
          )
        }

        // 3. 提交
        val submitResp = client.submitEvaluation(pjjglist)
        if (submitResp?.isSuccess() == true) {
          results.add(EvaluationResult(true, "评教成功", course.kcmc))
        } else {
          val msg = submitResp?.message ?: "提交失败，可能已评教"
          val code = submitResp?.codeString() ?: "no-code"
          results.add(EvaluationResult(false, "提交失败(code=$code): $msg", course.kcmc))
        }
      } catch (e: Exception) {
        log.error("Error evaluating course ${course.kcmc}", e)
        results.add(EvaluationResult(false, "处理异常: ${e.message}", course.kcmc))
      }
    }

    return results
  }

  private val emptyJsonArray = JsonArray(emptyList())

  private fun mergeCourses(
      courseMap: MutableMap<String, EvaluationCourse>,
      courses: List<SpocCourse>,
      rwid: String,
      wjid: String,
      xnxq: String?,
      msid: String,
      isEvaluated: Boolean,
  ) {
    courses.forEach { spocCourse ->
      val key = "${rwid}_${wjid}_${spocCourse.kcdm}_${spocCourse.bpdm}"
      val existing = courseMap[key]
      val evaluatedByUpstream = (spocCourse.ypjcs ?: 0) > 0

      val merged =
          EvaluationCourse(
              id = key,
              kcmc = spocCourse.kcmc,
              bpmc = spocCourse.bpmc ?: existing?.bpmc ?: "未知教师",
              isEvaluated = isEvaluated || evaluatedByUpstream || existing?.isEvaluated == true,
              rwid = rwid,
              wjid = wjid,
              kcdm = spocCourse.kcdm,
              bpdm = spocCourse.bpdm,
              pjrdm = spocCourse.pjrdm ?: existing?.pjrdm,
              pjrmc = spocCourse.pjrmc ?: existing?.pjrmc,
              xnxq = spocCourse.xnxq ?: xnxq,
              msid = msid,
              zdmc = spocCourse.zdmc ?: existing?.zdmc ?: "STID",
              ypjcs = spocCourse.ypjcs ?: existing?.ypjcs ?: 0,
              xypjcs = spocCourse.xypjcs ?: existing?.xypjcs ?: 1,
              sxz = spocCourse.sxz ?: existing?.sxz,
              rwh = spocCourse.rwh ?: existing?.rwh,
              xn = spocCourse.xn ?: existing?.xn,
              xq = spocCourse.xq ?: existing?.xq,
              pjlxid = spocCourse.pjlxid ?: existing?.pjlxid ?: "2",
              sfksqbpj = spocCourse.sfksqbpj ?: existing?.sfksqbpj ?: "1",
              yxsfktjst = spocCourse.yxsfktjst ?: existing?.yxsfktjst,
          )

      courseMap[key] = merged
    }
  }
}

/** 全局评教服务单例。 */
object GlobalEvaluationService {
  val instance: EvaluationService by lazy { EvaluationService() }
}
