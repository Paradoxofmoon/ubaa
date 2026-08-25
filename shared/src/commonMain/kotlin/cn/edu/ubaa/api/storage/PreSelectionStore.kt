package cn.edu.ubaa.api.storage

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 抢场预选草稿中的单个意向（按优先级排序，index = 排名）。 */
@Serializable
data class PriorityOption(
    val spaceId: Int,
    val spaceLabel: String,
    /** 时段 label（如 "10:00"），抢场日再解析成当天真实 timeId。 */
    val timeLabel: String,
    val displayLabel: String,
)

/** 一份抢场预选草稿：目标日 X + 按优先级排序的意向列表。 */
@Serializable
data class PriorityDraft(
    val id: String,
    val title: String,
    /** 目标抢场日 X（yyyy-MM-dd）。 */
    val date: String,
    val venueSiteId: Int,
    val venueSiteName: String,
    val phone: String,
    val purposeType: Int? = null,
    val options: List<PriorityOption> = emptyList(),
    val createdAt: Long = 0L,
)

/** 抢场预选草稿本地持久化（russhwolf Settings + JSON，多份草稿）。 */
object PreSelectionStore {
  private const val KEY_DRAFTS = "cgyy_priority_drafts"
  private val json = Json { ignoreUnknownKeys = true }

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  fun loadAll(): List<PriorityDraft> {
    val raw = settings.getStringOrNull(KEY_DRAFTS) ?: return emptyList()
    return runCatching { json.decodeFromString<List<PriorityDraft>>(raw) }.getOrNull()
        ?: emptyList()
  }

  fun saveAll(drafts: List<PriorityDraft>) {
    settings.putString(KEY_DRAFTS, json.encodeToString(drafts))
  }

  /** 新增或覆盖（按 id）。 */
  fun upsert(draft: PriorityDraft) {
    val list = loadAll()
    saveAll(list.filterNot { it.id == draft.id } + draft)
  }

  fun get(id: String): PriorityDraft? = loadAll().firstOrNull { it.id == id }

  fun delete(id: String) {
    val list = loadAll()
    saveAll(list.filterNot { it.id == id })
  }
}
