package com.example.eduquizz.features.loginreward.model

data class LoginRewardData(
    val day: Int, // 1-7
    val coinAmount: Int, // 100, 200, ..., 700
    val isClaimed: Boolean = false,
    val claimedTimestamp: Long? = null, // Server timestamp khi claim
    val deviceTimestamp: Long? = null, // Device timestamp khi claim (để kiểm tra)
    val hash: String? = null // Hash để xác thực
)

data class LoginRewardState(
    val currentDay: Int = 0, // 0 = chưa bắt đầu, 1-7 = ngày hiện tại
    val rewards: List<LoginRewardData> = emptyList(),
    val canClaimToday: Boolean = false,
    val isBlocked: Boolean = false, // Bị khóa do phát hiện gian lận
    val lastServerCheck: Long = 0,
    val errorMessage: String? = null
)

// Reward amounts cho 7 ngày
object LoginRewardConfig {
    const val TOTAL_DAYS = 7
    val REWARD_AMOUNTS = listOf(100, 200, 300, 400, 500, 600, 700)
    
    fun getRewardForDay(day: Int): Int {
        return if (day in 1..TOTAL_DAYS) {
            REWARD_AMOUNTS[day - 1]
        } else {
            0
        }
    }
}


