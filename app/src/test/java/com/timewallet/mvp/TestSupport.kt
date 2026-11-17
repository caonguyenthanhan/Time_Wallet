package com.timewallet.mvp

import android.content.SharedPreferences
import com.timewallet.mvp.data.local.LedgerDao
import com.timewallet.mvp.data.local.LedgerRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Bản sao dùng chung của InMemorySharedPreferences (bản gốc trong TimeWalletDomainTest là private). */
class InMemorySharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (map[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val staged = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { staged[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { staged[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { staged[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { staged[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { staged[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { staged[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { removals.add(key) }
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            if (clearAll) map.clear()
            removals.forEach { map.remove(it) }
            staged.forEach { (k, v) ->
                if (v == null) map.remove(k) else map[k] = v
            }
            return true
        }

        override fun apply() {
            commit()
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

/** DAO trong RAM dùng chung cho test; chạy NGUYÊN logic commit mặc định của LedgerDao (không mô phỏng index duy nhất). */
class InMemoryLedgerDao : LedgerDao {
    private val rows = mutableListOf<LedgerRecordEntity>()
    override fun observeAllRecords(): Flow<List<LedgerRecordEntity>> = emptyFlow()
    override suspend fun getAllRecordsSnapshot(): List<LedgerRecordEntity> = rows.toList()
    override suspend fun countByIdempotencyKey(idempotencyKey: String): Int =
        rows.count { it.idempotencyKey == idempotencyKey }
    override suspend fun findOverlappingEntity(startMs: Long, endMs: Long, excludeId: String): LedgerRecordEntity? =
        rows.firstOrNull { r ->
            (r.layer == "MEASURED" || r.layer == "REPORTED_TIMED" || r.layer == "ACTIVE") &&
                r.status != "PLANNED" &&
                r.status != "CANCELLED" &&
                r.confirmationState != "CANCELLED" &&
                r.overlapResolution !in setOf("PENDING", "BREAK_REJECTED") &&
                r.startedAt != null && r.endedAt != null &&
                r.id != excludeId && startMs < r.endedAt && endMs > r.startedAt
        }
    override suspend fun upsertInternal(entity: LedgerRecordEntity) {
        rows.removeAll { it.id == entity.id }; rows.add(entity)
    }
    override suspend fun deleteById(id: String) { rows.removeAll { it.id == id } }
    override suspend fun deleteAll() { rows.clear() }
}
