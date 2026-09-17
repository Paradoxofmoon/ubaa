package cn.edu.ubaa.ui.screens.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.icons.LocalAppIcons

data class FeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

@Composable
fun RegularFeaturesScreen(
    onScheduleClick: () -> Unit,
    onExamClick: () -> Unit,
    onGradeClick: () -> Unit,
    onBykcClick: () -> Unit,
    onClassroomClick: () -> Unit,
    onSpocClick: () -> Unit,
    onJudgeClick: () -> Unit,
    onLibBookClick: () -> Unit,
    onCardClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onZfwClick: () -> Unit,
    onElectricityClick: () -> Unit,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
  val features =
      listOf(
          FeatureItem(
              id = "schedule",
              title = "课表查询",
              description = "查看课程表，支持周视图和学期切换",
              icon = LocalAppIcons.current.CalendarToday,
          ),
          FeatureItem(
              id = "exam",
              title = "考试查询",
              description = "查看考试安排，支持学期切换",
              icon = LocalAppIcons.current.Assignment,
          ),
          FeatureItem(
              id = "grade",
              title = "成绩查询",
              description = "查看课程成绩、学分和绩点",
              icon = LocalAppIcons.current.Grade,
          ),
          FeatureItem(
              id = "bykc",
              title = "博雅课程",
              description = "浏览选课，查看已选，签到签退",
              icon = LocalAppIcons.current.School,
          ),
          FeatureItem(
              id = "classroom",
              title = "空教室查询",
              description = "查询各校区空闲教室",
              icon = LocalAppIcons.current.MeetingRoom,
          ),
          FeatureItem(
              id = "spoc",
              title = "SPOC作业",
              description = "查看当前学期作业与提交状态",
              icon = LocalAppIcons.current.AssignmentTurnedIn,
          ),
          FeatureItem(
              id = "judge",
              title = "希冀作业",
              description = "聚合希冀平台作业与提交进度",
              icon = LocalAppIcons.current.Code,
          ),
          FeatureItem(
              id = "libbook",
              title = "图书馆座位",
              description = "预约图书馆座位并管理记录",
              icon = LocalAppIcons.current.EventSeat,
          ),
          FeatureItem(
              id = "card",
              title = "校园卡",
              description = "查询校园卡余额",
              icon = LocalAppIcons.current.AccountBalanceWallet,
          ),
          FeatureItem(
              id = "network",
              title = "校园网",
              description = "查询免费、赠送与计费流量",
              icon = LocalAppIcons.current.NetworkWifi,
          ),
          FeatureItem(
              id = "zfw",
              title = "校园网充值",
              description = "登录自助服务门户完成缴费充值",
              icon = LocalAppIcons.current.Paid,
          ),
          FeatureItem(
              id = "electricity",
              title = "电费充值",
              description = "北航电费在线充值缴费",
              icon = LocalAppIcons.current.Bolt,
          ),
      )

  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      itemsIndexed(features) { index, feature ->
        FeatureGridCard(
            title = feature.title,
            description = feature.description,
            icon = feature.icon,
            tone = index % 4,
            onClick = {
              when (feature.id) {
                "schedule" -> onScheduleClick()
                "exam" -> onExamClick()
                "grade" -> onGradeClick()
                "bykc" -> onBykcClick()
                "classroom" -> onClassroomClick()
                "spoc" -> onSpocClick()
                "judge" -> onJudgeClick()
                "libbook" -> onLibBookClick()
                "card" -> onCardClick()
                "network" -> onNetworkClick()
                "zfw" -> onZfwClick()
                "electricity" -> onElectricityClick()
              }
            },
        )
      }
    }
  }
}
