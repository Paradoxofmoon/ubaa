package cn.edu.ubaa.ui.screens.libbook

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.edu.ubaa.model.dto.LibBookSeatDto
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import ubaa.composeapp.generated.resources.Res
import ubaa.composeapp.generated.resources.area_117
import ubaa.composeapp.generated.resources.area_16
import ubaa.composeapp.generated.resources.area_18
import ubaa.composeapp.generated.resources.area_19
import ubaa.composeapp.generated.resources.area_20
import ubaa.composeapp.generated.resources.area_21
import ubaa.composeapp.generated.resources.area_22
import ubaa.composeapp.generated.resources.area_23
import ubaa.composeapp.generated.resources.area_24
import ubaa.composeapp.generated.resources.area_25
import ubaa.composeapp.generated.resources.area_26
import ubaa.composeapp.generated.resources.area_27
import ubaa.composeapp.generated.resources.area_28
import ubaa.composeapp.generated.resources.area_29
import ubaa.composeapp.generated.resources.area_52
import ubaa.composeapp.generated.resources.area_53
import ubaa.composeapp.generated.resources.area_6
import ubaa.composeapp.generated.resources.area_63
import ubaa.composeapp.generated.resources.area_64
import ubaa.composeapp.generated.resources.area_65
import ubaa.composeapp.generated.resources.area_67
import ubaa.composeapp.generated.resources.area_68
import ubaa.composeapp.generated.resources.area_69
import ubaa.composeapp.generated.resources.area_71
import ubaa.composeapp.generated.resources.area_72
import ubaa.composeapp.generated.resources.area_73
import ubaa.composeapp.generated.resources.area_8
import ubaa.composeapp.generated.resources.area_82
import ubaa.composeapp.generated.resources.area_83

@Composable
fun LibBookReserveScreen(viewModel: LibBookViewModel, onSubmitSuccess: () -> Unit) {
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  var showMapDialog by remember { mutableStateOf(false) }
  val selectedMapResource = libBookAreaMapResource(uiState.selectedAreaId)

  LaunchedEffect(Unit) { viewModel.ensureInitialLoaded() }
  LaunchedEffect(uiState.actionMessage) {
    uiState.actionMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.clearActionMessage()
      if (message.contains("成功")) onSubmitSuccess()
    }
  }

  if (showMapDialog && selectedMapResource != null) {
    LibBookAreaMapDialog(
        areaName = uiState.selectedArea?.name.orEmpty(),
        mapResource = selectedMapResource,
        onDismiss = { showMapDialog = false },
    )
  }

  Scaffold(
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
      snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
      when {
        uiState.isInitialLoading && uiState.libraries.isEmpty() -> LoadingBox("正在加载图书馆楼馆...")
        uiState.initialError != null && uiState.libraries.isEmpty() ->
            LibBookErrorState(
                message = uiState.initialError,
                onRetry = viewModel::loadLibraries,
                modifier = Modifier.fillMaxSize(),
            )
        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              item {
                SectionTitle(
                    title = "楼馆",
                    trailing = {
                      IconButton(onClick = viewModel::refreshReserveData) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                      }
                    },
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  items(uiState.libraries) { library ->
                    FilterChip(
                        selected = library.id == uiState.selectedLibraryId,
                        onClick = { viewModel.selectLibrary(library.id) },
                        label = {
                          Text(
                              "${library.name} ${library.freeNum}/${library.totalNum}",
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis,
                          )
                        },
                    )
                  }
                }
              }

              uiState.selectedLibrary
                  ?.storeys
                  ?.takeIf { it.isNotEmpty() }
                  ?.let { storeys ->
                    item {
                      SectionTitle("楼层")
                      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(storeys) { storey ->
                          FilterChip(
                              selected = storey.id == uiState.selectedStoreyId,
                              onClick = { viewModel.selectStorey(storey.id) },
                              label = {
                                Text("${storey.name} ${storey.freeNum}/${storey.totalNum}")
                              },
                          )
                        }
                      }
                    }
                  }

              item {
                SectionTitle("分区")
                when {
                  uiState.isAreasLoading -> InlineLoading("正在加载分区...")
                  uiState.areasError != null ->
                      LibBookInlineError(
                          uiState.areasError,
                          onRetry = viewModel::refreshReserveData,
                      )
                  uiState.areas.isEmpty() ->
                      Text("当前楼层暂无可预约分区", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  else ->
                      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(uiState.areas) { area ->
                          FilterChip(
                              selected = area.id == uiState.selectedAreaId,
                              onClick = { viewModel.selectArea(area.id) },
                              label = { Text("${area.name} ${area.freeNum}/${area.totalNum}") },
                          )
                        }
                      }
                }
              }

              item {
                val mapAvailable = selectedMapResource != null
                SectionTitle(
                    title = "座位",
                    trailing = {
                      OutlinedButton(
                          enabled = mapAvailable,
                          onClick = { showMapDialog = true },
                      ) {
                        Text("查看座位分布")
                      }
                    },
                )
                if (!mapAvailable && uiState.selectedAreaId != null) {
                  Text("当前分区暂无平面图", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }

              item {
                val visibleSeats = visibleLibBookSeats(uiState.seats)
                when {
                  uiState.isSeatsLoading -> InlineLoading("正在加载座位...")
                  uiState.seatsError != null ->
                      LibBookInlineError(
                          uiState.seatsError,
                          onRetry = viewModel::refreshReserveData,
                      )
                  uiState.seats.isEmpty() ->
                      Text("当前分区暂无座位数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  visibleSeats.isEmpty() ->
                      Text("当前分区暂无可预约座位", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  else ->
                      SeatGrid(
                          seats = visibleSeats,
                          selectedSeatId = uiState.selectedSeatId,
                          onSeatClick = viewModel::selectSeat,
                      )
                }
              }

              item {
                ReserveSummary(uiState)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled =
                        uiState.selectedSeatId != null &&
                            uiState.selectedSlotId != null &&
                            !uiState.isSubmitting,
                    onClick = viewModel::submitReservation,
                ) {
                  if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                  }
                  Text("确认预约")
                }
              }
            }
      }
    }
  }
}

@Composable
private fun SeatGrid(
    seats: List<LibBookSeatDto>,
    selectedSeatId: String?,
    onSeatClick: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    seats.chunked(4).forEach { rowSeats ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        rowSeats.forEach { seat ->
          val selected = seat.id == selectedSeatId
          ElevatedCard(
              modifier = Modifier.weight(1f).height(72.dp).clickable { onSeatClick(seat.id) },
          ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
              Text(
                  text = seat.no.ifBlank { seat.name.ifBlank { seat.id } },
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
              )
              Text(
                  text = if (selected) "已选" else "可预约",
                  style = MaterialTheme.typography.bodySmall,
                  color =
                      if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        repeat(4 - rowSeats.size) { Spacer(modifier = Modifier.weight(1f)) }
      }
    }
  }
}

@Composable
private fun ReserveSummary(uiState: LibBookUiState) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text("预约信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text("日期：${uiState.selectedDay.ifBlank { "-" }}")
      Text("分区：${uiState.selectedArea?.name ?: "-"}")
      Text("座位：${uiState.selectedSeat?.no ?: uiState.selectedSeat?.name ?: "-"}")
    }
  }
  Spacer(Modifier.height(8.dp))
}

@Composable
private fun LibBookAreaMapDialog(
    areaName: String,
    mapResource: DrawableResource,
    onDismiss: () -> Unit,
) {
  var transform by remember(mapResource) { mutableStateOf(LibBookMapViewerTransform()) }
  val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
    transform =
        updateLibBookMapViewerTransform(
            current = transform,
            zoomChange = zoomChange,
            panChangeX = panChange.x,
            panChangeY = panChange.y,
        )
  }

  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 16.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
    ) {
      Column(
          modifier = Modifier.fillMaxSize().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
              text = areaName.ifBlank { "座位分布" },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { transform = resetLibBookMapViewerTransform(transform) }) {
              Text("重置")
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
          }
        }

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
          Image(
              painter = painterResource(mapResource),
              contentDescription = null,
              modifier =
                  Modifier.fillMaxSize()
                      .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offsetX
                        translationY = transform.offsetY
                      }
                      .transformable(transformableState),
              contentScale = ContentScale.Fit,
          )
        }
      }
    }
  }
}

@Composable
internal fun LibBookErrorState(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(message ?: "加载失败", color = MaterialTheme.colorScheme.error)
      OutlinedButton(onClick = onRetry) { Text("重试") }
    }
  }
}

@Composable
private fun LibBookInlineError(message: String?, onRetry: () -> Unit) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(message ?: "加载失败", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
    AssistChip(onClick = onRetry, label = { Text("重试") })
  }
}

@Composable
private fun InlineLoading(text: String) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun LoadingBox(text: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    InlineLoading(text)
  }
}

@Composable
private fun SectionTitle(title: String, trailing: @Composable (() -> Unit)? = null) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    trailing?.invoke()
  }
  Spacer(Modifier.height(4.dp))
}

internal fun visibleLibBookSeats(seats: List<LibBookSeatDto>): List<LibBookSeatDto> =
    seats.filter { it.isAvailable }

internal data class LibBookMapViewerTransform(
    val scale: Float = MinLibBookMapScale,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

internal fun updateLibBookMapViewerTransform(
    current: LibBookMapViewerTransform,
    zoomChange: Float,
    panChangeX: Float,
    panChangeY: Float,
): LibBookMapViewerTransform {
  val nextScale = (current.scale * zoomChange).coerceIn(MinLibBookMapScale, MaxLibBookMapScale)
  return if (nextScale == MinLibBookMapScale) {
    LibBookMapViewerTransform()
  } else {
    current.copy(
        scale = nextScale,
        offsetX = current.offsetX + panChangeX,
        offsetY = current.offsetY + panChangeY,
    )
  }
}

internal fun resetLibBookMapViewerTransform(
    current: LibBookMapViewerTransform
): LibBookMapViewerTransform = current.copy(scale = MinLibBookMapScale, offsetX = 0f, offsetY = 0f)

internal fun libBookAreaMapResource(areaId: String?): DrawableResource? =
    when (areaId) {
      "6" -> Res.drawable.area_6
      "8" -> Res.drawable.area_8
      "16" -> Res.drawable.area_16
      "18" -> Res.drawable.area_18
      "19" -> Res.drawable.area_19
      "20" -> Res.drawable.area_20
      "21" -> Res.drawable.area_21
      "22" -> Res.drawable.area_22
      "23" -> Res.drawable.area_23
      "24" -> Res.drawable.area_24
      "25" -> Res.drawable.area_25
      "26" -> Res.drawable.area_26
      "27" -> Res.drawable.area_27
      "28" -> Res.drawable.area_28
      "29" -> Res.drawable.area_29
      "52" -> Res.drawable.area_52
      "53" -> Res.drawable.area_53
      "63" -> Res.drawable.area_63
      "64" -> Res.drawable.area_64
      "65" -> Res.drawable.area_65
      "67" -> Res.drawable.area_67
      "68" -> Res.drawable.area_68
      "69" -> Res.drawable.area_69
      "71" -> Res.drawable.area_71
      "72" -> Res.drawable.area_72
      "73" -> Res.drawable.area_73
      "82" -> Res.drawable.area_82
      "83" -> Res.drawable.area_83
      "117" -> Res.drawable.area_117
      else -> null
    }

private const val MinLibBookMapScale = 1f
private const val MaxLibBookMapScale = 5f
