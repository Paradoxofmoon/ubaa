package cn.edu.ubaa.model.dto

import kotlinx.serialization.Serializable

/**
 * 智慧校车（zhihuixiaoche.buaa.edu.cn）订票 DTO。
 *
 * 抓包结论（2026-08）：校车系统是独立 CAS（service=http://zhihuixiaoche.buaa.edu.cn/wechat/CASLogin）， 登录后以
 * beihang2 会话 cookie 访问 /wechat 接口。站点固定两个：学院路 / 沙河（可双向）。
 */
@Serializable
data class BusShiftDto(
    val depart_time: String = "",
    val arrive_time: String = "",
    val is_shuttle: Int = 0,
    val line_name: String = "",
    val open_seat_num: Int = 0,
    val shifts_date: String = "",
    val shifts_number: String = "",
    val student_num: Int = 0,
    val teacher_num: Int = 0,
    val up_origin_name: String = "",
    val up_terminal_name: String = "",
    val type: Int = 0,
    /** 前端同款余票公式计算值（open_seat_num - 学生 - 教师，非随到随走且距发车>ticket_all_minute 再减保留座）。 */
    val ticketNum: Int = 0,
)

@Serializable
data class BusShiftsResponse(
    val success: Boolean = false,
    val message: String = "",
    val list: List<BusShiftDto> = emptyList(),
    val type: Int = 0,
    val currentTime: Long = 0,
    val seat_retain_num: Int = 0,
    val ticket_all_minute: Int = 15,
    val is_parttime: Int = 0,
)

/** 车次详情页（/wechat/ticketInfoPage）解析出的购票信息 + 下单所需 CSRF。 */
@Serializable
data class BusTicketDetailDto(
    val shiftsDate: String = "",
    val departTime: String = "",
    val weekday: String = "",
    val category: String = "",
    val remainingTickets: Int = -1,
    val price: String = "",
    val origin: String = "",
    val terminal: String = "",
    val shiftsNumber: String = "",
    val csrfToken: String = "",
)

/** 下单（/wechat/buyTicketForWX）响应。status=="1" 成功；price>0 时 url 为 ccpay 收银台地址。 */
@Serializable
data class BusBuyResultDto(
    val status: String = "",
    val message: String = "",
    val url: String = "",
    val orderId: String = "",
    val code: String = "",
    val price: String = "",
)

/** indexPage 嵌入的服务端可订日期列表 + 全局 CSRF + 服务端时间。 */
@Serializable
data class BusIndexPageDto(
    val shiftsDateList: List<String> = emptyList(),
    val csrfToken: String = "",
    val nowTime: Long = 0,
)

/** /wechat/waitingOrder 返回的当前用户（姓名 + 学工号）。 */
@Serializable
data class BusSessionUserDto(
    val name: String = "",
    val tempNumber: String = "",
    val success: Boolean = false,
)
