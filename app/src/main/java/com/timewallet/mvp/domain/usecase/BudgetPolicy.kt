package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.TimeBudgetProgress
import com.timewallet.mvp.domain.model.TimeCategory

/** WALLY A11: tối đa 3 ngân sách ưu tiên; không cho lưu ngân sách thứ 4 ưu tiên (không ẩn âm thầm). */
object BudgetPolicy {
    const val MAX_PRIORITY = 3

    /** @return thông báo lỗi nếu việc lưu sẽ vượt giới hạn ưu tiên; null nếu hợp lệ. */
    fun priorityViolation(existing: List<CategoryTimeBudget>, category: TimeCategory, wantsPriority: Boolean): String? {
        if (!wantsPriority) return null
        val otherPriority = existing.count { it.isPriority && it.category != category }
        return if (otherPriority >= MAX_PRIORITY) {
            "Chỉ được đặt tối đa $MAX_PRIORITY ngân sách ưu tiên. Hãy tắt \"Ưu tiên\" ở một mục khác trước."
        } else null
    }

    /** Dashboard: tối đa 3 mục, mỗi danh mục một lần (khóa chính là danh mục nên không trùng); ưu tiên trước. */
    fun dashboardBudgets(all: List<TimeBudgetProgress>): List<TimeBudgetProgress> {
        val priority = all.filter { it.budget.isPriority }
        return (if (priority.isNotEmpty()) priority else all).distinctBy { it.category }.take(MAX_PRIORITY)
    }

    /** true nếu danh sách Dashboard là các mục ưu tiên thật (để đặt tiêu đề đúng). */
    fun dashboardShowsPriority(all: List<TimeBudgetProgress>): Boolean = all.any { it.budget.isPriority }
}
