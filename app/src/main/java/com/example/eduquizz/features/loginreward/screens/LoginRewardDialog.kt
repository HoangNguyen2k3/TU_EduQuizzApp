package com.example.eduquizz.features.loginreward.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.eduquizz.R
import com.example.eduquizz.features.loginreward.model.LoginRewardConfig
import com.example.eduquizz.features.loginreward.viewmodel.LoginRewardViewModel
import kotlinx.coroutines.delay

@Composable
fun LoginRewardDialog(
    viewModel: LoginRewardViewModel,
    currentDay: Int,
    coinAmount: Int,
    onClaimClick: () -> Unit,
    onDismiss: () -> Unit,
    isBlocked: Boolean = false
) {
    var isClaiming by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val showDialog by viewModel.showRewardDialog.collectAsState()
    
    // Lấy danh sách rewards đã claim để hiển thị progress đúng
    val claimedDays = uiState.rewards.filter { it.isClaimed }.map { it.day }.toSet()
    
    // Reset claiming state khi dialog đóng hoặc showDialog thay đổi
    LaunchedEffect(showDialog) {
        if (!showDialog) {
            // Dialog đã được đóng từ ViewModel - reset state
            isClaiming = false
            android.util.Log.d("LoginReward", "🔄 Reset isClaiming vì dialog đã đóng")
        }
    }
    
    // Reset claiming state khi canClaimToday = false (đã claim xong)
    LaunchedEffect(uiState.canClaimToday) {
        if (!uiState.canClaimToday && isClaiming) {
            isClaiming = false
            android.util.Log.d("LoginReward", "🔄 Reset isClaiming vì canClaimToday = false")
        }
    }
    
    // Animation cho coin
    val infiniteTransition = rememberInfiniteTransition(label = "coin_animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Dialog(
        onDismissRequest = { if (!isClaiming) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isClaiming,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFD700),
                                Color(0xFFFFA500),
                                Color(0xFFFF8C00)
                            )
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { if (!isClaiming) onDismiss() },
                        enabled = !isClaiming
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Title
                Text(
                    text = "🎉 Phần Thưởng Đăng Nhập",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Day indicator
                Text(
                    text = "Ngày $currentDay / ${LoginRewardConfig.TOTAL_DAYS}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Coin display with animation
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(scale),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.coinimg),
                        contentDescription = "Coin",
                        modifier = Modifier.size(100.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Coin amount
                Text(
                    text = "+$coinAmount",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Xu",
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Claim button
                if (isBlocked) {
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Gray,
                            disabledContainerColor = Color.Gray
                        )
                    ) {
                        Text(
                            text = "Đã bị khóa",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (!isClaiming) {
                                android.util.Log.d("LoginReward", "🖱️ User click Nhận Ngay")
                                isClaiming = true
                                onClaimClick()
                            }
                        },
                        enabled = !isClaiming,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3),
                            disabledContainerColor = Color(0xFF90CAF9)
                        )
                    ) {
                        if (isClaiming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Nhận Ngay",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Progress indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (day in 1..LoginRewardConfig.TOTAL_DAYS) {
                        val isClaimed = claimedDays.contains(day)
                        val isToday = day == currentDay
                        
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = when {
                                        isClaimed -> Color(0xFF4CAF50) // Đã claim
                                        isToday -> Color(0xFF2196F3) // Hôm nay
                                        else -> Color.White.copy(alpha = 0.3f) // Chưa đến
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isClaimed) {
                                Text(
                                    text = "✓",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "$day",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
}

// Helper composable để hiển thị dialog với ViewModel
@Composable
fun LoginRewardDialogWrapper(
    viewModel: LoginRewardViewModel,
    onCoinReceived: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDialog by viewModel.showRewardDialog.collectAsState()
    val context = LocalContext.current
    
    // Debug logging
    LaunchedEffect(showDialog, uiState.canClaimToday, uiState.isBlocked, uiState.currentDay) {
        android.util.Log.d("LoginReward", "DialogWrapper - showDialog: $showDialog, canClaim: ${uiState.canClaimToday}, isBlocked: ${uiState.isBlocked}, currentDay: ${uiState.currentDay}")
    }
    
    // Chỉ hiển thị dialog nếu TẤT CẢ điều kiện đều đúng:
    // 1. showDialog = true (ViewModel cho phép hiển thị)
    // 2. canClaimToday = true (có thể claim)
    // 3. !isBlocked (không bị khóa)
    // 4. currentDay > 0 (có ngày để claim)
    // 5. Đảm bảo không hiển thị lại sau khi đã claim
    val shouldShowDialog = showDialog && 
                          uiState.canClaimToday && 
                          !uiState.isBlocked && 
                          uiState.currentDay > 0
    
    if (shouldShowDialog) {
        val currentDay = uiState.currentDay
        val coinAmount = LoginRewardConfig.getRewardForDay(currentDay)
        
        android.util.Log.d("LoginReward", "🎉 Hiển thị LoginRewardDialog - Day: $currentDay, Coin: $coinAmount")
        
            LoginRewardDialog(
            viewModel = viewModel,
            currentDay = currentDay,
            coinAmount = coinAmount,
            onClaimClick = {
                android.util.Log.d("LoginReward", "🔄 Bắt đầu claim reward...")
                viewModel.claimReward(
                    onSuccess = { amount ->
                        android.util.Log.d("LoginReward", "✅ Claim thành công! Số xu: $amount")
                        // Cập nhật coin
                        onCoinReceived(amount)
                        // Hiển thị thông báo
                        android.widget.Toast.makeText(
                            context,
                            "Đã nhận $amount xu!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        // Dialog sẽ tự đóng vì ViewModel đã set _showRewardDialog = false và currentDay = 0
                    },
                    onError = { error ->
                        // Hiển thị lỗi - có thể dùng Snackbar hoặc Toast
                        android.util.Log.e("LoginReward", "❌ Lỗi khi claim: $error")
                        android.widget.Toast.makeText(
                            context,
                            error,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        // Dialog đã được đóng trong ViewModel khi có lỗi
                    }
                )
            },
            onDismiss = {
                android.util.Log.d("LoginReward", "🚪 Đóng dialog")
                viewModel.dismissDialog()
            },
            isBlocked = uiState.isBlocked
        )
    }
}


