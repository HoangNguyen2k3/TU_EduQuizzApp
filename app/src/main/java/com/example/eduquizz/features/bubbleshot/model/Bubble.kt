package com.example.eduquizz.features.bubbleshot.model

import kotlin.random.Random

/**
 * Bubble object - sẽ được pool và tái sử dụng
 * Thay thế cho String? trong code cũ
 */
data class Bubble(
    val id: Int,                    // ID duy nhất để track bubble
    var answer: String = "",        // Đáp án hiển thị trên bubble
    var isActive: Boolean = false,  // Bubble đang được sử dụng hay không
    var position: Int = 0,          // Vị trí trong grid (0-19 cho grid 4x5)
    var offsetY: Float = Random.nextFloat() * 20f // Offset cho animation
) {
    /**
     * Reset bubble về trạng thái mặc định
     */
    fun reset() {
        answer = ""
        isActive = false
        position = 0
        offsetY = Random.nextFloat() * 20f
    }
}