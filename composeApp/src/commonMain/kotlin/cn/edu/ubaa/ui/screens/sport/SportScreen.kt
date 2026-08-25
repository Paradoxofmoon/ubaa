package cn.edu.ubaa.ui.screens.sport

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.ubaa.api.local.CgyyCaptchaImageData
import cn.edu.ubaa.api.local.decodeCgyyCaptchaImage
import cn.edu.ubaa.model.dto.CgyyBuddyDto
import cn.edu.ubaa.model.dto.CgyyClickWordCaptchaDto
import cn.edu.ubaa.model.dto.CgyyDayInfoResponse
import cn.edu.ubaa.model.dto.CgyyOrderPayResult
import cn.edu.ubaa.model.dto.CgyyPurposeTypeDto
import cn.edu.ubaa.model.dto.CgyyReservationSelectionDto
import cn.edu.ubaa.model.dto.CgyySlotStatusDto
import cn.edu.ubaa.model.dto.CgyySpaceAvailabilityDto
import cn.edu.ubaa.model.dto.CgyyTimeSlotDto
import cn.edu.ubaa.model.dto.CgyyVenueSiteDto
import cn.edu.ubaa.ui.common.util.BackHandlerCompat
import cn.edu.ubaa.ui.component.SchemeTriggerWebView
import cn.edu.ubaa.ui.screens.cgyy.CgyySportCaptchaPoint
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
  var showTimePicker by remember { mutableStateOf(false) }
  var showCompanionPicker by remember { mutableStateOf(false) }
  var showGrab by remember { mutableStateOf(false) }
  val grabViewModel: SportGrabViewModel = viewModel(key = "sport-grab") { SportGrabViewModel() }

  Box(modifier = modifier.fillMaxSize()) {
    if (showGrab) {
      // 抢场预选/智能抢场（全屏模式，隐藏正常订场流程）
      SportGrabScreen(
          viewModel = grabViewModel,
          currentSite = uiState.sites.firstOrNull { it.id == uiState.selectedSiteId },
          onExit = { showGrab = false },
      )
    } else {
      Column(modifier = Modifier.fillMaxSize()) {
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
                Text(
                    initialError,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::loadInitialData) { Text("重试") }
              }
          else -> {
            // 抢场预选入口：提前预选第 4 天，开抢日自动锁定/降级
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                  "智能抢场：提前预选第4天，开抢自动锁定",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              TextButton(
                  onClick = {
                    val site = uiState.sites.firstOrNull { it.id == uiState.selectedSiteId }
                    if (site != null) {
                      grabViewModel.openPreSelect(
                          site,
                          uiState.selectedDate,
                          uiState.dayInfo?.availableDates.orEmpty(),
                      )
                    }
                    showGrab = true
                  },
              ) {
                Text("🎯 抢场")
              }
            }
            SportCategorySelector(
                purposeTypes = uiState.purposeTypes,
                selected = uiState.purposeType,
                onSelect = viewModel::selectSportCategory,
            )
            SportSiteSelector(
                sites = uiState.sites,
                selectedSiteId = uiState.selectedSiteId,
                onSelectSite = viewModel::selectSite,
            )

            SportTimeEntry(
                date = uiState.selectedDate,
                selections = uiState.selections,
                isLoading = uiState.isDayInfoLoading && uiState.dayInfo == null,
                error = dayInfoError,
                onOpen = { showTimePicker = true },
                onRetry = viewModel::refreshReserveData,
            )

            uiState.actionMessage?.let { msg ->
              Text(
                  msg,
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall,
              )
            }

            SportCompanionEntry(
                buddies = uiState.buddies.content,
                selectedBuddyIds = uiState.selectedBuddyIds,
                isBuddiesLoading = uiState.isBuddiesLoading,
                buddyError = uiState.buddyError,
                onOpen = { showCompanionPicker = true },
                onRefresh = viewModel::loadBuddies,
            )

            val totalFee =
                uiState.dayInfo?.let { info ->
                  uiState.selections.sumOf { sel ->
                    info.spaces
                        .firstOrNull { it.spaceId == sel.spaceId }
                        ?.slots
                        ?.firstOrNull { it.timeId == sel.timeId }
                        ?.orderFee ?: 0.0
                  }
                } ?: 0.0

            SportSubmitBar(
                phone = uiState.phone,
                onPhoneChange = viewModel::updatePhone,
                totalFee = totalFee.takeIf { it > 0 },
                selectedCount = uiState.selections.size,
                isSubmitting = uiState.isSubmitting,
                onSubmit = { clientX, clientY ->
                  viewModel.submitReservation(clientX = clientX, clientY = clientY)
                },
            )
          }
        }
      }

      uiState.clickWordCaptcha?.let { captcha ->
        uiState.captchaImage?.let { image ->
          SportClickWordCaptchaOverlay(
              captcha = captcha,
              image = image,
              points = uiState.captchaPoints,
              isLoading = uiState.isCaptchaLoading,
              error = uiState.captchaError,
              onTap = viewModel::onCaptchaTap,
              onRefresh = viewModel::refreshClickWordCaptcha,
              onDismiss = viewModel::dismissClickWordCaptcha,
          )
        }
      }

      // 场馆支付：cc-pay 收银台 → 隐藏 WebView 自动唤起微信/支付宝（复用校车/电费同款机制）
      // ccpayReady：等 cc-pay 会话后台预热完成 + 用户选定渠道后才渲染，避免收银台页无会话。
      val payCashierUrl = uiState.payCashierUrl
      if (payCashierUrl != null && !uiState.payChannelPending && uiState.ccpayReady) {
        SchemeTriggerWebView(
            cashierUrl = payCashierUrl,
            channel = uiState.payChannel,
            onConsumed = viewModel::clearVenuePay,
        )
      }
      if (uiState.payChannelPending && payCashierUrl != null) {
        SportPayChannelDialog(
            onChooseWx = { viewModel.chooseVenuePayChannel("wx") },
            onChooseAli = { viewModel.chooseVenuePayChannel("ali") },
            onDismiss = viewModel::clearVenuePay,
        )
      }
      // 无 cc-pay 收银台时才退回航财通·校园付扫码
      if (payCashierUrl == null) {
        uiState.payResult?.let { pay ->
          SportPayDialog(
              pay = pay,
              payError = uiState.payError,
              isPaying = uiState.isPaying,
              onDismiss = viewModel::dismissPayResult,
          )
        }
      }

      if (showTimePicker) {
        SportTimePickerScreen(
            dayInfo = uiState.dayInfo,
            selections = uiState.selections,
            selectedDate = uiState.selectedDate,
            dates = uiState.dayInfo?.availableDates.orEmpty(),
            onSelectDate = viewModel::selectDate,
            onToggleSlot = viewModel::toggleSlot,
            onDone = { showTimePicker = false },
        )
      }

      if (showCompanionPicker) {
        SportCompanionPickerScreen(
            buddies = uiState.buddies.content,
            selectedBuddyIds = uiState.selectedBuddyIds,
            addBuddyUid = uiState.addBuddyUid,
            isAddingBuddy = uiState.isAddingBuddy,
            isBuddiesLoading = uiState.isBuddiesLoading,
            buddyError = uiState.buddyError,
            buddyAddTick = uiState.buddyAddTick,
            onToggle = viewModel::toggleBuddy,
            onDelete = viewModel::deleteBuddy,
            onUidChange = viewModel::updateAddBuddyUid,
            onAdd = viewModel::addBuddyByUid,
            onRefresh = viewModel::loadBuddies,
            onDone = { showCompanionPicker = false },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SportCategorySelector(
    purposeTypes: List<CgyyPurposeTypeDto>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
  if (purposeTypes.isEmpty()) return
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
    Text(
        "项目分类",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier =
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      FilterChip(
          selected = selected == null,
          onClick = { onSelect(null) },
          label = { Text("全部") },
      )
      purposeTypes.forEach { type ->
        FilterChip(
            selected = selected == type.key,
            onClick = { onSelect(type.key) },
            label = { Text(type.name) },
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
          Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 12.dp),
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
private fun SportTimeEntry(
    date: String,
    selections: List<CgyyReservationSelectionDto>,
    isLoading: Boolean,
    error: String?,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
  Card(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 4.dp)
              .clickable(enabled = error == null && !isLoading, onClick = onOpen),
  ) {
    when {
      error != null ->
          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("重试") }
          }
      isLoading ->
          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("时段加载中…", style = MaterialTheme.typography.bodySmall)
          }
      else ->
          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("选择时段", style = MaterialTheme.typography.titleSmall)
              Text(
                  if (selections.isEmpty()) date else "$date · 已选 ${selections.size} 个时段",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "进入选择",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
    }
  }
}

/** 二级全屏时段选择页：顶栏 + 日期切换 + 场地时间网格。 */
@Composable
private fun SportTimePickerScreen(
    dayInfo: CgyyDayInfoResponse?,
    selections: List<CgyyReservationSelectionDto>,
    selectedDate: String,
    dates: List<String>,
    onSelectDate: (String) -> Unit,
    onToggleSlot: (Int, Int, Int?) -> Unit,
    onDone: () -> Unit,
) {
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onDone) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            "选择时段",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            sportDateLabel(selectedDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDone) { Text("完成") }
      }
      SportDateSelector(
          dates = dates,
          selectedDate = selectedDate,
          onSelectDate = onSelectDate,
      )
      SportTimeGrid(
          dayInfo = dayInfo,
          selections = selections,
          onToggleSlot = onToggleSlot,
      )
    }
  }
  BackHandlerCompat { onDone() }
}

@Composable
internal fun SportTimeGrid(
    dayInfo: CgyyDayInfoResponse?,
    selections: List<CgyyReservationSelectionDto>,
    onToggleSlot: (Int, Int, Int?) -> Unit,
) {
  if (dayInfo == null) return
  val timeSlots = dayInfo.timeSlots
  val spaces = dayInfo.spaces
  // 表头 + 所有场地行共用一个横向滚动状态，整块联动滑动
  val sharedH = rememberScrollState()

  Column(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(sharedH)) {
      Box(
          modifier =
              Modifier.width(70.dp)
                  .height(36.dp)
                  .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
      ) {
        Text("场地", fontWeight = FontWeight.Bold, fontSize = 12.sp)
      }
      timeSlots.forEach { slot ->
        Box(
            modifier =
                Modifier.width(56.dp)
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
            sharedScroll = sharedH,
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
    sharedScroll: ScrollState,
) {
  Row(modifier = Modifier.fillMaxWidth().horizontalScroll(sharedScroll)) {
    Box(
        modifier =
            Modifier.width(70.dp)
                .height(44.dp)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
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
              .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
      contentAlignment = Alignment.Center,
  ) {
    Text(text, fontSize = 10.sp, color = textColor, textAlign = TextAlign.Center)
  }
}

@Composable
/** 主屏紧凑同伴入口行：点击进入同伴二级页；＋ 快速添加；刷新按钮。 */
private fun SportCompanionEntry(
    buddies: List<CgyyBuddyDto>,
    selectedBuddyIds: Set<Int>,
    isBuddiesLoading: Boolean,
    buddyError: String?,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
) {
  val selected = buddies.filter { it.id in selectedBuddyIds }
  Card(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 4.dp)
              .clickable(onClick = onOpen),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("同伴", style = MaterialTheme.typography.titleSmall)
        Text(
            when {
              isBuddiesLoading && buddies.isEmpty() -> "加载中…"
              selected.isEmpty() -> if (buddies.isEmpty()) "暂无同伴" else "未选择同伴"
              else -> selected.joinToString("、") { it.name.orEmpty() }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
      buddyError?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.5f).padding(end = 4.dp),
        )
      }
      IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
      IconButton(onClick = onOpen) { Icon(Icons.Default.Add, contentDescription = "添加同伴") }
      Icon(
          Icons.Default.ChevronRight,
          contentDescription = "进入",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** 二级全屏同伴选择页：列表勾选 + 删除 + 底部添加。 */
@Composable
private fun SportCompanionPickerScreen(
    buddies: List<CgyyBuddyDto>,
    selectedBuddyIds: Set<Int>,
    addBuddyUid: String,
    isAddingBuddy: Boolean,
    isBuddiesLoading: Boolean,
    buddyError: String?,
    buddyAddTick: Int,
    onToggle: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onUidChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onDone: () -> Unit,
) {
  var showAddDialog by remember { mutableStateOf(false) }
  val lastAddTick = remember { mutableStateOf(buddyAddTick) }
  // 添加成功后自动关闭弹窗
  LaunchedEffect(buddyAddTick) {
    if (buddyAddTick > lastAddTick.value) {
      lastAddTick.value = buddyAddTick
      showAddDialog = false
    }
  }
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onDone) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            "同伴（含本人最多 3 人）",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRefresh) { Text("刷新") }
        TextButton(onClick = onDone) { Text("完成") }
      }
      when {
        isBuddiesLoading && buddies.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
        buddies.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                  "暂无同伴，点击下方按钮添加",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
        else ->
            LazyColumn(modifier = Modifier.weight(1f)) {
              items(buddies, key = { it.id }) { buddy ->
                val selected = buddy.id in selectedBuddyIds
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onToggle(buddy.id) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                  Checkbox(checked = selected, onCheckedChange = { onToggle(buddy.id) })
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${buddy.name.orEmpty()}（${buddy.userUid.orEmpty()}）",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        buddy.buddyTypeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  IconButton(onClick = { onDelete(buddy.id) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }
      }
      buddyError?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
      }
      Button(
          onClick = { showAddDialog = true },
          enabled = selectedBuddyIds.size < 2 && !isAddingBuddy,
          modifier = Modifier.fillMaxWidth().padding(16.dp),
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(if (selectedBuddyIds.size >= 2) "已达人数上限" else "添加同伴")
      }
    }
  }
  if (showAddDialog) {
    SportAddBuddyDialog(
        value = addBuddyUid,
        isAdding = isAddingBuddy,
        onValueChange = onUidChange,
        onConfirm = onAdd,
        onDismiss = { showAddDialog = false },
    )
  }
  BackHandlerCompat { onDone() }
}

/** 添加同伴弹窗：输入北航学号。 */
@Composable
private fun SportAddBuddyDialog(
    value: String,
    isAdding: Boolean,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("添加同伴") },
      text = {
        Column {
          Text(
              "请输入同伴的北航学号（8 位数字）",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
              value = value,
              onValueChange = { v -> onValueChange(v.filter { it.isDigit() }.take(8)) },
              label = { Text("学号") },
              singleLine = true,
              enabled = !isAdding,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(onClick = onConfirm, enabled = value.isNotBlank() && !isAdding) {
          if (isAdding) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
          }
          Text("添加")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss, enabled = !isAdding) { Text("取消") } },
  )
}

@Composable
private fun SportPayDialog(
    pay: CgyyOrderPayResult,
    payError: String?,
    isPaying: Boolean,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("订单支付") },
      text = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          pay.payFee?.let {
            Text("应付金额：¥${formatFee(it)}", style = MaterialTheme.typography.titleMedium)
          }
          val payCode = pay.payCode
          if (payCode != null) {
            val image =
                remember(payCode) {
                  runCatching { decodeCgyyCaptchaImage("data:image/png;base64,$payCode") }
                      .getOrNull()
                }
            if (image != null) {
              Box(
                  modifier =
                      Modifier.size(240.dp)
                          .clip(RoundedCornerShape(8.dp))
                          .background(MaterialTheme.colorScheme.surfaceVariant),
                  contentAlignment = Alignment.Center,
              ) {
                Image(
                    bitmap = remember(image) { cgyyCaptchaImageBitmap(image) },
                    contentDescription = "支付二维码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
              }
            } else {
              Text("二维码解码失败", color = MaterialTheme.colorScheme.error)
            }
          }
          Text(
              "请使用航财通·校园付扫码支付",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          pay.scanTip
              ?.takeIf { it.isNotBlank() }
              ?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
          payError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
          }
          if (isPaying) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("已完成支付") } },
      dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
  )
}

@Composable
private fun SportPayChannelDialog(
    onChooseWx: () -> Unit,
    onChooseAli: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("选择支付方式") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
              "订单已提交，请选择支付渠道：",
              style = MaterialTheme.typography.bodyMedium,
          )
          OutlinedButton(
              onClick = onChooseWx,
              modifier = Modifier.fillMaxWidth().height(48.dp),
          ) {
            Text("微信支付")
          }
          OutlinedButton(
              onClick = onChooseAli,
              modifier = Modifier.fillMaxWidth().height(48.dp),
          ) {
            Text("支付宝支付")
          }
        }
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = onDismiss) { Text("稍后支付") } },
  )
}

@Composable
private fun SportSubmitBar(
    phone: String,
    onPhoneChange: (String) -> Unit,
    totalFee: Double?,
    selectedCount: Int,
    isSubmitting: Boolean,
    onSubmit: (Int, Int) -> Unit,
) {
  var buttonPosition by remember { mutableStateOf<LayoutCoordinates?>(null) }
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
          onClick = {
            val coords = buttonPosition
            val clientX =
                if (coords != null) {
                  (coords.positionInRoot().x + coords.size.width / 2f).toInt()
                } else {
                  0
                }
            val clientY =
                if (coords != null) {
                  (coords.positionInRoot().y + coords.size.height / 2f).toInt()
                } else {
                  0
                }
            onSubmit(clientX, clientY)
          },
          enabled = selectedCount > 0 && phone.isNotBlank() && !isSubmitting,
          modifier =
              Modifier.fillMaxWidth().height(48.dp).onGloballyPositioned { buttonPosition = it },
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

@Composable
internal fun SportClickWordCaptchaOverlay(
    captcha: CgyyClickWordCaptchaDto,
    image: CgyyCaptchaImageData,
    points: List<CgyySportCaptchaPoint>,
    isLoading: Boolean,
    error: String?,
    onTap: (Int, Int, Int, Int) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
  Box(
      modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(enabled = false) {},
      contentAlignment = Alignment.Center,
  ) {
    Card(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
      Column(
          modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
            "请按顺序点击：${captcha.wordList.joinToString("、")}",
            style = MaterialTheme.typography.titleSmall,
        )
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = 320.dp)
                    .aspectRatio(image.width.toFloat() / maxOf(1, image.height))
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(image) {
                      detectTapGestures { offset ->
                        onTap(offset.x.toInt(), offset.y.toInt(), size.width, size.height)
                      }
                    },
        ) {
          Image(
              bitmap = remember(image) { cgyyCaptchaImageBitmap(image) },
              contentDescription = "点选验证码",
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize(),
          )
          points.forEachIndexed { index, p ->
            Box(
                modifier =
                    Modifier.offset { IntOffset(p.x - 12, p.y - 12) }
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
              Text(
                  "${index + 1}",
                  color = MaterialTheme.colorScheme.onPrimary,
                  fontSize = 12.sp,
              )
            }
          }
        }
        error?.let {
          Text(
              it,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
          )
        }
        if (isLoading) {
          Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          TextButton(onClick = onDismiss) { Text("取消") }
          TextButton(onClick = onRefresh) { Text("刷新") }
        }
      }
    }
  }
  BackHandlerCompat { onDismiss() }
}
