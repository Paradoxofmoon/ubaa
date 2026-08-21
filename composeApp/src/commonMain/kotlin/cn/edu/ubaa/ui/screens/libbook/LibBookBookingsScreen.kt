package cn.edu.ubaa.ui.screens.libbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.model.dto.LibBookBookingDto
import cn.edu.ubaa.model.dto.canCancelBooking

@Composable
fun LibBookBookingsScreen(viewModel: LibBookViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(Unit) { viewModel.ensureBookingsLoaded() }
  LaunchedEffect(uiState.actionMessage) {
    uiState.actionMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.clearActionMessage()
    }
  }

  Scaffold(
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
      snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
      when {
        uiState.isBookingsLoading && uiState.bookings.bookings.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
        uiState.bookingsError != null && uiState.bookings.bookings.isEmpty() ->
            LibBookErrorState(
                message = uiState.bookingsError,
                onRetry = viewModel::loadBookings,
                modifier = Modifier.fillMaxSize(),
            )
        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                      "预约记录",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                  )
                  IconButton(onClick = { viewModel.loadBookings() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                  }
                }
              }
              if (uiState.bookings.bookings.isEmpty()) {
                item { Text("当前暂无图书馆座位预约", color = MaterialTheme.colorScheme.onSurfaceVariant) }
              } else {
                items(uiState.bookings.bookings) { booking ->
                  LibBookBookingCard(
                      booking = booking,
                      isCanceling = uiState.cancelingBookingId == booking.id,
                      onCancel = { viewModel.cancelBooking(booking.id) },
                  )
                }
              }
            }
      }
    }
  }
}

@Composable
private fun LibBookBookingCard(
    booking: LibBookBookingDto,
    isCanceling: Boolean,
    onCancel: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text =
                booking.nameMerge
                    .ifBlank {
                      listOf(booking.areaName, booking.seatNo)
                          .filter { it.isNotBlank() }
                          .joinToString(" / ")
                    }
                    .ifBlank { "图书馆座位" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = booking.statusName.ifBlank { booking.status.ifBlank { "已预约" } },
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
      }
      Text(
          text =
              listOf(
                      booking.day,
                      listOf(booking.beginTime, booking.endTime)
                          .filter { it.isNotBlank() }
                          .joinToString("~"),
                  )
                  .filter { it.isNotBlank() }
                  .joinToString(" "),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (booking.seatNo.isNotBlank()) {
        Text("座位：${booking.seatNo}", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (booking.canCancelBooking()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AssistChip(
              onClick = onCancel,
              enabled = !isCanceling,
              label = { Text(if (isCanceling) "取消中..." else "取消预约") },
          )
        }
      }
    }
  }
}
