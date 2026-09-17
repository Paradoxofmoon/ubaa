package cn.edu.ubaa.api

import cn.edu.ubaa.api.storage.ScheduleCacheStore
import cn.edu.ubaa.model.dto.Term
import cn.edu.ubaa.model.dto.TodayClass
import cn.edu.ubaa.model.dto.Week
import cn.edu.ubaa.model.dto.WeeklySchedule
import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleCacheStoreTest {
  @BeforeTest
  fun resetStore() {
    ScheduleCacheStore.settings = MapSettings()
  }

  private val term =
      Term(itemCode = "2024-2025-1", itemName = "2024-2025学年第一学期", selected = true, itemIndex = 1)
  private val week =
      Week(
          startDate = "2024-09-02",
          endDate = "2024-09-08",
          term = "2024-2025-1",
          curWeek = true,
          serialNumber = 1,
          name = "第1周",
      )
  private val schedule =
      WeeklySchedule(arrangedList = emptyList(), code = "2024-2025-1", name = "第1周课表")

  @Test
  fun emptyByDefault() {
    val bundle = ScheduleCacheStore.load()
    assertTrue(bundle.terms.isEmpty())
    assertTrue(bundle.weeksByTerm.isEmpty())
    assertTrue(bundle.scheduleByTermWeek.isEmpty())
    assertTrue(bundle.todayClasses.isEmpty())
    assertEquals("", bundle.todayDate)
  }

  @Test
  fun termsRoundTrip() {
    ScheduleCacheStore.updateTerms(listOf(term))

    val loaded = ScheduleCacheStore.load()
    assertEquals(1, loaded.terms.size)
    assertEquals("2024-2025-1", loaded.terms.first().itemCode)
    assertTrue(loaded.terms.first().selected)
    assertTrue(loaded.savedAtEpochMs > 0)
  }

  @Test
  fun weeksAndScheduleAndTodayMerge() {
    ScheduleCacheStore.updateTerms(listOf(term))
    ScheduleCacheStore.updateWeeks(term, listOf(week))
    ScheduleCacheStore.updateWeeklySchedule(term, week, schedule)
    ScheduleCacheStore.updateToday(
        todayDate = "2024-09-03",
        classes =
            listOf(
                TodayClass(bizName = "高等数学", place = "A101", time = "08:00-09:40", shortName = "高数")
            ),
    )

    val loaded = ScheduleCacheStore.load()
    assertEquals(listOf(week), loaded.weeksByTerm[term.itemCode])
    assertEquals(schedule, loaded.scheduleByTermWeek["2024-2025-1|1"])
    assertEquals("2024-09-03", loaded.todayDate)
    assertEquals("高等数学", loaded.todayClasses.first().bizName)
  }

  @Test
  fun updateOverwritesExistingData() {
    ScheduleCacheStore.updateTerms(listOf(term))
    ScheduleCacheStore.updateTerms(listOf(term.copy(selected = false)))

    val loaded = ScheduleCacheStore.load()
    assertEquals(1, loaded.terms.size)
    assertFalse(loaded.terms.first().selected)
  }

  @Test
  fun differentTermsKeepSeparateWeeks() {
    val otherTerm = term.copy(itemCode = "2023-2024-2", selected = false, itemIndex = 0)
    ScheduleCacheStore.updateWeeks(term, listOf(week))
    ScheduleCacheStore.updateWeeks(
        otherTerm,
        listOf(week.copy(term = "2023-2024-2", serialNumber = 2)),
    )

    val loaded = ScheduleCacheStore.load()
    assertEquals(1, loaded.weeksByTerm["2024-2025-1"]?.size)
    assertEquals(2, loaded.weeksByTerm["2023-2024-2"]?.first()?.serialNumber)
  }

  @Test
  fun emptyUpdatesAreIgnored() {
    ScheduleCacheStore.updateTerms(emptyList())
    ScheduleCacheStore.updateWeeks(term, emptyList())

    assertTrue(ScheduleCacheStore.load().terms.isEmpty())
    assertTrue(ScheduleCacheStore.load().weeksByTerm.isEmpty())
  }

  @Test
  fun clearRemovesEverything() {
    ScheduleCacheStore.updateTerms(listOf(term))
    ScheduleCacheStore.updateWeeks(term, listOf(week))
    ScheduleCacheStore.updateWeeklySchedule(term, week, schedule)
    ScheduleCacheStore.updateToday(
        "2024-09-03",
        listOf(TodayClass(bizName = "x", place = null, time = null, shortName = null)),
    )

    ScheduleCacheStore.clear()

    val loaded = ScheduleCacheStore.load()
    assertTrue(loaded.terms.isEmpty())
    assertTrue(loaded.weeksByTerm.isEmpty())
    assertTrue(loaded.scheduleByTermWeek.isEmpty())
    assertTrue(loaded.todayClasses.isEmpty())
  }
}
