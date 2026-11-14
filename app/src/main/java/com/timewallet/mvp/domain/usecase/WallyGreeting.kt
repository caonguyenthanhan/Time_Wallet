package com.timewallet.mvp.domain.usecase

/**
 * Lời chào TRANG TRÍ một câu trên Dashboard (không phải câu trả lời của Wally, không gọi API).
 * WALLY API-FIRST: tab Wally KHÔNG còn trả lời lời chào cục bộ; mọi tin nhắn ở đó do Cloud AI xử lý.
 */
object WallyGreeting {

    private fun partOfDay(hour: Int): String = when (hour) {
        in 5..10 -> "buổi sáng"
        in 11..12 -> "buổi trưa"
        in 13..17 -> "buổi chiều"
        else -> "buổi tối"
    }

    fun dashboardLine(hour: Int): String = "Chào ${partOfDay(hour)}! Wally đang giữ sổ thời gian giúp bạn."
}
