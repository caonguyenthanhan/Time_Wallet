package com.timewallet.mvp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.timewallet.mvp.domain.model.BudgetPeriod
import com.timewallet.mvp.domain.model.CategoryTimeBudget
import com.timewallet.mvp.domain.model.TimeCategory

@Entity(tableName = "category_budgets")
data class CategoryBudgetEntity(
    @PrimaryKey val categoryKey: String,
    val period: String,
    val targetSeconds: Long,
    val isPriority: Int,
    val priorityOrder: Int,
    val updatedAt: Long
) {
    fun toDomain(): CategoryTimeBudget {
        return CategoryTimeBudget(
            category = TimeCategory.fromKeyOrDefault(categoryKey),
            period = BudgetPeriod.fromStorageString(period),
            targetSeconds = targetSeconds.coerceAtLeast(60L),
            isPriority = isPriority != 0,
            priorityOrder = priorityOrder,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(budget: CategoryTimeBudget): CategoryBudgetEntity {
            return CategoryBudgetEntity(
                categoryKey = budget.category.key,
                period = budget.period.storageKey,
                targetSeconds = budget.targetSeconds,
                isPriority = if (budget.isPriority) 1 else 0,
                priorityOrder = budget.priorityOrder,
                updatedAt = budget.updatedAt
            )
        }
    }
}

