package cn.edu.ubaa.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.ScheduleApi
import cn.edu.ubaa.api.storage.ScheduleCacheStore
import cn.edu.ubaa.api.storage.ScheduleSnapshotStore
import cn.edu.ubaa.api.storage.nearbyWeekSerials
import cn.edu.ubaa.model.dto.*
import cn.edu.ubaa.repository.GlobalTermRepository
import cn.edu.ubaa.repository.TermRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 课程表相关业务逻辑的 ViewModel。 负责拉取学期列表、周次列表、周课表详情以及今日课表摘要。 */
class ScheduleViewModel(
    private val scheduleApi: ScheduleApi = ScheduleApi(),
    private val termRepository: TermRepository = GlobalTermRepository.instance,
) : ViewModel() {
  /** 启动核对只检测当前周 ± 此半径的邻近周，不全量拉取。 */
  private companion object {
    const val NEARBY_WEEKS_RADIUS = 3
  }

  private var todayLoadedOnce = false
  private var scheduleLoadedOnce = false
  private var currentWeekLoadedOnce = false

  private val _uiState = MutableStateFlow(ScheduleUiState())
  /** 周课表选择与展示的状态流。 */
  val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

  private val _todayScheduleState = MutableStateFlow(TodayScheduleState())
  /** 今日课表简要摘要的状态流。 */
  val todayScheduleState: StateFlow<TodayScheduleState> = _todayScheduleState.asStateFlow()

  fun ensureTodayLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && todayLoadedOnce) return
    loadTodaySchedule()
  }

  internal fun hasTodayLoaded(): Boolean = todayLoadedOnce

  fun ensureCurrentWeekLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && currentWeekLoadedOnce) return
    loadCurrentWeek(forceRefresh)
  }

  internal fun hasCurrentWeekLoaded(): Boolean = currentWeekLoadedOnce

  fun ensureScheduleLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && scheduleLoadedOnce) return
    loadTerms(forceRefresh)
  }

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    todayLoadedOnce = false
    scheduleLoadedOnce = false
    currentWeekLoadedOnce = false
    _uiState.value = ScheduleUiState()
    _todayScheduleState.value = TodayScheduleState()
  }

  /** 加载今日的课程安排摘要：先展示本地缓存，再后台核对更新（服务器慢/挂掉也能立刻看到）。 */
  fun loadTodaySchedule() {
    todayLoadedOnce = true
    viewModelScope.launch {
      val today = todayString()
      val cache = ScheduleCacheStore.load()
      val cachedToday = cache.todayClasses.takeIf { it.isNotEmpty() && cache.todayDate == today }
      if (cachedToday != null) {
        // 缓存命中：立即展示，不显示加载态
        _todayScheduleState.value =
            TodayScheduleState(isLoading = false, todayClasses = cachedToday, error = null)
      } else {
        _todayScheduleState.value = _todayScheduleState.value.copy(isLoading = true, error = null)
      }
      scheduleApi
          .getTodaySchedule()
          .onSuccess {
            _todayScheduleState.value =
                TodayScheduleState(isLoading = false, todayClasses = it, error = null)
            // 落盘今日课表快照（桌面小组件）+ 本地缓存
            runCatching {
              ScheduleSnapshotStore.save(today, it)
              ScheduleCacheStore.updateToday(today, it, savedAtEpochMs = nowEpochMs())
            }
          }
          .onFailure {
            if (cachedToday != null) {
              // 有缓存兜底：静默失败，保留缓存展示
              _todayScheduleState.value =
                  TodayScheduleState(isLoading = false, todayClasses = cachedToday, error = null)
            } else {
              _todayScheduleState.value =
                  _todayScheduleState.value.copy(
                      isLoading = false,
                      error = it.message ?: "加载今日课表失败",
                  )
            }
          }
    }
  }

  private fun loadCurrentWeek(forceRefresh: Boolean = false) {
    viewModelScope.launch {
      // 缓存优先：先用缓存的学期/周次推断当前周
      val cache = ScheduleCacheStore.load()
      val cachedCurrentWeek =
          cache.terms
              .takeIf { it.isNotEmpty() }
              ?.find { it.selected }
              ?.let { selected ->
                cache.weeksByTerm[selected.itemCode].orEmpty().find { it.curWeek }
              }
      if (cachedCurrentWeek != null) {
        currentWeekLoadedOnce = true
        _uiState.value = _uiState.value.copy(currentWeek = cachedCurrentWeek)
      }
      // 后台核对更新
      termRepository.getTerms(forceRefresh).onSuccess { terms ->
        val selectedTerm = terms.find { it.selected } ?: terms.firstOrNull()
        if (selectedTerm == null) return@onSuccess
        scheduleApi.getWeeks(selectedTerm.itemCode).onSuccess { weeks ->
          val currentWeek = weeks.find { it.curWeek } ?: weeks.firstOrNull()
          currentWeekLoadedOnce = currentWeek != null
          _uiState.value = _uiState.value.copy(currentWeek = currentWeek)
        }
      }
    }
  }

  /** 加载学期列表：先展示缓存（含缓存的周次与课表），再后台核对更新。 */
  fun loadTerms(forceRefresh: Boolean = false) {
    scheduleLoadedOnce = true
    viewModelScope.launch {
      val cache = ScheduleCacheStore.load()
      val cachedTerms = cache.terms.takeIf { it.isNotEmpty() }
      if (cachedTerms != null) {
        // 缓存命中：一次性展示缓存里的学期/周次/当前周课表
        val cachedSelected = cachedTerms.find { it.selected } ?: cachedTerms.first()
        val cachedWeeks = cache.weeksByTerm[cachedSelected.itemCode].orEmpty()
        val cachedWeek = cachedWeeks.find { it.curWeek } ?: cachedWeeks.firstOrNull()
        val cachedSchedule =
            cachedWeek?.let {
              cache.scheduleByTermWeek[ScheduleCacheStore.termWeekKey(cachedSelected, it)]
            }
        _uiState.value =
            _uiState.value.copy(
                isLoading = false,
                error = null,
                terms = cachedTerms,
                selectedTerm = cachedSelected,
                weeks = cachedWeeks,
                selectedWeek = cachedWeek,
                weeklySchedule = cachedSchedule,
            )
      } else {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      }
      reconcileTerms(forceRefresh, hadCache = cachedTerms != null)
    }
  }

  private suspend fun reconcileTerms(forceRefresh: Boolean, hadCache: Boolean) {
    termRepository
        .getTerms(forceRefresh)
        .onSuccess { terms ->
          val selectedTerm = terms.find { it.selected } ?: terms.firstOrNull()
          _uiState.value =
              _uiState.value.copy(isLoading = false, terms = terms, selectedTerm = selectedTerm)
          runCatching { ScheduleCacheStore.updateTerms(terms, savedAtEpochMs = nowEpochMs()) }
          selectedTerm?.let {
            reconcileWeeks(
                it,
                hadCache = ScheduleCacheStore.load().weeksByTerm.containsKey(it.itemCode),
            )
          }
        }
        .onFailure {
          if (hadCache) {
            // 有缓存兜底：静默失败
            _uiState.value = _uiState.value.copy(isLoading = false)
          } else {
            _uiState.value =
                _uiState.value.copy(isLoading = false, error = it.message ?: "加载学期信息失败")
          }
        }
  }

  /** 切换选中的学期。 */
  fun selectTerm(term: Term) {
    _uiState.value =
        _uiState.value.copy(selectedTerm = term, selectedWeek = null, weeklySchedule = null)
    loadWeeks(term)
  }

  /** 加载指定学期的教学周次：先展示缓存，再后台核对更新。 */
  fun loadWeeks(term: Term) {
    viewModelScope.launch {
      val cache = ScheduleCacheStore.load()
      val cachedWeeks = cache.weeksByTerm[term.itemCode].orEmpty()
      if (cachedWeeks.isNotEmpty()) {
        val cachedWeek = cachedWeeks.find { it.curWeek } ?: cachedWeeks.first()
        _uiState.value =
            _uiState.value.copy(
                isLoading = false,
                error = null,
                weeks = cachedWeeks,
                selectedWeek = cachedWeek,
            )
        cache.scheduleByTermWeek[ScheduleCacheStore.termWeekKey(term, cachedWeek)]?.let {
          _uiState.value = _uiState.value.copy(weeklySchedule = it)
        }
      } else {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      }
      reconcileWeeks(term, hadCache = cachedWeeks.isNotEmpty())
    }
  }

  private suspend fun reconcileWeeks(term: Term, hadCache: Boolean) {
    scheduleApi
        .getWeeks(term.itemCode)
        .onSuccess { weeks ->
          val selectedWeek = weeks.find { it.curWeek } ?: weeks.firstOrNull()
          _uiState.value =
              _uiState.value.copy(isLoading = false, weeks = weeks, selectedWeek = selectedWeek)
          runCatching { ScheduleCacheStore.updateWeeks(term, weeks, savedAtEpochMs = nowEpochMs()) }
          if (selectedWeek != null) {
            reconcileNearbyWeeks(term, weeks, selectedWeek)
          }
        }
        .onFailure {
          if (hadCache) {
            _uiState.value = _uiState.value.copy(isLoading = false)
          } else {
            _uiState.value = _uiState.value.copy(isLoading = false, error = it.message ?: "加载周信息失败")
          }
        }
  }

  /**
   * 启动核对：只检测当前周 ± [NEARBY_WEEKS_RADIUS] 的邻近周，不全量拉取。 每拉取一周先写入缓存（扩大缓存覆盖），再与旧缓存比对； 任一邻近周数据有变动 → 触发
   * [refreshAllWeeks] 全量刷新该学期所有周。 手动逐周浏览仍由 [loadWeeklySchedule] 负责（浏览过的周都会记录）。
   */
  private suspend fun reconcileNearbyWeeks(term: Term, weeks: List<Week>, selectedWeek: Week) {
    var changed = false

    // 1) 当前周：必须核对并展示，避免周列表不完整时漏掉当前周
    val selectedSchedule =
        scheduleApi.getWeeklySchedule(term.itemCode, selectedWeek.serialNumber).getOrNull()
    if (selectedSchedule != null) {
      val cached =
          ScheduleCacheStore.load()
              .scheduleByTermWeek[ScheduleCacheStore.termWeekKey(term, selectedWeek)]
      if (cached != null && cached != selectedSchedule) {
        changed = true
      }
      runCatching {
        ScheduleCacheStore.updateWeeklySchedule(
            term,
            selectedWeek,
            selectedSchedule,
            savedAtEpochMs = nowEpochMs(),
        )
      }
      _uiState.value = _uiState.value.copy(weeklySchedule = selectedSchedule)
    }

    // 2) 邻近周：只用于「检测变动」，发现变动才全量刷新
    val totalWeeks = weeks.maxOfOrNull { it.serialNumber } ?: weeks.size
    val nearby =
        nearbyWeekSerials(selectedWeek.serialNumber, totalWeeks, NEARBY_WEEKS_RADIUS) -
            selectedWeek.serialNumber
    for (serial in nearby) {
      val week = weeks.firstOrNull { it.serialNumber == serial } ?: continue
      scheduleApi.getWeeklySchedule(term.itemCode, week.serialNumber).onSuccess { schedule ->
        val cached =
            ScheduleCacheStore.load().scheduleByTermWeek[ScheduleCacheStore.termWeekKey(term, week)]
        if (cached != null && cached != schedule) {
          changed = true
        }
        runCatching {
          ScheduleCacheStore.updateWeeklySchedule(
              term,
              week,
              schedule,
              savedAtEpochMs = nowEpochMs(),
          )
        }
      }
    }

    if (changed) {
      refreshAllWeeks(term, weeks)
    }
  }

  /** 全量刷新：重新拉取该学期所有周并写入缓存（仅检测到变动时触发）。 */
  private suspend fun refreshAllWeeks(term: Term, weeks: List<Week>) {
    weeks.forEach { week ->
      scheduleApi.getWeeklySchedule(term.itemCode, week.serialNumber).onSuccess { schedule ->
        runCatching {
          ScheduleCacheStore.updateWeeklySchedule(
              term,
              week,
              schedule,
              savedAtEpochMs = nowEpochMs(),
          )
        }
        if (week.serialNumber == _uiState.value.selectedWeek?.serialNumber) {
          _uiState.value = _uiState.value.copy(weeklySchedule = schedule)
        }
      }
    }
  }

  /** 切换选中的周次。 */
  fun selectWeek(week: Week) {
    _uiState.value = _uiState.value.copy(selectedWeek = week)
    _uiState.value.selectedTerm?.let { loadWeeklySchedule(it, week) }
  }

  /** 加载指定学期和周次的完整排课表：先展示缓存，再后台核对更新。 */
  fun loadWeeklySchedule(term: Term, week: Week) {
    viewModelScope.launch {
      val key = ScheduleCacheStore.termWeekKey(term, week)
      val cachedSchedule = ScheduleCacheStore.load().scheduleByTermWeek[key]
      if (cachedSchedule != null) {
        _uiState.value =
            _uiState.value.copy(isLoading = false, error = null, weeklySchedule = cachedSchedule)
      } else {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      }
      scheduleApi
          .getWeeklySchedule(term.itemCode, week.serialNumber)
          .onSuccess {
            _uiState.value = _uiState.value.copy(isLoading = false, weeklySchedule = it)
            runCatching {
              ScheduleCacheStore.updateWeeklySchedule(term, week, it, savedAtEpochMs = nowEpochMs())
            }
          }
          .onFailure {
            if (cachedSchedule != null) {
              _uiState.value = _uiState.value.copy(isLoading = false)
            } else {
              _uiState.value =
                  _uiState.value.copy(isLoading = false, error = it.message ?: "加载课程表失败")
            }
          }
    }
  }

  /** 清空错误提示。 */
  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
    _todayScheduleState.value = _todayScheduleState.value.copy(error = null)
  }

  private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

  private fun todayString(): String =
      Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}

/** 周课表界面 UI 状态。 */
data class ScheduleUiState(
    val isLoading: Boolean = false,
    val terms: List<Term> = emptyList(),
    val weeks: List<Week> = emptyList(),
    val currentWeek: Week? = null,
    val selectedTerm: Term? = null,
    val selectedWeek: Week? = null,
    val weeklySchedule: WeeklySchedule? = null,
    val error: String? = null,
)

/** 今日摘要界面 UI 状态。 */
data class TodayScheduleState(
    val isLoading: Boolean = false,
    val todayClasses: List<TodayClass> = emptyList(),
    val error: String? = null,
)
