package com.timewallet.mvp.domain.model

enum class TimeCategory(
    val key: String,
    val displayName: String,
    val emoji: String,
    val colorHex: Long
) {
    WORK("work", "Công việc", "💼", 0xFF287BCBL),
    LEARN("learn", "Học tập", "📘", 0xFF8962C7L),
    HEALTH("health", "Vận động", "🏃", 0xFF24A890L),
    LIFE("life", "Sinh hoạt", "🏡", 0xFFCE9E54L),
    REST("rest", "Nghỉ ngơi", "☕", 0xFFDBB560L),
    SOCIAL("social", "Kết nối", "🤝", 0xFFD986A0L),
    FUN("fun", "Giải trí", "🎧", 0xFFEC9863L),
    SLEEP("sleep", "Ngủ", "🌙", 0xFF8197AAL),
    OTHER("other", "Khác", "◌", 0xFF97A5A0L);

    companion object {
        fun fromKeyOrNull(key: String?): TimeCategory? {
            if (key.isNullOrBlank()) return null
            val normalized = key.trim().lowercase()
            return entries.firstOrNull { it.key == normalized }
        }

        fun fromKeyOrDefault(key: String?): TimeCategory {
            return fromKeyOrNull(key) ?: OTHER
        }
    }
}

