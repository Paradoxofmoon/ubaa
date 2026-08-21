package cn.edu.ubaa.ui.screens.libbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.LibBookApi
import cn.edu.ubaa.model.dto.LibBookAreaDetailDto
import cn.edu.ubaa.model.dto.LibBookAreaDto
import cn.edu.ubaa.model.dto.LibBookBookingsResponse
import cn.edu.ubaa.model.dto.LibBookLibraryDto
import cn.edu.ubaa.model.dto.LibBookReserveRequest
import cn.edu.ubaa.model.dto.LibBookSeatDto
import cn.edu.ubaa.model.dto.LibBookTimeSlotDto
import cn.edu.ubaa.model.dto.cancelBlockedMessage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class LibBookUiState(
    val isInitialLoading: Boolean = false,
    val isAreasLoading: Boolean = false,
    val isSeatsLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val isBookingsLoading: Boolean = false,
    val libraries: List<LibBookLibraryDto> = emptyList(),
    val areas: List<LibBookAreaDto> = emptyList(),
    val areaDetail: LibBookAreaDetailDto? = null,
    val seats: List<LibBookSeatDto> = emptyList(),
    val bookings: LibBookBookingsResponse = LibBookBookingsResponse(),
    val selectedLibraryId: String? = null,
    val selectedStoreyId: String? = null,
    val selectedAreaId: String? = null,
    val selectedDay: String = "",
    val selectedSlotId: String? = null,
    val selectedSeatId: String? = null,
    val initialError: String? = null,
    val areasError: String? = null,
    val seatsError: String? = null,
    val bookingsError: String? = null,
    val actionMessage: String? = null,
    val cancelingBookingId: String? = null,
)

@OptIn(ExperimentalTime::class)
class LibBookViewModel(
    private val libBookApi: LibBookApi = LibBookApi(),
    private val currentDateProvider: () -> String = {
      val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      val month = (now.month.ordinal + 1).toString().padStart(2, '0')
      val day = now.day.toString().padStart(2, '0')
      "${now.year}-$month-$day"
    },
) : ViewModel() {
  private var initialLoadedOnce = false
  private var bookingsLoadedOnce = false
  private val _uiState = MutableStateFlow(LibBookUiState(selectedDay = currentDateProvider()))
  val uiState: StateFlow<LibBookUiState> = _uiState.asStateFlow()

  fun ensureInitialLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && initialLoadedOnce) return
    loadLibraries()
  }

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    initialLoadedOnce = false
    bookingsLoadedOnce = false
    _uiState.value = LibBookUiState(selectedDay = _uiState.value.selectedDay)
  }

  fun ensureBookingsLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && bookingsLoadedOnce) return
    loadBookings()
  }

  fun loadLibraries() {
    initialLoadedOnce = true
    val day = _uiState.value.selectedDay.ifBlank { currentDateProvider() }
    viewModelScope.launch {
      _uiState.value =
          _uiState.value.copy(
              isInitialLoading = true,
              initialError = null,
              areasError = null,
              seatsError = null,
              actionMessage = null,
          )
      libBookApi
          .getLibraries(day)
          .onSuccess { libraries ->
            val libraryId =
                _uiState.value.selectedLibraryId?.takeIf { id -> libraries.any { it.id == id } }
                    ?: libraries.firstOrNull()?.id
            val storeyId = resolveStoreyId(libraries, libraryId, _uiState.value.selectedStoreyId)
            _uiState.value =
                _uiState.value.copy(
                    isInitialLoading = false,
                    libraries = libraries,
                    selectedLibraryId = libraryId,
                    selectedStoreyId = storeyId,
                    selectedDay = day,
                    initialError = null,
                )
            if (libraryId != null) {
              loadAreas(libraryId, storeyId, day)
            }
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isInitialLoading = false,
                    initialError = it.message ?: "加载楼馆失败",
                )
          }
    }
  }

  fun selectLibrary(libraryId: String) {
    val libraries = _uiState.value.libraries
    val storeyId = resolveStoreyId(libraries, libraryId, null)
    _uiState.value =
        _uiState.value.copy(
            selectedLibraryId = libraryId,
            selectedStoreyId = storeyId,
            selectedAreaId = null,
            selectedSlotId = null,
            selectedSeatId = null,
            areas = emptyList(),
            areaDetail = null,
            seats = emptyList(),
            actionMessage = null,
        )
    loadAreas(libraryId, storeyId, _uiState.value.selectedDay.ifBlank { currentDateProvider() })
  }

  fun selectStorey(storeyId: String?) {
    val libraryId = _uiState.value.selectedLibraryId ?: return
    _uiState.value =
        _uiState.value.copy(
            selectedStoreyId = storeyId,
            selectedAreaId = null,
            selectedSlotId = null,
            selectedSeatId = null,
            areaDetail = null,
            seats = emptyList(),
            actionMessage = null,
        )
    loadAreas(libraryId, storeyId, _uiState.value.selectedDay.ifBlank { currentDateProvider() })
  }

  fun selectArea(areaId: String) {
    _uiState.value =
        _uiState.value.copy(
            selectedAreaId = areaId,
            selectedSlotId = null,
            selectedSeatId = null,
            seats = emptyList(),
            seatsError = null,
            actionMessage = null,
        )
    loadAreaDetailAndSeats(areaId)
  }

  fun selectSlot(slotId: String) {
    val current = _uiState.value
    val slot = current.areaDetail?.timeSlots?.firstOrNull { it.id == slotId } ?: return
    val areaId = current.selectedAreaId ?: return
    _uiState.value =
        current.copy(
            selectedSlotId = slot.id,
            selectedSeatId = null,
            seats = emptyList(),
            actionMessage = null,
        )
    loadSeats(areaId, current.selectedDay.ifBlank { currentDateProvider() }, slot)
  }

  fun selectSeat(seatId: String) {
    val selected = _uiState.value.seats.firstOrNull { it.id == seatId } ?: return
    if (!selected.isAvailable) {
      _uiState.value = _uiState.value.copy(actionMessage = "该座位当前不可预约")
      return
    }
    _uiState.value =
        _uiState.value.copy(
            selectedSeatId = if (_uiState.value.selectedSeatId == seatId) null else seatId,
            actionMessage = null,
        )
  }

  fun submitReservation() {
    val current = _uiState.value
    val areaId = current.selectedAreaId ?: return setActionMessage("请先选择分区")
    val seatId = current.selectedSeatId ?: return setActionMessage("请先选择可预约座位")
    val slot = current.selectedSlot ?: return setActionMessage("请先选择预约时段")
    val day = current.selectedDay.ifBlank { currentDateProvider() }
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isSubmitting = true, actionMessage = null)
      libBookApi
          .reserve(
              LibBookReserveRequest(
                  areaId = areaId,
                  seatId = seatId,
                  day = day,
                  segment = slot.id,
                  startTime = slot.start,
                  endTime = slot.end,
              )
          )
          .onSuccess {
            _uiState.value =
                _uiState.value.copy(
                    isSubmitting = false,
                    selectedSeatId = null,
                    actionMessage = it.message,
                )
            loadSeats(areaId, day, slot)
            loadBookings()
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isSubmitting = false,
                    actionMessage = it.message ?: "预约失败",
                )
          }
    }
  }

  fun loadBookings(page: Int = 1, limit: Int = 20) {
    bookingsLoadedOnce = true
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isBookingsLoading = true, bookingsError = null)
      libBookApi
          .getBookings(page, limit)
          .onSuccess {
            _uiState.value =
                _uiState.value.copy(
                    isBookingsLoading = false,
                    bookings = it,
                    bookingsError = null,
                )
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isBookingsLoading = false,
                    bookingsError = it.message ?: "加载预约记录失败",
                )
          }
    }
  }

  fun cancelBooking(bookingId: String) {
    val current = _uiState.value
    if (current.cancelingBookingId == bookingId) return
    val booking = current.bookings.bookings.firstOrNull { it.id == bookingId }
    val blockedMessage =
        booking?.cancelBlockedMessage() ?: if (bookingId.isBlank()) "预约记录不存在或已失效，请刷新后重试" else null
    if (blockedMessage != null) {
      setActionMessage(blockedMessage)
      return
    }
    _uiState.value = current.copy(cancelingBookingId = bookingId, actionMessage = null)
    viewModelScope.launch {
      libBookApi
          .cancelBooking(bookingId)
          .onSuccess {
            _uiState.value =
                _uiState.value.copy(
                    cancelingBookingId = null,
                    actionMessage = it.message,
                )
            loadBookings(_uiState.value.bookings.page, _uiState.value.bookings.limit)
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    cancelingBookingId = null,
                    actionMessage = it.message ?: "取消预约失败",
                )
          }
    }
  }

  fun refreshReserveData() {
    val current = _uiState.value
    val areaId = current.selectedAreaId
    val slot = current.selectedSlot
    when {
      areaId != null && slot != null -> loadSeats(areaId, current.selectedDay, slot)
      areaId != null -> loadAreaDetailAndSeats(areaId)
      current.selectedLibraryId != null ->
          loadAreas(current.selectedLibraryId, current.selectedStoreyId, current.selectedDay)
      else -> loadLibraries()
    }
  }

  fun clearActionMessage() {
    _uiState.value = _uiState.value.copy(actionMessage = null)
  }

  private fun loadAreas(libraryId: String, storeyId: String?, day: String) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isAreasLoading = true, areasError = null)
      libBookApi
          .getAreas(libraryId, storeyId, day)
          .onSuccess { areas ->
            val areaId =
                _uiState.value.selectedAreaId?.takeIf { id -> areas.any { it.id == id } }
                    ?: areas.firstOrNull()?.id
            _uiState.value =
                _uiState.value.copy(
                    isAreasLoading = false,
                    areas = areas,
                    selectedAreaId = areaId,
                    areasError = null,
                    selectedSeatId = null,
                )
            if (areaId != null) {
              loadAreaDetailAndSeats(areaId)
            }
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isAreasLoading = false,
                    areasError = it.message ?: "加载分区失败",
                )
          }
    }
  }

  private fun loadAreaDetailAndSeats(areaId: String) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isSeatsLoading = true, seatsError = null)
      libBookApi
          .getAreaDetail(areaId)
          .onSuccess { detail ->
            val day =
                detail.availableDates.firstOrNull()
                    ?: _uiState.value.selectedDay.ifBlank { currentDateProvider() }
            val slot = detail.timeSlots.firstOrNull()
            _uiState.value =
                _uiState.value.copy(
                    areaDetail = detail,
                    selectedDay = day,
                    selectedSlotId = slot?.id,
                    selectedSeatId = null,
                    isSeatsLoading = slot == null,
                    seats = emptyList(),
                    seatsError = if (slot == null) "当前分区暂无可预约时段" else null,
                )
            if (slot != null) {
              loadSeats(areaId, day, slot)
            }
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isSeatsLoading = false,
                    seatsError = it.message ?: "加载分区信息失败",
                )
          }
    }
  }

  private fun loadSeats(areaId: String, day: String, slot: LibBookTimeSlotDto) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isSeatsLoading = true, seatsError = null)
      libBookApi
          .getSeats(areaId, day, slot.start, slot.end)
          .onSuccess { seats ->
            val selectedSeatId =
                _uiState.value.selectedSeatId?.takeIf { id ->
                  seats.any { it.id == id && it.isAvailable }
                }
            _uiState.value =
                _uiState.value.copy(
                    isSeatsLoading = false,
                    seats = seats,
                    selectedSeatId = selectedSeatId,
                    seatsError = null,
                )
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isSeatsLoading = false,
                    seatsError = it.message ?: "加载座位失败",
                )
          }
    }
  }

  private fun setActionMessage(message: String) {
    _uiState.value = _uiState.value.copy(actionMessage = message)
  }

  private fun resolveStoreyId(
      libraries: List<LibBookLibraryDto>,
      libraryId: String?,
      preferredStoreyId: String?,
  ): String? {
    val storeys = libraries.firstOrNull { it.id == libraryId }?.storeys.orEmpty()
    if (storeys.isEmpty()) return null
    return preferredStoreyId?.takeIf { id -> storeys.any { it.id == id } } ?: storeys.first().id
  }
}

val LibBookUiState.selectedLibrary: LibBookLibraryDto?
  get() = libraries.firstOrNull { it.id == selectedLibraryId }

val LibBookUiState.selectedArea: LibBookAreaDto?
  get() = areas.firstOrNull { it.id == selectedAreaId }

val LibBookUiState.selectedSlot: LibBookTimeSlotDto?
  get() = areaDetail?.timeSlots?.firstOrNull { it.id == selectedSlotId }

val LibBookUiState.selectedSeat: LibBookSeatDto?
  get() = seats.firstOrNull { it.id == selectedSeatId }
