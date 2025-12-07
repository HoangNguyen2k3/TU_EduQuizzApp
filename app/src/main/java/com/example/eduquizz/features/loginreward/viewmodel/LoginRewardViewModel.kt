package com.example.eduquizz.features.loginreward.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.eduquizz.features.loginreward.model.LoginRewardConfig
import com.example.eduquizz.features.loginreward.model.LoginRewardData
import com.example.eduquizz.features.loginreward.model.LoginRewardState
import com.example.eduquizz.features.loginreward.repository.LoginRewardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginRewardViewModel @Inject constructor(
    application: Application,
    private val repository: LoginRewardRepository
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(LoginRewardState())
    val uiState: StateFlow<LoginRewardState> = _uiState.asStateFlow()
    
    private val _showRewardDialog = MutableStateFlow(false)
    val showRewardDialog: StateFlow<Boolean> = _showRewardDialog.asStateFlow()
    
    // Flag để tránh hiển thị dialog lại sau khi đã claim thành công
    private var hasClaimedToday = false
    
    private val userId: String
        get() = repository.getUserId(getApplication())
    
    /**
     * Kiểm tra và load dữ liệu login reward khi app mở
     */
    fun checkLoginReward() {
        viewModelScope.launch {
            try {
                android.util.Log.d("LoginReward", "🔍 Bắt đầu kiểm tra login reward...")
                
                // Kiểm tra xem user có bị khóa không
                val isBlocked = repository.isUserBlocked(userId)
                android.util.Log.d("LoginReward", "🔒 User blocked: $isBlocked")
                if (isBlocked) {
                    _uiState.value = _uiState.value.copy(
                        isBlocked = true,
                        errorMessage = "Tài khoản của bạn đã bị khóa do phát hiện gian lận."
                    )
                    return@launch
                }
                
                // Kiểm tra và khóa nếu phát hiện gian lận
                val deviceTimestamp = System.currentTimeMillis()
                val cheatingDetected = repository.checkAndBlockIfCheating(userId, deviceTimestamp)
                android.util.Log.d("LoginReward", "🚫 Cheating detected: $cheatingDetected")
                if (cheatingDetected) {
                    _uiState.value = _uiState.value.copy(
                        isBlocked = true,
                        errorMessage = "Phát hiện thao tác không hợp lệ. Tài khoản đã bị khóa."
                    )
                    return@launch
                }
                
                // Lấy ngày hiện tại có thể claim
                val currentDay = repository.getCurrentClaimableDay(userId)
                android.util.Log.d("LoginReward", "📅 Current claimable day: $currentDay")
                
                // Lấy tất cả rewards
                val rewards = repository.getAllRewards(userId)
                android.util.Log.d("LoginReward", "🎁 Total rewards: ${rewards.size}")
                
                // Kiểm tra xem có thể claim hôm nay không
                val canClaim = currentDay > 0 && currentDay <= LoginRewardConfig.TOTAL_DAYS
                android.util.Log.d("LoginReward", "✅ Can claim today: $canClaim, currentDay: $currentDay")
                
                _uiState.value = LoginRewardState(
                    currentDay = currentDay,
                    rewards = rewards,
                    canClaimToday = canClaim,
                    isBlocked = false,
                    lastServerCheck = System.currentTimeMillis()
                )
                
                // Chỉ hiển thị dialog nếu:
                // 1. Có thể claim (currentDay > 0)
                // 2. Dialog chưa đang hiển thị
                // 3. State hiện tại chưa claim (currentDay > 0 trong state)
                // 4. Chưa claim trong session này (hasClaimedToday = false)
                val currentState = _uiState.value
                val shouldShow = canClaim && 
                                 !_showRewardDialog.value && 
                                 currentState.currentDay != 0 && // Chỉ hiển thị nếu state chưa được set về 0 (chưa claim)
                                 !hasClaimedToday // Chưa claim trong session này
                
                if (shouldShow) {
                    android.util.Log.d("LoginReward", "🎉 Hiển thị dialog reward! Day: $currentDay")
                    _showRewardDialog.value = true
                } else {
                    android.util.Log.d("LoginReward", "❌ Không hiển thị dialog. canClaim=$canClaim, currentDay=$currentDay, alreadyShowing=${_showRewardDialog.value}, currentStateDay=${currentState.currentDay}, hasClaimedToday=$hasClaimedToday")
                    // Đảm bảo dialog đóng nếu không thể claim hoặc đã claim (currentDay = 0)
                    if (!canClaim || currentState.currentDay == 0 || hasClaimedToday) {
                        _showRewardDialog.value = false
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("LoginReward", "❌ Lỗi khi kiểm tra phần thưởng", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Lỗi khi kiểm tra phần thưởng: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Claim reward cho ngày hiện tại
     */
    fun claimReward(
        onSuccess: (Int) -> Unit, // coinAmount
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentDay = _uiState.value.currentDay
                if (currentDay <= 0 || currentDay > LoginRewardConfig.TOTAL_DAYS) {
                    onError("Không thể nhận phần thưởng!")
                    return@launch
                }
                
                if (_uiState.value.isBlocked) {
                    onError("Tài khoản của bạn đã bị khóa!")
                    return@launch
                }
                
                val deviceTimestamp = System.currentTimeMillis()
                val result = repository.claimReward(userId, currentDay, deviceTimestamp)
                
                result.getOrElse { exception ->
                    android.util.Log.e("LoginReward", "❌ Lỗi khi claim: ${exception.message}", exception)
                    // Reset state khi có lỗi
                    _showRewardDialog.value = false
                    onError(exception.message ?: "Lỗi khi nhận phần thưởng!")
                    return@launch
                }.let { reward ->
                    android.util.Log.d("LoginReward", "✅ Claim reward thành công: ${reward.coinAmount} xu")
                    
                    // Cập nhật state
                    val updatedRewards = _uiState.value.rewards.toMutableList()
                    if (currentDay <= updatedRewards.size) {
                        updatedRewards[currentDay - 1] = reward
                    } else {
                        // Nếu chưa có trong list, thêm vào
                        while (updatedRewards.size < currentDay) {
                            updatedRewards.add(
                                LoginRewardData(
                                    day = updatedRewards.size + 1,
                                    coinAmount = LoginRewardConfig.getRewardForDay(updatedRewards.size + 1),
                                    isClaimed = false
                                )
                            )
                        }
                        updatedRewards[currentDay - 1] = reward
                    }
                    
                    // Đánh dấu đã claim trong session này
                    hasClaimedToday = true
                    
                    // Cập nhật state TRƯỚC để đảm bảo điều kiện hiển thị dialog không còn đúng
                    _uiState.value = _uiState.value.copy(
                        rewards = updatedRewards,
                        canClaimToday = false,
                        currentDay = 0 // Đã claim hôm nay - set về 0 để dialog không hiển thị nữa
                    )
                    
                    // Đóng dialog SAU khi cập nhật state
                    _showRewardDialog.value = false
                    
                    android.util.Log.d("LoginReward", "💰 Gọi callback onSuccess với ${reward.coinAmount} xu, dialog đã đóng, hasClaimedToday=true")
                    // Gọi callback để cập nhật coin
                    onSuccess(reward.coinAmount)
                }
                
            } catch (e: Exception) {
                onError("Lỗi: ${e.message}")
            }
        }
    }
    
    /**
     * Đóng dialog
     */
    fun dismissDialog() {
        _showRewardDialog.value = false
    }
}


