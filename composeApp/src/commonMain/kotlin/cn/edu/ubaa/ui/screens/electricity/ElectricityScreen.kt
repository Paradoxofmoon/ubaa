package cn.edu.ubaa.ui.screens.electricity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.component.SchemeTriggerWebView

/** 电费购电原生 UI（无状态）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityScreen(
    uiState: ElectricityUiState,
    onCampusSelect: (String) -> Unit,
    onBuildingSelect: (String) -> Unit,
    onFloorSelect: (String) -> Unit,
    onRoomSelect: (String) -> Unit,
    onMeterSelect: (ElectricityMeter) -> Unit,
    onUseMeterForPay: () -> Unit,
    onMeterNumberChange: (String) -> Unit,
    onHistorySelect: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onQueryMeter: () -> Unit,
    onPowerChange: (String) -> Unit,
    onSubmitPay: (ElectricityPayWay) -> Unit,
    onContinuePendingPay: (ElectricityPayWay) -> Unit,
    onCancelPendingPay: () -> Unit,
    onClearPendingPay: () -> Unit,
    onRetryTree: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableIntStateOf(0) }

  val cashierUrl = uiState.pendingCashierUrl
  val channel = uiState.pendingChannel
  var payStatus by remember(cashierUrl) { mutableStateOf<String?>(if (cashierUrl != null) "正在拉起支付..." else null) }
  // 与校园卡一致的隐藏 WebView：加载真实收银台页并注入 JS 自动点支付渠道，唤起支付 App。
  if (cashierUrl != null) {
    SchemeTriggerWebView(
        cashierUrl = cashierUrl,
        channel = channel ?: "wx",
        onDiagnose = { msg -> payStatus = msg },
        onConsumed = onClearPendingPay,
    )
  }

  Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      TabRow(selectedTabIndex = selectedTab) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("电表查询") })
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("电费缴费") })
      }

      when (selectedTab) {
        0 ->
            QueryPanel(
                uiState = uiState,
                onCampusSelect = onCampusSelect,
                onBuildingSelect = onBuildingSelect,
                onFloorSelect = onFloorSelect,
                onRoomSelect = onRoomSelect,
                onMeterSelect = onMeterSelect,
                onUseMeterForPay = {
                  selectedTab = 1
                  onUseMeterForPay()
                },
                onRetry = onRetryTree,
            )
        1 ->
            PayPanel(
                uiState = uiState,
                onMeterNumberChange = onMeterNumberChange,
                onHistorySelect = onHistorySelect,
                onHistoryRemove = onHistoryRemove,
                onQueryMeter = onQueryMeter,
                onPowerChange = onPowerChange,
                onSubmitPay = onSubmitPay,
                onContinuePendingPay = onContinuePendingPay,
                onCancelPendingPay = onCancelPendingPay,
            )
      }
    }

    // 支付唤起状态/诊断提示
    payStatus?.let { status ->
      Card(
          modifier = Modifier
              .align(Alignment.TopCenter)
              .fillMaxWidth()
              .padding(top = 16.dp, start = 12.dp, end = 12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
      ) {
        Text(
            text = status,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }
  }
}

// ===== 查询 tab =====

@Composable
private fun QueryPanel(
    uiState: ElectricityUiState,
    onCampusSelect: (String) -> Unit,
    onBuildingSelect: (String) -> Unit,
    onFloorSelect: (String) -> Unit,
    onRoomSelect: (String) -> Unit,
    onMeterSelect: (ElectricityMeter) -> Unit,
    onUseMeterForPay: () -> Unit,
    onRetry: () -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("用电查询", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "依次选择校区、楼宇、楼层、房间与电表，定位购电表号。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when {
      uiState.isLoadingTree -> {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
      }
      uiState.error != null && uiState.meters.isEmpty() -> {
        Text(
            uiState.error ?: "数据加载失败",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRetry) { Text("重试") }
      }
      else -> {
        CascadeDropdown(
            label = "校区",
            options = uiState.campuses,
            selected = uiState.selectedCampus,
            onSelect = onCampusSelect,
        )
        CascadeDropdown(
            label = "楼宇",
            options = uiState.buildings,
            selected = uiState.selectedBuilding,
            enabled = uiState.selectedCampus != null,
            onSelect = onBuildingSelect,
        )
        CascadeDropdown(
            label = "楼层",
            options = uiState.floors,
            selected = uiState.selectedFloor,
            enabled = uiState.selectedBuilding != null,
            onSelect = onFloorSelect,
        )
        CascadeDropdown(
            label = "房间",
            options = uiState.rooms,
            selected = uiState.selectedRoom,
            enabled = uiState.selectedFloor != null,
            onSelect = onRoomSelect,
        )
        CascadeDropdown(
            label = "电表",
            options = uiState.meterOptions,
            selected = uiState.selectedMeter,
            enabled = uiState.selectedRoom != null,
            optionLabel = { "地址: ${it.address} (序列号: ${it.meterNo})" },
            onSelect = onMeterSelect,
        )

        uiState.selectedMeter?.let { meter ->
          OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
              Text(
                  "购电表号：${meter.identityNo}",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
              )
              Spacer(Modifier.height(4.dp))
              Text("地址：${meter.address}", style = MaterialTheme.typography.bodyMedium)
              Spacer(Modifier.height(12.dp))
              Button(onClick = onUseMeterForPay, modifier = Modifier.fillMaxWidth()) {
                Text("用此表号去缴费")
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> CascadeDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    enabled: Boolean = true,
    optionLabel: (T) -> String = { it.toString() },
    onSelect: (T) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    OutlinedButton(
        onClick = { expanded = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            selected?.let(optionLabel) ?: "请选择",
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
        )
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { option ->
        DropdownMenuItem(
            text = { Text(optionLabel(option)) },
            onClick = {
              onSelect(option)
              expanded = false
            },
        )
      }
    }
  }
}

// ===== 缴费 tab =====

@Composable
private fun PayPanel(
    uiState: ElectricityUiState,
    onMeterNumberChange: (String) -> Unit,
    onHistorySelect: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onQueryMeter: () -> Unit,
    onPowerChange: (String) -> Unit,
    onSubmitPay: (ElectricityPayWay) -> Unit,
    onContinuePendingPay: (ElectricityPayWay) -> Unit,
    onCancelPendingPay: () -> Unit,
) {
  var selectedPayWay by remember { mutableStateOf<ElectricityPayWay?>(null) }
  Column(
      modifier =
          Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("电费缴费", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "输入购电表号查询余额，再输入购电量完成下单。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    uiState.error?.let { error ->
      Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }

    OutlinedTextField(
        value = uiState.meterNumber,
        onValueChange = onMeterNumberChange,
        label = { Text("购电表号") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    if (uiState.meterHistory.isNotEmpty()) {
      Text(
          "历史表号",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      uiState.meterHistory.forEach { num ->
        AssistChip(
            onClick = { onHistorySelect(num) },
            label = { Text(num) },
            leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = {
              Icon(
                  Icons.Default.Close,
                  contentDescription = "删除",
                  modifier =
                      Modifier.size(16.dp).clickable { onHistoryRemove(num) },
              )
            },
            modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    Button(
        onClick = onQueryMeter,
        enabled = uiState.meterNumber.isNotBlank() && !uiState.isLoadingMeter,
        modifier = Modifier.fillMaxWidth(),
    ) {
      if (uiState.isLoadingMeter) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
      } else {
        Text("查询电表")
      }
    }

    val info = uiState.meterInfo
    if (info != null) {
      MeterInfoCard(info)

      if (uiState.hasPendingOrder) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
          Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
                "存在未完成订单",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text("流水号：${info.serial ?: ""}", style = MaterialTheme.typography.bodyMedium)
            PayWaySelector(
                payWays = uiState.payWays,
                selectedPayWay = selectedPayWay,
                onSelect = { selectedPayWay = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                  onClick = { selectedPayWay?.let(onContinuePendingPay) },
                  enabled = selectedPayWay != null && !uiState.isSubmitting,
                  modifier = Modifier.weight(1f),
              ) {
                Text("继续支付")
              }
              OutlinedButton(onClick = onCancelPendingPay, modifier = Modifier.weight(1f)) {
                Text("删除订单")
              }
            }
          }
        }
      } else {
        OutlinedTextField(
            value = uiState.power,
            onValueChange = onPowerChange,
            label = { Text("购电量（度）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "购电量请输入整数，系统会自动按表计倍率调整。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (uiState.computedPower != null && uiState.computedMoney != null) {
          Text(
              "下发电量 ${uiState.computedPower} 度 · 支付金额 ¥${"%.2f".format(uiState.computedMoney)}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
          )
        }

        PayWaySelector(
            payWays = uiState.payWays,
            selectedPayWay = selectedPayWay,
            onSelect = { selectedPayWay = it },
        )

        Button(
            onClick = { selectedPayWay?.let(onSubmitPay) },
            enabled = !uiState.isSubmitting && (uiState.computedPower ?: 0) >= 1 && selectedPayWay != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
          if (uiState.isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          } else {
            Text("确认支付")
          }
        }
      }
    }
  }
}

@Composable
private fun MeterInfoCard(info: ElectricityMeterInfo) {
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text("电表信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      info.address?.let { Text("用电地址：$it", style = MaterialTheme.typography.bodyMedium) }
      Text(
          "剩余电量：${info.remain} 度",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
      )
      Text("电价：${info.price} 元/度", style = MaterialTheme.typography.bodyMedium)
      Text(
          "读数时间：${info.readingTime ?: "未知"}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** 支付渠道选择器，与校园卡一致(微信/支付宝)。 */
@Composable
private fun PayWaySelector(
    payWays: List<ElectricityPayWay>,
    selectedPayWay: ElectricityPayWay?,
    onSelect: (ElectricityPayWay) -> Unit,
) {
  if (payWays.isEmpty()) return
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        "选择支付方式",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    payWays.forEach { way ->
      val selected = way.channel == selectedPayWay?.channel
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .clip(MaterialTheme.shapes.small)
              .background(
                  if (selected) MaterialTheme.colorScheme.secondaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant
              )
              .clickable { onSelect(way) }
              .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(way.text, style = MaterialTheme.typography.bodyLarge)
        if (selected) {
          Icon(
              Icons.Default.CheckCircle,
              contentDescription = "已选择",
              tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}
