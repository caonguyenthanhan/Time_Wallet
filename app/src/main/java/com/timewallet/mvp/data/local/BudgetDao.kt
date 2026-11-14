package com.timewallet.mvp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM category_budgets ORDER BY isPriority DESC, priorityOrder ASC, updatedAt DESC")
    fun observeAllBudgets(): Flow<List<CategoryBudgetEntity>>

    @Query("SELECT * FROM category_budgets ORDER BY isPriority DESC, priorityOrder ASC, updatedAt DESC")
    suspend fun getAllBudgetsSnapshot(): List<CategoryBudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(entity: CategoryBudgetEntity)

    @Query("DELETE FROM category_budgets WHERE categoryKey = :categoryKey")
    suspend fun deleteBudgetByCategory(categoryKey: String)

    @Query("DELETE FROM category_budgets")
    suspend fun deleteAllBudgets()
}

