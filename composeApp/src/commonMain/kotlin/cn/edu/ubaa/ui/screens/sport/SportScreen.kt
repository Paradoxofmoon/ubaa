package cn.edu.ubaa.ui.screens.sport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.ubaa.model.dto.CgyyDayInfoResponse
import cn.edu.ubaa.model.dto.CgyyReservationSelectionDto
import cn.edu.ubaa.model.dto.CgyySpaceAvailabilityDto
import cn.edu.ubaa.model.dto.CgyySlotStatusDto
import cn.edu.ubaa.model.dto.CgyyTimeSlotDto
import cn.edu.ubaa.model.dto.CgyyVenueSiteDto
import cn.edu.ubaa.ui.screens.cgyy.CgyyViewModel

/** 运动场订场独立 UI（不复用研讨室审批式表单）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportScreen(
    viewModel: CgyyViewModel,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  val initialError = uiState.initialError
  val dayInfoError = uiState.dayInfoError

  Column(modifier = modifier.fillMaxSize()) {
    when {
      uiState.isInitialLoading && uiState.sites.isEmpty() ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
      initialError != null && uiState.sites.isEmpty() ->
          Column(
              modifier = Modifier.fillMaxSize().padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
          ) {
            Text(initialError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = viewModel::loadInitialData) { Text("重试") }
          }
      else -> {
        SportSiteSelector(
            sites = uiState.sites,
            selectedSiteId = uiState.selectedSiteId,
            onSelectSite = viewModel::selectSite,
        )
        SportDateSelector(
            dates = uiState.dayInfo?.availableDates.orEmpty(),
            selectedDate = uiState.selectedDate,
            onSelectDate = viewModel::selectDate,
        )

        Box(modifier = Modifier.weight(1f)) {
          when {
            uiState.isDayInfoLoading && uiState.dayInfo == null ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator()
                }
            dayInfoError != null ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                  Text(dayInfoError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                  Spacer(Modifier.height(8.dp))
                  Button(onClick = viewModel::refreshReserveData) { Text("重试") }
                }
            else ->
                SportTimeGrid(
                    dayInfo = uiState.dayInfo,
                    selections = uiState.selections,
                    onToggleSlot = viewModel::toggleSlot,
                )
          }
        }

        uiState.actionMessage?.let { msg ->
          Text(
              msg,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
          )
        }

        val totalFee =
            uiState.dayInfo?.let { info ->
              uiState.selections.sumOf { sel ->
                info.spaces
                    .firstOrNull { it.spaceId == sel.spaceId }
                    ?.slots?.firstOrNull { it.timeId == sel.timeId }
                    ?.orderFee ?: 0.0
              }
            } ?: 0.0

        SportSubmitBar(
            phone = uiState.phone,
            onPhoneChange = viewModel::updatePhone,
            totalFee = totalFee.takeIf { it > 0 },
            selectedCount = uiState.selections.size,
            isSubmitting = uiState.isSubmitting,
            onSubmit = { viewModel.submitReservation() },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SportSiteSelector(
    sites: List<CgyyVenueSiteDto>,
    selectedSiteId: Int?,
    onSelectSite: (Int) -> Unit,
) {
  val selectedSite = sites.firstOrNull { it.id == selectedSiteId }
  var expanded by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
      Text(
          selectedSite?.let { "${it.campusName} · ${it.venueName} · ${it.siteName}" } ?: "选择运动场地",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(4.dp))
      Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      sites.forEach { site ->
        DropdownMenuItem(
            text = { Text("${site.campusName} · ${site.venueName} · ${site.siteName}") },
            onClick = {
              onSelectSite(site.id)
              expanded = false
            },
        )
      }
    }
  }
}

@Composable
private fun SportDateSelector(
    dates: List<String>,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
) {
  if (dates.isEmpty()) return
  Row(
      modifier =
          Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    dates.forEach { date ->
      val selected = date == selectedDate
      FilterChip(
          selected = selected,
          onClick = { onSelectDate(date) },
          label = { Text(sportDateLabel(date)) },
      )
    }
  }
}

@Composable
private fun SportTimeGrid(
    dayInfo: CgyyDayInfoResponse?,
    selections: List<CgyyReservationSelectionDto>,
    onToggleSlot: (Int, Int, Int?) -> Unit,
) {
  if (dayInfo == null) return
  val timeSlots = dayInfo.timeSlots
  val spaces = dayInfo.spaces
  val headerScroll = rememberScrollState()

  Column(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(headerScroll)) {
      Box(
          modifier =
              Modifier.width(70.dp).height(36.dp).background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
      ) {
        Text("场地", fontWeight = FontWeight.Bold, fontSize = 12.sp)
      }
      timeSlots.forEach { slot ->
        Box(
            modifier =
                Modifier.width(56.dp).height(36.dp).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
          Text(slot.beginTime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
      items(spaces) { space ->
        SportSpaceRow(
            space = space,
            timeSlots = timeSlots,
            selections = selections,
            onToggleSlot = onToggleSlot,
        )
      }
    }
  }
}

@Composable
private fun SportSpaceRow(
    space: CgyySpaceAvailabilityDto,
    timeSlots: List<CgyyTimeSlotDto>,
    selections: List<CgyyReservationSelectionDto>,
    onToggleSlot: (Int, Int, Int?) -> Unit,
) {
  Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
    Box(
        modifier =
            Modifier.width(70.dp).height(44.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
      Text(
          space.spaceName,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    timeSlots.forEach { slot ->
      val slotState = space.slots.firstOrNull { it.timeId == slot.id }
      val selected = selections.any { it.spaceId == space.spaceId && it.timeId == slot.id }
      SportSlotCell(
          slotState = slotState,
          selected = selected,
          onClick = { onToggleSlot(space.spaceId, slot.id, space.venueSpaceGroupId) },
      )
    }
  }
}

@Composable
private fun SportSlotCell(
    slotState: CgyySlotStatusDto?,
    selected: Boolean,
    onClick: () -> Unit,
) {
  val state = slotState
  val fee = state?.orderFee
  val clickable = state?.isReservable == true || selected
  val bg =
      when {
        selected -> MaterialTheme.colorScheme.primary
        state?.isReservable == true -> Color(0xFFE8F5E9)
        else -> MaterialTheme.colorScheme.surfaceVariant
      }
  val text =
      when {
        selected -> "已选"
        state?.isReservable == true && fee != null -> "¥${formatFee(fee)}"
        state?.isReservable == true -> "可约"
        else -> "—"
      }
  val textColor =
      when {
        selected -> MaterialTheme.colorScheme.onPrimary
        state?.isReservable == true -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
      }
  Box(
      modifier =
          Modifier.width(56.dp)
              .height(44.dp)
              .background(bg)
              .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
              .then(
                  if (clickable) Modifier.clickable(onClick = onClick) else Modifier
              ),
      contentAlignment = Alignment.Center,
  ) {
    Text(text, fontSize = 10.sp, color = textColor, textAlign = TextAlign.Center)
  }
}

@Composable
private fun SportSubmitBar(
    phone: String,
    onPhoneChange: (String) -> Unit,
    totalFee: Double?,
    selectedCount: Int,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
) {
  Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
          value = phone,
          onValueChange = onPhoneChange,
          label = { Text("手机号") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          modifier = Modifier.fillMaxWidth(),
      )
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
            if (totalFee != null) "订单金额：¥${formatFee(totalFee)}" else "请选择时段",
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (totalFee != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "已选 $selectedCount 个时段",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(
          onClick = onSubmit,
          enabled = selectedCount > 0 && phone.isNotBlank() && !isSubmitting,
          modifier = Modifier.fillMaxWidth().height(48.dp),
      ) {
        if (isSubmitting) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Text("提交预约")
        }
      }
    }
  }
}

private fun formatFee(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun sportDateLabel(date: String): String =
    if (date.length >= 10) "${date.substring(5, 7)}/${date.substring(8, 10)}" else date
