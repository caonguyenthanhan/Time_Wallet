package com.timewallet.mvp.domain.model

/**
 * Dự án cá nhân tùy chọn giúp nhóm các Task liên quan (ví dụ: "Wally 2.0", "Sức khỏe", "Ôn thi").
 */
data class Project(
    val id: String,
    val name: String,
    val colorHex: Long = TimeCategory.WORK.colorHex,
    val archivedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val isArchived: Boolean get() = archivedAt != null
}

enum class TaskPriority(val labelVi: String, val badgeText: String, val sortWeight: Int) {
    P1("Ưu tiên cao", "P1", 0),
    P2("Trung bình", "P2", 1),
    P3("Thấp", "P3", 2);

    companion object {
        fun fromStorage(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: P2
    }
}

enum class TaskStatus(val labelVi: String) {
    INBOX("Inbox"),
    TODO("Cần làm"),
    DOING("Đang làm"),
    DONE("Đã xong"),
    ARCHIVED("Lưu trữ");

    companion object {
        fun fromStorage(raw: String?): TaskStatus =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: INBOX
    }
}

enum class EisenhowerQuad(val labelVi: String, val shortHint: String) {
    Q1("Gấp & Quan trọng", "Làm ngay"),
    Q2("Quan trọng · Không gấp", "Lên lịch"),
    Q3("Gấp · Ít quan trọng", "Thu gọn"),
    Q4("Không gấp · Khác", "Cân nhắc bỏ");

    companion object {
        fun fromStorageOrNull(raw: String?): EisenhowerQuad? =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() }
    }
}

data class SubtaskItem(
    val id: String,
    val taskId: String,
    val title: String,
    val isDone: Boolean = false,
    val sortOrder: Int = 0
)

data class TaskItem(
    val id: String,
    val title: String,
    val notes: String = "",
    val projectId: String? = null,
    val tagId: String? = null,
    val category: TimeCategory = TimeCategory.WORK,
    val priority: TaskPriority = TaskPriority.P2,
    val estimatedMinutes: Int? = null,
    val dueDateLocal: String? = null,
    val dueTimeLocal: String? = null,
    val status: TaskStatus = TaskStatus.INBOX,
    val eisenhowerQuad: EisenhowerQuad? = null,
    val goalId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val completedAt: Long? = null,
    val subtasks: List<SubtaskItem> = emptyList()
) {
    val isDone: Boolean get() = status == TaskStatus.DONE
    val effectiveQuadrant: EisenhowerQuad
        get() = eisenhowerQuad ?: when (priority) {
            TaskPriority.P1 -> if (dueDateLocal != null) EisenhowerQuad.Q1 else EisenhowerQuad.Q2
            TaskPriority.P2 -> EisenhowerQuad.Q2
            TaskPriority.P3 -> EisenhowerQuad.Q4
        }
}

enum class HabitFrequency(val labelVi: String) {
    DAILY("Hàng ngày"),
    WEEKDAYS("T2–T6"),
    CUSTOM_DAYS("Theo thứ"),
    TIMES_PER_WEEK("Theo số lần/tuần");

    companion object {
        fun fromStorage(raw: String?): HabitFrequency =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: DAILY
    }
}

data class Habit(
    val id: String,
    val title: String,
    val tagId: String? = null,
    val category: TimeCategory = TimeCategory.HEALTH,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    /** Bitmask ISO dayOfWeek (bit 0 = T2 .. bit 6 = CN). Mặc định 127 (cả tuần). */
    val daysMask: Int = 0b1111111,
    val targetPerPeriod: Int = 1,
    val minSessionMinutes: Int = 15,
    val goalId: String? = null,
    val archivedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val isArchived: Boolean get() = archivedAt != null
}

data class HabitLog(
    val id: String,
    val habitId: String,
    val dateLocal: String,
    val completed: Boolean,
    val linkedLedgerId: String? = null,
    val updatedAt: Long = 0L
)

enum class ThemeModePreference(val labelVi: String) {
    SYSTEM("Theo máy"),
    LIGHT("Sáng"),
    DARK("Tối");

    companion object {
        fun fromStorage(raw: String?): ThemeModePreference =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: LIGHT
    }
}

/**
 * Cấu hình bật/tắt module (Customization-first).
 * Tắt một module chỉ ẩn giao diện, KHÔNG xóa dữ liệu đã lưu trong Room.
 */
data class CustomizationSettings(
    val enableKanban: Boolean = false,
    val enableEisenhower: Boolean = false,
    val enableHabits: Boolean = true,
    val enablePomodoro: Boolean = true,
    val enableDice: Boolean = true,
    val enableAiAssistant: Boolean = true,
    val defaultPomodoroMinutes: Int = 25,
    val defaultBreakMinutes: Int = 5,
    val themeMode: ThemeModePreference = ThemeModePreference.LIGHT
)

/**
 * Quy tắc tự động phân loại cục bộ 100% offline theo từ khóa người dùng cài đặt.
 */
data class LocalCategoryRule(
    val id: String,
    val keyword: String,
    val category: TimeCategory,
    val tagId: String? = null,
    val markWasteByDefault: Boolean = false,
    val enabled: Boolean = true
)

/**
 * Ma trận quyền đọc chi tiết của Wally AI (Zero-leak: nhóm nào tắt sẽ không được đưa vào prompt).
 */
data class AiReadPermissions(
    val readLedger: Boolean = true,
    val readTasks: Boolean = true,
    val readHabits: Boolean = true,
    val readGoals: Boolean = true
) {
    fun enabledLabels(): List<String> = buildList {
        if (readLedger) add("Sổ cái 24h")
        if (readTasks) add("Công việc")
        if (readHabits) add("Thói quen")
        if (readGoals) add("Mục tiêu")
    }
}
