package com.example.eduquizz.features.loginreward.repository

import android.content.Context
import android.util.Log
import com.example.eduquizz.features.loginreward.model.LoginRewardConfig
import com.example.eduquizz.features.loginreward.model.LoginRewardData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class LoginRewardRepository @Inject constructor() {
    
    private val database by lazy {
        FirebaseDatabase.getInstance()
    }
    
    /**
     * Tạo hash để xác thực request
     */
    private fun createHash(userId: String, day: Int, serverTimestamp: Long, deviceTimestamp: Long): String {
        val input = "${userId}_${day}_${serverTimestamp}_${deviceTimestamp}_secret_key"
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Lấy user ID từ device (có thể dùng Android ID hoặc tạo unique ID)
     */
    fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        var userId = prefs.getString("user_id", null)
        if (userId == null) {
            userId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            prefs.edit().putString("user_id", userId).apply()
        }
        return userId
    }
    
    /**
     * Lấy thời gian server từ Firebase
     */
    suspend fun getServerTimestamp(): Long {
        return suspendCancellableCoroutine { cont ->
            val ref = database.getReference(".info/serverTimeOffset")
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val offset = snapshot.getValue(Long::class.java) ?: 0L
                    val serverTime = System.currentTimeMillis() + offset
                    cont.resume(serverTime)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // Fallback về device time nếu không lấy được server time
                    cont.resume(System.currentTimeMillis())
                }
            })
        }
    }
    
    /**
     * Lấy dữ liệu login reward từ Firebase
     */
    suspend fun getLoginRewardData(userId: String): Map<String, Any>? {
        return suspendCancellableCoroutine { cont ->
            try {
                val ref = database.getReference("login_rewards/$userId")
                Log.d("LoginReward", "Đang lấy dữ liệu từ Firebase: login_rewards/$userId")
                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val data = snapshot.value as? Map<String, Any>
                            Log.d("LoginReward", "Đã lấy được dữ liệu: $data")
                            cont.resume(data)
                        } else {
                            Log.d("LoginReward", "Chưa có dữ liệu cho user này (user mới)")
                            cont.resume(null)
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("LoginReward", "Error getting reward data: ${error.message}")
                        cont.resume(null)
                    }
                })
            } catch (e: Exception) {
                Log.e("LoginReward", "Exception khi lấy dữ liệu: ${e.message}", e)
                cont.resume(null)
            }
        }
    }
    
    /**
     * Kiểm tra và claim reward với xác thực server
     */
    suspend fun claimReward(
        userId: String,
        day: Int,
        deviceTimestamp: Long
    ): Result<LoginRewardData> {
        return try {
            // Lấy server timestamp
            val serverTimestamp = getServerTimestamp()
            
            // Kiểm tra thời gian hợp lệ (chênh lệch không quá 5 phút)
            val timeDiff = kotlin.math.abs(serverTimestamp - deviceTimestamp)
            if (timeDiff > 5 * 60 * 1000) { // 5 phút
                return Result.failure(Exception("Thời gian không hợp lệ. Vui lòng đồng bộ thời gian."))
            }
            
            // Tạo hash
            val hash = createHash(userId, day, serverTimestamp, deviceTimestamp)
            
            // Lấy dữ liệu hiện tại
            val currentData = getLoginRewardData(userId)
            
            // Kiểm tra đã claim chưa
            val rewardKey = "day_$day"
            if (currentData != null && currentData.containsKey(rewardKey)) {
                val dayData = currentData[rewardKey] as? Map<*, *>
                if (dayData?.get("isClaimed") == true) {
                    return Result.failure(Exception("Bạn đã nhận phần thưởng ngày $day rồi!"))
                }
            }
            
            // Kiểm tra ngày hợp lệ (phải claim tuần tự)
            val expectedDay = getCurrentDay(userId, currentData)
            if (day != expectedDay) {
                return Result.failure(Exception("Bạn phải nhận phần thưởng theo thứ tự! Ngày hiện tại: $expectedDay"))
            }
            
            // Lưu vào Firebase
            val rewardData = mapOf(
                "day" to day,
                "coinAmount" to LoginRewardConfig.getRewardForDay(day),
                "isClaimed" to true,
                "claimedTimestamp" to serverTimestamp,
                "deviceTimestamp" to deviceTimestamp,
                "hash" to hash,
                "claimDate" to getDateString(serverTimestamp)
            )
            
            val ref = database.getReference("login_rewards/$userId/$rewardKey")
            ref.setValue(rewardData).await()
            
            // Cập nhật lastClaimDay
            database.getReference("login_rewards/$userId/lastClaimDay")
                .setValue(day).await()
            
            // Cập nhật lastServerCheck với server timestamp (quan trọng để kiểm tra đã claim hôm nay)
            database.getReference("login_rewards/$userId/lastServerCheck")
                .setValue(serverTimestamp).await()
            
            Log.d("LoginReward", "✅ Đã lưu reward vào Firebase: day=$day, lastServerCheck=$serverTimestamp")
            
            val reward = LoginRewardData(
                day = day,
                coinAmount = LoginRewardConfig.getRewardForDay(day),
                isClaimed = true,
                claimedTimestamp = serverTimestamp,
                deviceTimestamp = deviceTimestamp,
                hash = hash
            )
            
            Result.success(reward)
            
        } catch (e: Exception) {
            Log.e("LoginReward", "Error claiming reward: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Kiểm tra và khóa user nếu phát hiện gian lận
     */
    suspend fun checkAndBlockIfCheating(
        userId: String,
        deviceTimestamp: Long
    ): Boolean {
        return try {
            val serverTimestamp = getServerTimestamp()
            val currentData = getLoginRewardData(userId)
            
            if (currentData == null) return false
            
            // Kiểm tra lastServerCheck
            val lastServerCheck = (currentData["lastServerCheck"] as? Long) ?: 0L
            val lastClaimDay = (currentData["lastClaimDay"] as? Int) ?: 0
            
            // Nếu có claim trước đó, kiểm tra thời gian
            if (lastServerCheck > 0) {
                // Thời gian phải tăng (không được quay lại quá khứ)
                if (serverTimestamp < lastServerCheck - 60000) { // Cho phép sai số 1 phút
                    // Phát hiện gian lận - khóa
                    database.getReference("login_rewards/$userId/isBlocked")
                        .setValue(true).await()
                    database.getReference("login_rewards/$userId/blockReason")
                        .setValue("Time manipulation detected").await()
                    return true
                }
                
                // Kiểm tra ngày claim phải tuần tự
                val expectedNextDay = lastClaimDay + 1
                val deviceDate = getDateFromTimestamp(deviceTimestamp)
                val lastClaimDate = getDateFromTimestamp(lastServerCheck)
                
                // Nếu device date nhỏ hơn last claim date -> gian lận
                if (deviceDate < lastClaimDate) {
                    database.getReference("login_rewards/$userId/isBlocked")
                        .setValue(true).await()
                    database.getReference("login_rewards/$userId/blockReason")
                        .setValue("Date manipulation detected").await()
                    return true
                }
            }
            
            false
        } catch (e: Exception) {
            Log.e("LoginReward", "Error checking cheating: ${e.message}", e)
            false
        }
    }
    
    /**
     * Lấy ngày hiện tại user có thể claim
     */
    private suspend fun getCurrentDay(userId: String, currentData: Map<String, Any>?): Int {
        Log.d("LoginReward", "getCurrentDay - userId: $userId, currentData: ${currentData != null}")
        
        // Nếu chưa có dữ liệu, trả về ngày 1 (user mới)
        if (currentData == null) {
            Log.d("LoginReward", "getCurrentDay - User mới, trả về ngày 1")
            return 1
        }
        
        val lastClaimDay = (currentData["lastClaimDay"] as? Int) ?: 0
        val isBlocked = (currentData["isBlocked"] as? Boolean) ?: false
        
        Log.d("LoginReward", "getCurrentDay - lastClaimDay: $lastClaimDay, isBlocked: $isBlocked")
        
        if (isBlocked) {
            Log.d("LoginReward", "getCurrentDay - User bị khóa")
            return 0 // Bị khóa
        }
        
        // Kiểm tra xem đã claim hết 7 ngày chưa
        if (lastClaimDay >= LoginRewardConfig.TOTAL_DAYS) {
            Log.d("LoginReward", "getCurrentDay - Đã claim hết 7 ngày")
            return 0 // Đã claim hết
        }
        
        // Kiểm tra xem đã claim hôm nay chưa (sử dụng server timestamp)
        val lastServerCheck = (currentData["lastServerCheck"] as? Long) ?: 0L
        Log.d("LoginReward", "getCurrentDay - lastServerCheck: $lastServerCheck")
        
        if (lastServerCheck > 0) {
            try {
                // Sử dụng device time trước, sau đó sẽ verify với server khi claim
                val deviceTimestamp = System.currentTimeMillis()
                val lastClaimDate = getDateFromTimestamp(lastServerCheck)
                val today = getDateFromTimestamp(deviceTimestamp)
                
                Log.d("LoginReward", "getCurrentDay - lastClaimDate: $lastClaimDate, today: $today")
                
                if (lastClaimDate == today) {
                    // Đã claim hôm nay rồi (theo device time, sẽ verify lại với server khi claim)
                    Log.d("LoginReward", "getCurrentDay - Đã claim hôm nay rồi (theo device time)")
                    return 0
                }
                
                // Kiểm tra xem có skip ngày không (phải claim liên tiếp)
                val daysDiff = getDaysDifference(lastClaimDate, today)
                Log.d("LoginReward", "getCurrentDay - daysDiff: $daysDiff")
                
                if (daysDiff > 1) {
                    // Bỏ lỡ ngày -> reset về ngày 1
                    Log.d("LoginReward", "getCurrentDay - Bỏ lỡ ngày, reset về ngày 1")
                    return 1
                }
            } catch (e: Exception) {
                Log.e("LoginReward", "Lỗi khi kiểm tra ngày: ${e.message}", e)
                // Nếu có lỗi, vẫn cho phép claim ngày tiếp theo
            }
        }
        
        val nextDay = lastClaimDay + 1
        Log.d("LoginReward", "getCurrentDay - Trả về ngày tiếp theo: $nextDay")
        return nextDay
    }
    
    /**
     * Lấy ngày hiện tại có thể claim (public)
     */
    suspend fun getCurrentClaimableDay(userId: String): Int {
        return try {
            val currentData = getLoginRewardData(userId)
            val day = getCurrentDay(userId, currentData)
            Log.d("LoginReward", "getCurrentClaimableDay - userId: $userId, day: $day")
            day
        } catch (e: Exception) {
            Log.e("LoginReward", "Lỗi khi lấy claimable day: ${e.message}", e)
            // Nếu có lỗi, trả về 1 để user mới có thể claim
            1
        }
    }
    
    /**
     * Kiểm tra user có bị khóa không
     */
    suspend fun isUserBlocked(userId: String): Boolean {
        val currentData = getLoginRewardData(userId)
        return (currentData?.get("isBlocked") as? Boolean) ?: false
    }
    
    /**
     * Lấy tất cả rewards đã claim
     */
    suspend fun getAllRewards(userId: String): List<LoginRewardData> {
        val currentData = getLoginRewardData(userId) ?: return emptyList()
        val rewards = mutableListOf<LoginRewardData>()
        
        for (day in 1..LoginRewardConfig.TOTAL_DAYS) {
            val rewardKey = "day_$day"
            val dayData = currentData[rewardKey] as? Map<*, *>
            
            if (dayData != null) {
                rewards.add(
                    LoginRewardData(
                        day = (dayData["day"] as? Int) ?: day,
                        coinAmount = (dayData["coinAmount"] as? Int) ?: LoginRewardConfig.getRewardForDay(day),
                        isClaimed = (dayData["isClaimed"] as? Boolean) ?: false,
                        claimedTimestamp = (dayData["claimedTimestamp"] as? Long),
                        deviceTimestamp = (dayData["deviceTimestamp"] as? Long),
                        hash = (dayData["hash"] as? String)
                    )
                )
            } else {
                rewards.add(
                    LoginRewardData(
                        day = day,
                        coinAmount = LoginRewardConfig.getRewardForDay(day),
                        isClaimed = false
                    )
                )
            }
        }
        
        return rewards
    }
    
    // Helper functions
    private fun getDateFromTimestamp(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return String.format(
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    private fun getDateString(timestamp: Long): String {
        return getDateFromTimestamp(timestamp)
    }
    
    private fun getDaysDifference(date1: String, date2: String): Long {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        
        val parts1 = date1.split("-")
        val parts2 = date2.split("-")
        
        cal1.set(parts1[0].toInt(), parts1[1].toInt() - 1, parts1[2].toInt())
        cal2.set(parts2[0].toInt(), parts2[1].toInt() - 1, parts2[2].toInt())
        
        val diff = cal2.timeInMillis - cal1.timeInMillis
        return diff / (24 * 60 * 60 * 1000)
    }
}


