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
        in 18..23 -> "buổi tối"
        else -> "buổi khuya"
    }

    // v1.0.11: một câu ngắn; 0-4h không chào "buổi tối".
    fun dashboardLine(hour: Int): String =
        if (hour in 0..4) "Khuya rồi, nhớ nghỉ ngơi nhé!" else "Chào ${partOfDay(hour)}!"
}
