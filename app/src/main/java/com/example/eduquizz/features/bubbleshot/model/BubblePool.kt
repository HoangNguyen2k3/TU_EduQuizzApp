package com.example.eduquizz.features.bubbleshot.model

import kotlin.random.Random

/**
 * Object Pool để quản lý Bubble objects
 * Tái sử dụng objects thay vì tạo mới mỗi lần
 */
class BubblePool(private val initialSize: Int = 20) {

    private val pool = mutableListOf<Bubble>()
    private var nextId = 0
    init {
        repeat(initialSize) {
            pool.add(Bubble(id = nextId++))
        }
    }
    fun acquire(answer: String, position: Int): Bubble {
        var bubble = pool.firstOrNull { !it.isActive }
        if (bubble == null) {
            bubble = Bubble(id = nextId++)
            pool.add(bubble)
        }
        bubble.answer = answer
        bubble.isActive = true
        bubble.position = position
        bubble.offsetY = Random.nextFloat() * 20f
        return bubble
    }
    fun release(bubble: Bubble) {
        bubble.reset()
    }
    fun releaseAll() {
        pool.forEach { it.reset() }
    }
    fun getActiveBubbles(): List<Bubble> {
        return pool.filter { it.isActive }
    }
    fun getActiveCount(): Int {
        return pool.count { it.isActive }
    }
    fun getAvailableCount(): Int {
        return pool.count { !it.isActive }
    }

    fun getTotalSize(): Int {
        return pool.size
    }

    /**
     * Debug info
     */
    override fun toString(): String {
        return "BubblePool(total=${pool.size}, active=${getActiveCount()}, available=${getAvailableCount()})"
    }
}