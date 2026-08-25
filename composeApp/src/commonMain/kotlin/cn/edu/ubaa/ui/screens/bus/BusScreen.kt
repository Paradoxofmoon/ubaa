package cn.edu.ubaa.ui.screens.bus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.api.local.decodeCgyyCaptchaImage
import cn.edu.ubaa.ui.component.SchemeTriggerWebView
import cn.edu.ubaa.ui.screens.sport.cgyyCaptchaImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 智慧校车订票原生屏：日期/方向/车次余票 → 车票详情 → 验证码 → 下单跳 ccpay。 */
@Composable
fun BusScreen(
    viewModel: BusViewModel,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  var showCaptchaDialog by remember { mutableStateOf(false) }

  // 支付：复用校园卡/电费同款隐藏 WebView，按用户选择的渠道自动唤起对应支付 App
  // ccpayReady：等 cc-pay 会话后台预热完成 + 用户选定渠道后才渲染收银台。
  val cashierUrl = uiState.pendingCashierUrl
  if (cashierUrl != null && !uiState.payChannelPending && uiState.ccpayReady) {
    SchemeTriggerWebView(
        cashierUrl = cashierUrl,
        channel = uiState.pendingChannel,
        onDiagnose = {},
        onConsumed = viewModel::clearPendingPay,
    )
  }
  LaunchedEffect(uiState.pendingCashierUrl, uiState.payMessage) {
    if (uiState.pendingCashierUrl != null || uiState.payMessage != null) {
      showCaptchaDialog = false
    }
  }

  val initialError = uiState.initialError
  Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
    ) {
      when {
        uiState.isInitialLoading && uiState.dates.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
        initialError != null && uiState.dates.isEmpty() ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                  initialError,
                  color = MaterialTheme.colorScheme.error,
                  textAlign = TextAlign.Center,
              )
              Spacer(Modifier.height(12.dp))
              Button(onClick = { viewModel.loadInitialData(forceRefresh = true) }) { Text("重试") }
            }
        else -> {
          uiState.sessionUser?.let { user ->
            if (user.name.isNotBlank()) {
              Text(
                  "你好，${user.name}${user.tempNumber.ifBlank { "" }.let { if (it.isBlank()) "" else "（$it）" }}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(top = 8.dp),
              )
            }
          }
          DirectionSelector(
              origin = uiState.origin,
              terminal = uiState.terminal,
              onSwap = viewModel::swapDirection,
          )
          DateSelector(
              dates = uiState.dates,
              selectedDate = uiState.selectedDate,
              onSelectDate = viewModel::selectDate,
          )
          Spacer(Modifier.height(8.dp))
          Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("车次", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = viewModel::searchShifts) {
              Icon(
                  Icons.Default.Refresh,
                  contentDescription = "刷新",
                  modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text("刷新")
            }
          }
          val shiftsError = uiState.shiftsError
          when {
            uiState.isSearchingShifts && uiState.shifts.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                  CircularProgressIndicator()
                }
            shiftsError != null && uiState.shifts.isEmpty() ->
                Text(
                    shiftsError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            else ->
                ShiftList(
                    shifts = uiState.shifts,
                    selected = uiState.selectedShift,
                    onSelect = viewModel::selectShift,
                )
          }

          uiState.ticketDetail?.let { detail ->
            TicketDetailCard(
                detail = detail,
                isBuying = uiState.isBuying,
                buyError = uiState.buyError,
                payMessage = uiState.payMessage,
                onBuyClick = { showCaptchaDialog = true },
            )
          }
          uiState.actionMessage?.let { msg ->
            Text(
                msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
          }
          Spacer(Modifier.height(24.dp))
        }
      }
    }
  }

  if (showCaptchaDialog) {
    CaptchaDialog(
        image = uiState.captchaImage,
        isLoading = uiState.isCaptchaLoading,
        input = uiState.captchaInput,
        isBuying = uiState.isBuying,
        onInputChange = viewModel::setCaptchaInput,
        onRefresh = viewModel::refreshCaptcha,
        onConfirm = { viewModel.buyTicket() },
        onDismiss = { if (!uiState.isBuying) showCaptchaDialog = false },
    )
  }

  // 下单成功 → 先选支付方式（微信/支付宝），选定后再拉起 ccpay 收银台
  if (uiState.payChannelPending && uiState.pendingCashierUrl != null) {
    PayChannelDialog(
        onChooseWx = { viewModel.choosePayChannel("wx") },
        onChooseAli = { viewModel.choosePayChannel("ali") },
        onDismiss = viewModel::dismissPayChannel,
    )
  }
}

@Composable
private fun DirectionSelector(
    origin: String,
    terminal: String,
    onSwap: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          origin,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.weight(1f),
          textAlign = TextAlign.Center,
      )
      IconButton(onClick = onSwap) {
        Icon(
            Icons.Default.SwapHoriz,
            contentDescription = "交换出发/到达",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      Text(
          terminal,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.weight(1f),
          textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun DateSelector(
    dates: List<String>,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    dates.forEach { date ->
      FilterChip(
          selected = date == selectedDate,
          onClick = { onSelectDate(date) },
          label = { Text(busDateLabel(date)) },
      )
    }
  }
}

@Composable
private fun ShiftList(
    shifts: List<cn.edu.ubaa.model.dto.BusShiftDto>,
    selected: cn.edu.ubaa.model.dto.BusShiftDto?,
    onSelect: (cn.edu.ubaa.model.dto.BusShiftDto) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    shifts.forEach { shift ->
      val isSelected = selected?.shifts_number == shift.shifts_number
      Card(
          modifier =
              Modifier.fillMaxWidth()
                  .clickable { onSelect(shift) }
                  .then(
                      if (isSelected) Modifier.padding(1.dp)
                      // 选中态边框
                      else Modifier
                  ),
          colors =
              CardDefaults.cardColors(
                  containerColor =
                      if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant,
              ),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
                "${shift.depart_time} → ${shift.arrive_time}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${shift.line_name} · 车次${shift.shifts_number}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
              when {
                shift.ticketNum <= 0 -> "无票"
                else -> "余票 ${shift.ticketNum}"
              },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color =
                  if (shift.ticketNum <= 0) MaterialTheme.colorScheme.error
                  else MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
private fun TicketDetailCard(
    detail: cn.edu.ubaa.model.dto.BusTicketDetailDto,
    isBuying: Boolean,
    buyError: String?,
    payMessage: String?,
    onBuyClick: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text("车票信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(8.dp))
      InfoRow("日期", "${detail.shiftsDate} ${detail.departTime} ${detail.weekday}")
      InfoRow("类别", detail.category.ifBlank { "-" })
      InfoRow("余票", if (detail.remainingTickets >= 0) "${detail.remainingTickets}张" else "-")
      InfoRow("票价", if (detail.price.isNotBlank()) "${detail.price}元" else "-")
      Spacer(Modifier.height(10.dp))
      Button(
          onClick = onBuyClick,
          enabled = !isBuying,
          modifier = Modifier.fillMaxWidth(),
      ) {
        if (isBuying) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
        }
        Text("订票")
      }
      buyError?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
      }
      payMessage?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            it,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(64.dp),
    )
    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
  }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun CaptchaDialog(
    image: ByteArray?,
    isLoading: Boolean,
    input: String,
    isBuying: Boolean,
    onInputChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  val bitmap =
      remember(image) {
        image?.let { bytes ->
          val dataUri = "data:image/jpeg;base64,${Base64.encode(bytes)}"
          runCatching { cgyyCaptchaImageBitmap(decodeCgyyCaptchaImage(dataUri)) }.getOrNull()
        }
      }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("请输入验证码") },
      text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          when {
            isLoading && bitmap == null ->
                Box(Modifier.height(60.dp), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            bitmap != null ->
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = "验证码",
                    modifier =
                        Modifier.size(width = 140.dp, height = 56.dp)
                            .clickable(onClick = onRefresh),
                    contentScale = ContentScale.Fit,
                )
            else ->
                Text(
                    "验证码加载失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
          }
          TextButton(onClick = onRefresh, enabled = !isBuying) { Text("看不清？换一张") }
          OutlinedTextField(
              value = input,
              onValueChange = { v ->
                if (v.length <= 8) onInputChange(v.filter { it.isLetterOrDigit() })
              },
              label = { Text("验证码") },
              singleLine = true,
              enabled = !isBuying,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(onClick = onConfirm, enabled = !isBuying) {
          if (isBuying) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
          }
          Text("去支付")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss, enabled = !isBuying) { Text("取消") } },
  )
}

/** 支付方式选择弹窗：微信 / 支付宝（选定后由隐藏 WebView 自动点击对应渠道）。 */
@Composable
private fun PayChannelDialog(
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

private fun busDateLabel(date: String): String {
  val parts = date.split("-")
  if (parts.size != 3) return date
  val m = parts[1].toIntOrNull() ?: return date
  val d = parts[2].toIntOrNull() ?: return date
  return "${m}月${d}日"
}
