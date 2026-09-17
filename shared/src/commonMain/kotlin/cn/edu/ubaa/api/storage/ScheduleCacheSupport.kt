package cn.edu.ubaa.api.storage

/**
 * 课表缓存「邻近周检测」支持函数。
 *
 * 启动核对时不盲目全量拉取，只检查当前周附近的几周（先缓存、再与旧缓存比对）， 任一邻近周有变动才触发全量刷新。
 *
 * @param currentSerial 当前周次序号（curWeek）。
 * @param weekCount 该学期总周数。
 * @param radius 邻近半径，默认 ±3 周。
 * @return 需要检测的周次序号列表，已裁剪到 [1, weekCount] 范围。
 */
fun nearbyWeekSerials(currentSerial: Int, weekCount: Int, radius: Int = 3): List<Int> =
    (maxOf(1, currentSerial - radius)..minOf(weekCount, currentSerial + radius)).toList()
