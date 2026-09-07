package cn.edu.ubaa.ui.screens.sport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.api.storage.PriorityDraft
import cn.edu.ubaa.model.dto.CgyyBuddyDto
import cn.edu.ubaa.model.dto.CgyyReservationSelectionDto
import cn.edu.ubaa.model.dto.CgyyVenueSiteDto
import cn.edu.ubaa.ui.common.util.BackHandlerCompat
import cn.edu.ubaa.ui.component.SchemeTriggerWebView

/** 抢场界面：草稿列表 / 预选编辑器 / 抢场监控 三态。 */
@Composable
fun SportGrabScreen(
    viewModel: SportGrabViewModel,
    currentSite: CgyyVenueSiteDto?,
    onExit: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()

  BackHandlerCompat { if (uiState.grabActive) viewModel.stopGrab() else onExit() }

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = { if (uiState.grabActive) viewModel.stopGrab() else onExit() }) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            if (uiState.grabActive) "抢场中" else if (uiState.editingDraft != null) "预选编辑" else "抢场预选",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
      }
      when {
        uiState.grabActive -> GrabMonitorContent(uiState, viewModel)
        uiState.editingDraft != null -> PreSelectEditorContent(uiState, viewModel)
        else -> DraftListContent(uiState, viewModel, currentSite)
      }
    }

    // 抢场验证码浮层（全屏 Box 内，避免 Column 挤压）
    val grabCaptcha = uiState.captcha
    val grabCaptchaImage = uiState.captchaImage
    if (uiState.grabActive && grabCaptcha != null && grabCaptchaImage != null) {
      SportClickWordCaptchaOverlay(
          captcha = grabCaptcha,
          image = grabCaptchaImage,
          points = uiState.captchaPoints,
          isLoading = uiState.isCaptchaLoading,
          error = uiState.captchaError,
          onTap = viewModel::onCaptchaTap,
          onRefresh = viewModel::refreshCaptcha,
          onDismiss = viewModel::dismissCaptcha,
      )
    }

    // 抢到订单后的支付：cc-pay 收银台 → 隐藏 WebView 自动唤起微信/支付宝（复用校车/电费同款机制）
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
  }
}

// ===================== 草稿列表 =====================

@Composable
private fun DraftListContent(
    uiState: SportGrabUiState,
    viewModel: SportGrabViewModel,
    currentSite: CgyyVenueSiteDto?,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
          "保存的预选（第4天）",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = { if (currentSite != null) viewModel.newDraft(currentSite) }) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text("新建预选")
      }
    }
    if (currentSite == null) {
      Text(
          "请先在场馆预约页选择一个场地，再进入抢场预选",
          modifier = Modifier.padding(12.dp),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
    if (uiState.drafts.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "暂无预选草稿",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(uiState.drafts) { draft -> DraftCard(draft, viewModel) }
      }
    }
  }
}

@Composable
private fun DraftCard(draft: PriorityDraft, viewModel: SportGrabViewModel) {
  // 记录「开始抢场」按钮位置：下单 orderPin 需要真实点击坐标（服务端反机器人校验，假坐标会静默拒绝 data:null）
  var startButtonPosition by remember { mutableStateOf<LayoutCoordinates?>(null) }
  Card(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(draft.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(
          "目标日 ${draft.date} · ${draft.options.size} 个意向 · ${draft.venueSiteName}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (draft.buddyIds.isNotEmpty()) {
        Text(
            "同伴 × ${draft.buddyIds.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
      }
      Text(
          draft.options.mapIndexed { i, o -> "${i + 1}.${o.displayLabel}" }.joinToString("  "),
          style = MaterialTheme.typography.bodySmall,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
      )
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = { viewModel.deleteDraft(draft.id) }) {
          Icon(
              Icons.Default.Delete,
              contentDescription = "删除",
              tint = MaterialTheme.colorScheme.error,
          )
        }
        TextButton(onClick = { viewModel.editDraft(draft.id) }) { Text("编辑") }
        // 开始抢场：与手动下单一致，用按钮真实中心坐标生成 orderPin，否则服务端静默拒绝（data:null）
        Button(
            onClick = {
              val coords = startButtonPosition
              val clientX =
                  if (coords != null) (coords.positionInRoot().x + coords.size.width / 2f).toInt()
                  else 0
              val clientY =
                  if (coords != null) (coords.positionInRoot().y + coords.size.height / 2f).toInt()
                  else 0
              viewModel.startGrab(draft, clientX, clientY)
            },
            modifier = Modifier.onGloballyPositioned { startButtonPosition = it },
        ) {
          Text("开始抢场")
        }
      }
    }
  }
}

// ===================== 预选编辑器 =====================

@Composable
private fun PreSelectEditorContent(
    uiState: SportGrabUiState,
    viewModel: SportGrabViewModel,
) {
  val draft = uiState.editingDraft ?: return
  Column(modifier = Modifier.fillMaxSize()) {
    Text(
        "目标日：${draft.date}（第4天，结构未开放）",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
    Text(
        "下方为场地时段参考（全部可点选，含今日已满时段——目标日会重新放名额）",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )

    val ref = uiState.referenceDayInfo
    when {
      uiState.isReferenceLoading && ref == null ->
          Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
      ref == null ->
          Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(uiState.referenceError ?: "参考网格加载失败")
              Spacer(Modifier.height(8.dp))
              Button(onClick = viewModel::retryLoadReference) { Text("重试") }
            }
          }
      else -> {
        // 预选网格：目标日结构未开放，抹平参考日状态为全部可抢
        val preselectGrid = viewModel.buildPreselectGrid() ?: ref
        val highlight =
            draft.options.mapNotNull { opt ->
              preselectGrid.timeSlots
                  .firstOrNull { it.beginTime == opt.timeLabel }
                  ?.let { slot ->
                    CgyyReservationSelectionDto(spaceId = opt.spaceId, timeId = slot.id)
                  }
            }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          SportTimeGrid(
              dayInfo = preselectGrid,
              selections = highlight,
              onToggleSlot = { spaceId, timeId, _ ->
                val label = preselectGrid.timeSlots.firstOrNull { it.id == timeId }?.beginTime
                if (label != null) {
                  val spaceName =
                      preselectGrid.spaces.firstOrNull { it.spaceId == spaceId }?.spaceName ?: ""
                  viewModel.toggleOption(spaceId, spaceName, label)
                }
              },
          )
        }
      }
    }

    // 意向列表（优先级排序）
    if (draft.options.isNotEmpty()) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text("当前意向（${draft.options.size}）", style = MaterialTheme.typography.labelMedium)
        draft.options.forEachIndexed { index, opt ->
          Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                "${index + 1}",
                modifier = Modifier.width(24.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }

    // 同伴选择
    GrabCompanionSection(
        buddies = uiState.grabBuddies,
        selectedBuddyIds = draft.buddyIds.toSet(),
        isBuddiesLoading = uiState.isGrabBuddiesLoading,
        buddyError = uiState.grabBuddyError,
        addUid = uiState.grabBuddyAddUid,
        isAddingBuddy = uiState.isAddingGrabBuddy,
        buddyAddTick = uiState.buddyAddTick,
        onToggle = viewModel::toggleGrabBuddy,
        onDelete = viewModel::deleteGrabBuddy,
        onUidChange = viewModel::updateGrabBuddyUid,
        onAdd = viewModel::addGrabBuddyByUid,
        onRefresh = { viewModel.loadGrabBuddies(true) },
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
          value = draft.phone,
          onValueChange = viewModel::updateDraftPhone,
          label = { Text("手机号") },
          singleLine = true,
          modifier = Modifier.weight(1f),
      )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedButton(onClick = viewModel::clearEditing, modifier = Modifier.weight(1f)) {
        Text("取消")
      }
      OutlinedButton(onClick = viewModel::clearOptions, modifier = Modifier.weight(1f)) {
        Text("清空")
      }
      Button(onClick = viewModel::saveDraft, modifier = Modifier.weight(1f)) { Text("保存草稿") }
    }
    uiState.message?.let {
      Text(
          it,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

// ===================== 抢场监控 =====================

/** 抢场预选编辑器内的同伴选择区：勾选已有同伴 + 添加（学号）+ 刷新 + 删除。 */
@Composable
private fun GrabCompanionSection(
    buddies: List<CgyyBuddyDto>,
    selectedBuddyIds: Set<Int>,
    isBuddiesLoading: Boolean,
    buddyError: String?,
    addUid: String,
    isAddingBuddy: Boolean,
    buddyAddTick: Int,
    onToggle: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onUidChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
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
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text("同伴（含本人最多 3 人）", style = MaterialTheme.typography.labelMedium)
      Row {
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
        IconButton(onClick = { showAddDialog = true }) {
          Icon(Icons.Default.Add, contentDescription = "添加同伴")
        }
      }
    }
    when {
      isBuddiesLoading && buddies.isEmpty() ->
          Text("加载中…", style = MaterialTheme.typography.bodySmall)
      buddyError != null ->
          Text(
              buddyError,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
          )
      buddies.isEmpty() -> Text("暂无同伴，点右上角 ＋ 添加（输学号）", style = MaterialTheme.typography.bodySmall)
      else ->
          buddies.forEach { buddy ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                  checked = buddy.id in selectedBuddyIds,
                  onCheckedChange = { onToggle(buddy.id) },
              )
              Text(
                  buddy.name.orEmpty().ifBlank { buddy.userUid.orEmpty() },
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f),
              )
              Text(
                  buddy.buddyTypeLabel,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(end = 4.dp),
              )
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
    if (selectedBuddyIds.isNotEmpty()) {
      val names =
          buddies
              .filter { it.id in selectedBuddyIds }
              .joinToString("、") { it.name.orEmpty().ifBlank { it.userUid.orEmpty() } }
      Text(
          "已选：$names",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
      )
    }
  }

  if (showAddDialog) {
    AlertDialog(
        onDismissRequest = { showAddDialog = false },
        title = { Text("添加同伴") },
        text = {
          Column {
            OutlinedTextField(
                value = addUid,
                onValueChange = onUidChange,
                label = { Text("学号") },
                singleLine = true,
            )
            Text("填写同伴的学号（北航校园卡号）", style = MaterialTheme.typography.bodySmall)
          }
        },
        confirmButton = {
          TextButton(
              onClick = { if (addUid.isNotBlank()) onAdd() },
              enabled = !isAddingBuddy,
          ) {
            Text("添加")
          }
        },
        dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } },
    )
  }
}

@Composable
private fun GrabMonitorContent(uiState: SportGrabUiState, viewModel: SportGrabViewModel) {
  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
    uiState.result?.let {
      Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
              "🎉 抢场成功！",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary,
          )
          Text("订单号 $it", style = MaterialTheme.typography.bodyMedium)
          if (uiState.isPaying) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(
                  modifier = Modifier.width(16.dp).height(16.dp),
                  strokeWidth = 2.dp,
              )
              Spacer(Modifier.width(6.dp))
              Text("正在发起支付…", style = MaterialTheme.typography.bodySmall)
            }
          }
          uiState.payError?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
          }
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::retryPay) { Text("去支付") }
            OutlinedButton(onClick = viewModel::exitGrab) { Text("完成") }
          }
        }
      }
    }
    uiState.message?.let {
      Text(
          it,
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          color =
              if (it.startsWith("抢场成功")) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
    if (!uiState.windowOpened && uiState.result == null) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.width(20.dp).height(20.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text("检测「${uiState.grabDraft?.date}」开抢中…", style = MaterialTheme.typography.bodySmall)
      }
    }
    if (uiState.grabDraft != null) {
      Text(
          "目标日 ${uiState.grabDraft!!.date} · ${uiState.grabDraft!!.venueSiteName}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 4.dp),
      )
    }
    if (uiState.optionStatuses.isNotEmpty()) {
      Text("意向状态（按优先级）：", style = MaterialTheme.typography.labelMedium)
      uiState.optionStatuses.forEach { st ->
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        if (st.isActive)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              "${st.index + 1}",
              modifier = Modifier.width(24.dp),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
          )
          Text(
              st.displayLabel,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f),
          )
          Text(
              when {
                st.isActive -> "尝试中…"
                st.isReservable && !st.isTaken -> "可抢 ✓"
                st.isTaken -> "被抢 ✗"
                st.isUnavailable -> "未开放"
                st.failCount >= 2 -> "失败"
                else -> "—"
              },
              style = MaterialTheme.typography.bodySmall,
              color =
                  when {
                    st.isActive -> MaterialTheme.colorScheme.primary
                    st.isReservable && !st.isTaken -> MaterialTheme.colorScheme.primary
                    st.isTaken -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                  },
          )
        }
      }
    }
    Spacer(Modifier.weight(1f))
    if (uiState.result == null) {
      OutlinedButton(
          onClick = viewModel::stopGrab,
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      ) {
        Text("停止抢场")
      }
    }
  }
}
