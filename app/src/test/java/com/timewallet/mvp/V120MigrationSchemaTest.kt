package com.timewallet.mvp

import androidx.sqlite.db.SupportSQLiteDatabase
import com.timewallet.mvp.data.local.TimeWalletDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * v1.2.0: migration 4 -> 5 phải (1) khớp 100% lược đồ `app/schemas/.../5.json` do Room xuất,
 * và (2) bảo toàn toàn bộ dữ liệu cũ (chuỗi 1 -> 2 -> 3 -> 4 -> 5) với 7 cột mới mặc định NULL.
 */
class V120MigrationSchemaTest {

    private val schemaFile = File("schemas/com.timewallet.mvp.data.local.TimeWalletDatabase/5.json")
    private val schemaV6File = File("schemas/com.timewallet.mvp.data.local.TimeWalletDatabase/6.json")
    private val schemaV7File = File("schemas/com.timewallet.mvp.data.local.TimeWalletDatabase/7.json")
    private val schemaV8File = File("schemas/com.timewallet.mvp.data.local.TimeWalletDatabase/8.json")

    private fun proxy(conn: Connection): SupportSQLiteDatabase = Proxy.newProxyInstance(
        SupportSQLiteDatabase::class.java.classLoader, arrayOf(SupportSQLiteDatabase::class.java)
    ) { _, method, args ->
        when (method.name) {
            "execSQL" -> { conn.createStatement().use { it.execute(args[0] as String) }; null }
            "isOpen" -> !conn.isClosed
            "close" -> { conn.close(); null }
            else -> throw UnsupportedOperationException("Method ${method.name} not mocked")
        }
    } as SupportSQLiteDatabase

    private class Col(val name: String, val type: String, val notNull: Int, val pk: Int)

    private fun columns(conn: Connection, table: String): List<Col> = conn.createStatement().use { st ->
        st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
            val out = ArrayList<Col>()
            while (rs.next()) out += Col(rs.getString("name"), rs.getString("type").uppercase(), rs.getInt("notnull"), rs.getInt("pk"))
            out.sortedBy { it.name }
        }
    }

    private fun indices(conn: Connection, table: String): List<Triple<String, Int, List<String>>> = conn.createStatement().use { st ->
        val names = ArrayList<Pair<String, Int>>()
        st.executeQuery("PRAGMA index_list(`$table`)").use { rs ->
            while (rs.next()) { val n = rs.getString("name"); if (!n.startsWith("sqlite_autoindex")) names += n to rs.getInt("unique") }
        }
        names.map { (n, u) ->
            val cols = ArrayList<String>()
            st.executeQuery("PRAGMA index_info(`$n`)").use { rs -> while (rs.next()) cols += rs.getString("name") }
            Triple(n, u, cols)
        }.sortedBy { it.first }
    }

    private fun entitySql(json: JSONObject): List<Pair<String, String>> {
        val entities = json.getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length()).map { entities.getJSONObject(it) }.map { e ->
            val table = e.getString("tableName")
            table to e.getString("createSql").replace("\${TABLE_NAME}", table)
        }
    }

    private fun indexSql(json: JSONObject): List<String> {
        val entities = json.getJSONObject("database").getJSONArray("entities")
        val out = ArrayList<String>()
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i); val table = e.getString("tableName")
            val idx = e.optJSONArray("indices") ?: continue
            for (j in 0 until idx.length()) out += idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
        }
        return out
    }

    private fun loadSchema(file: File = schemaFile): JSONObject {
        assertTrue("thiếu ${file.absolutePath}: chạy :app:kspDebugKotlin trước", file.exists())
        return JSONObject(file.readText(Charsets.UTF_8))
    }

    private val v3LedgerSql = """
        CREATE TABLE IF NOT EXISTS `ledger_records` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `layer` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
        `userConfirmedAt` INTEGER NOT NULL, `category` TEXT NOT NULL, `title` TEXT NOT NULL, `date` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL,
        `startedAt` INTEGER, `endedAt` INTEGER, `timezone` TEXT NOT NULL, `status` TEXT NOT NULL, `idempotencyKey` TEXT NOT NULL, PRIMARY KEY(`id`))
    """.trimIndent()

    @Test
    fun `schema version 5 lists the 7 new confirmation and overlap columns on ledger_records`() {
        val json = loadSchema()
        assertEquals(5, json.getJSONObject("database").getInt("version"))
        val ledgerSql = entitySql(json).first { it.first == "ledger_records" }.second
        listOf(
            "`confirmationState` TEXT",
            "`plannedStartedAt` INTEGER",
            "`plannedEndedAt` INTEGER",
            "`plannedDurationSeconds` INTEGER",
            "`overlapResolution` TEXT",
            "`relationGroupId` TEXT",
            "`overlapWithTitle` TEXT"
        ).forEach { col ->
            assertTrue("thiếu cột $col trong 5.json", ledgerSql.contains(col))
        }
    }

    @Test
    fun `chained migration 1 to 5 matches Room v5 schema and preserves legacy rows untouched`() {
        val json = loadSchema()
        val ref = DriverManager.getConnection("jdbc:sqlite::memory:")
        val migFile = File.createTempFile("timewallet_migration_v1_to_v5_", ".db")
        try {
            entitySql(json).forEach { (_, sql) -> ref.createStatement().use { it.execute(sql) } }
            indexSql(json).forEach { sql -> ref.createStatement().use { it.execute(sql) } }

            val url = "jdbc:sqlite:${migFile.absolutePath}"
            DriverManager.getConnection(url).use { conn ->
                conn.createStatement().use { st ->
                    st.execute(v3LedgerSql)
                    st.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_records_idempotencyKey` ON `ledger_records` (`idempotencyKey`)")
                    st.execute("CREATE INDEX IF NOT EXISTS `index_ledger_records_date` ON `ledger_records` (`date`)")
                    st.execute("CREATE INDEX IF NOT EXISTS `index_ledger_records_startedAt_endedAt` ON `ledger_records` (`startedAt`, `endedAt`)")
                    entitySql(json).filter { it.first in setOf("category_budgets", "daily_reviews", "activity_review_notes") }.forEach { st.execute(it.second) }
                    indexSql(json).filter { it.contains("category_budgets") || it.contains("daily_reviews") || it.contains("activity_review_notes") }.forEach { st.execute(it) }
                    st.execute("INSERT INTO ledger_records VALUES ('legacy1','timer','MEASURED',1,2,'work','Làm việc cũ','2026-11-15',3600,1000,3601000,'Asia/Ho_Chi_Minh','CONFIRMED','k_legacy')")
                }
                val db = proxy(conn)
                TimeWalletDatabase.MIGRATION_1_2.migrate(db)
                TimeWalletDatabase.MIGRATION_2_3.migrate(db)
                TimeWalletDatabase.MIGRATION_3_4.migrate(db)
                TimeWalletDatabase.MIGRATION_4_5.migrate(db)
            }

            DriverManager.getConnection(url).use { conn ->
                for (table in listOf("ledger_records", "category_budgets", "daily_reviews", "activity_review_notes", "tags", "goals")) {
                    val a = columns(ref, table)
                    val b = columns(conn, table)
                    assertEquals("cột $table", a.map { listOf(it.name, it.type, it.notNull, it.pk) }, b.map { listOf(it.name, it.type, it.notNull, it.pk) })
                    assertEquals("chỉ mục $table", indices(ref, table), indices(conn, table))
                }
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT title, durationSeconds, confirmationState, plannedStartedAt, overlapResolution, relationGroupId, overlapWithTitle FROM ledger_records WHERE id='legacy1'").use { rs ->
                        assertTrue(rs.next())
                        assertEquals("Làm việc cũ", rs.getString("title"))
                        assertEquals(3600L, rs.getLong("durationSeconds"))
                        assertNull(rs.getString("confirmationState"))
                        assertEquals(0L, rs.getLong("plannedStartedAt"))
                        assertTrue(rs.wasNull())
                        assertNull(rs.getString("overlapResolution"))
                        assertNull(rs.getString("relationGroupId"))
                        assertNull(rs.getString("overlapWithTitle"))
                    }
                }
            }
        } finally {
            ref.close()
            migFile.delete()
        }
    }

    @Test
    fun `schema version 6 adds isMarkedWaste NOT NULL and chained migration 1 to 6 matches Room v6 schema`() {
        val json = loadSchema(schemaV6File)
        assertEquals(6, json.getJSONObject("database").getInt("version"))
        val ledgerSql = entitySql(json).first { it.first == "ledger_records" }.second
        assertTrue("thiếu cột isMarkedWaste trong 6.json", ledgerSql.contains("`isMarkedWaste` INTEGER NOT NULL"))

        val ref = DriverManager.getConnection("jdbc:sqlite::memory:")
        val migFile = File.createTempFile("timewallet_migration_v1_to_v6_", ".db")
        try {
            entitySql(json).forEach { (_, sql) -> ref.createStatement().use { it.execute(sql) } }
            indexSql(json).forEach { sql -> ref.createStatement().use { it.execute(sql) } }

            val url = "jdbc:sqlite:${migFile.absolutePath}"
            DriverManager.getConnection(url).use { conn ->
                conn.createStatement().use { st ->
                    st.execute(v3LedgerSql)
                    st.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_records_idempotencyKey` ON `ledger_records` (`idempotencyKey`)")
                    st.execute("CREATE INDEX IF NOT EXISTS `index_ledger_records_date` ON `ledger_records` (`date`)")
                    st.execute("CREATE INDEX IF NOT EXISTS `index_ledger_records_startedAt_endedAt` ON `ledger_records` (`startedAt`, `endedAt`)")
                    entitySql(json).filter { it.first in setOf("category_budgets", "daily_reviews", "activity_review_notes") }.forEach { st.execute(it.second) }
                    indexSql(json).filter { it.contains("category_budgets") || it.contains("daily_reviews") || it.contains("activity_review_notes") }.forEach { st.execute(it) }
                    st.execute("INSERT INTO ledger_records VALUES ('legacy1','timer','MEASURED',1,2,'work','Làm việc cũ','2026-11-15',3600,1000,3601000,'Asia/Ho_Chi_Minh','CONFIRMED','k_legacy')")
                }
                val db = proxy(conn)
                TimeWalletDatabase.MIGRATION_1_2.migrate(db)
                TimeWalletDatabase.MIGRATION_2_3.migrate(db)
                TimeWalletDatabase.MIGRATION_3_4.migrate(db)
                TimeWalletDatabase.MIGRATION_4_5.migrate(db)
                TimeWalletDatabase.MIGRATION_5_6.migrate(db)
            }

            DriverManager.getConnection(url).use { conn ->
                for (table in listOf("ledger_records", "category_budgets", "daily_reviews", "activity_review_notes", "tags", "goals")) {
                    val a = columns(ref, table)
                    val b = columns(conn, table)
                    assertEquals("cột $table", a.map { listOf(it.name, it.type, it.notNull, it.pk) }, b.map { listOf(it.name, it.type, it.notNull, it.pk) })
                    assertEquals("chỉ mục $table", indices(ref, table), indices(conn, table))
                }
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT title, durationSeconds, isMarkedWaste FROM ledger_records WHERE id='legacy1'").use { rs ->
                        assertTrue(rs.next())
                        assertEquals("Làm việc cũ", rs.getString("title"))
                        assertEquals(3600L, rs.getLong("durationSeconds"))
                        assertEquals(0, rs.getInt("isMarkedWaste"))
                    }
                }
            }
        } finally {
            ref.close()
            migFile.delete()
        }
    }

    @Test
    fun `schema version 8 adds postponedCount to tasks and chained migration 1 to 8 matches Room v8 schema`() {
        val json = loadSchema(schemaV8File)
        assertEquals(8, json.getJSONObject("database").getInt("version"))

        val ref = DriverManager.getConnection("jdbc:sqlite::memory:")
        val migFile = File.createTempFile("timewallet_migration_v1_to_v8_", ".db")
        try {
            entitySql(json).forEach { (_, sql) -> ref.createStatement().use { it.execute(sql) } }
            indexSql(json).forEach { sql -> ref.createStatement().use { it.execute(sql) } }

            val v6Json = loadSchema(schemaV6File)
            val url = "jdbc:sqlite:${migFile.absolutePath}"
            DriverManager.getConnection(url).use { conn ->
                conn.createStatement().use { st ->
                    st.execute(v3LedgerSql)
                    st.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_records_idempotencyKey` ON `ledger_records` (`idempotencyKey`)")
                    st.execute("CREATE INDEX IF NOT EXISTS `index_ledger_records_date` ON `ledger_records` (`date`)")
                    st.execute("CREATE INDEX IF NOT EXISTS `index_ledger_records_startedAt_endedAt` ON `ledger_records` (`startedAt`, `endedAt`)")
                    entitySql(v6Json).filter { it.first in setOf("category_budgets", "daily_reviews", "activity_review_notes") }.forEach { st.execute(it.second) }
                    indexSql(v6Json).filter { it.contains("category_budgets") || it.contains("daily_reviews") || it.contains("activity_review_notes") }.forEach { st.execute(it) }
                }
                val db = proxy(conn)
                TimeWalletDatabase.MIGRATION_1_2.migrate(db)
                TimeWalletDatabase.MIGRATION_2_3.migrate(db)
                TimeWalletDatabase.MIGRATION_3_4.migrate(db)
                TimeWalletDatabase.MIGRATION_4_5.migrate(db)
                TimeWalletDatabase.MIGRATION_5_6.migrate(db)
                TimeWalletDatabase.MIGRATION_6_7.migrate(db)
                TimeWalletDatabase.MIGRATION_7_8.migrate(db)
            }

            DriverManager.getConnection(url).use { conn ->
                val a = columns(ref, "tasks")
                val b = columns(conn, "tasks")
                assertEquals(a.map { listOf(it.name, it.type, it.notNull, it.pk) }, b.map { listOf(it.name, it.type, it.notNull, it.pk) })
                assertEquals(indices(ref, "tasks"), indices(conn, "tasks"))
            }
        } finally {
            ref.close()
            migFile.delete()
        }
    }

    @Test
    fun `schema version 7 adds tasks habits projects columns and chained migration 1 to 7 matches Room v7 schema`() {
        val json = loadSchema(schemaV7File)
        assertEquals(7, json.getJSONObject("database").getInt("version"))
        val tables = entitySql(json).map { it.first }.toSet()
        assertTrue(tables.containsAll(listOf("projects", "tasks", "subtasks", "habits", "habit_logs")))

        val ref = DriverManager.getConnection("jdbc:sqlite::memory:")
        val migFile = File.createTempFile("timewallet_migration_v1_to_v7_", ".db")
        try {
            entitySql(json).forEach { (_, sql) -> ref.createStatement().use { it.execute(sql) } }
            indexSql(json).forEach { sql -> ref.createStatement().use { it.execute(sql) } }

            val v6Json = loadSchema(schemaV6File)
            val url = "jdbc:sqlite:${migFile.absolutePath}"
            DriverManager.getConnection(url).use { conn ->
                conn.createStatement().use { st ->
                    st.execute(v3LedgerSql)
                    st.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_records_idempotencyKey` ON `ledger_records` (`idempotencyKey`)")
                    st.execute("CREATE INDEX IF NOT EXISTS `index_ledger_records_date` ON `ledger_records` (`date`)")
                    st.execute("CREATE INDEX IF NOT EXISTS `index_ledger_records_startedAt_endedAt` ON `ledger_records` (`startedAt`, `endedAt`)")
                    entitySql(v6Json).filter { it.first in setOf("category_budgets", "daily_reviews", "activity_review_notes") }.forEach { st.execute(it.second) }
                    indexSql(v6Json).filter { it.contains("category_budgets") || it.contains("daily_reviews") || it.contains("activity_review_notes") }.forEach { st.execute(it) }
                    st.execute("INSERT INTO ledger_records VALUES ('legacy1','timer','MEASURED',1,2,'work','Làm việc cũ','2026-11-15',3600,1000,3601000,'Asia/Ho_Chi_Minh','CONFIRMED','k_legacy')")
                    st.execute("INSERT INTO category_budgets (categoryKey, period, targetSeconds, isPriority, priorityOrder, updatedAt) VALUES ('work','WEEKLY',72000,1,0,1000)")
                }
                val db = proxy(conn)
                TimeWalletDatabase.MIGRATION_1_2.migrate(db)
                TimeWalletDatabase.MIGRATION_2_3.migrate(db)
                TimeWalletDatabase.MIGRATION_3_4.migrate(db)
                TimeWalletDatabase.MIGRATION_4_5.migrate(db)
                TimeWalletDatabase.MIGRATION_5_6.migrate(db)
                TimeWalletDatabase.MIGRATION_6_7.migrate(db)
            }

            DriverManager.getConnection(url).use { conn ->
                val allTables = listOf(
                    "ledger_records", "category_budgets", "daily_reviews", "activity_review_notes",
                    "tags", "goals", "projects", "tasks", "subtasks", "habits", "habit_logs"
                )
                for (table in allTables) {
                    val a = columns(ref, table)
                    val b = columns(conn, table)
                    assertEquals("cột $table", a.map { listOf(it.name, it.type, it.notNull, it.pk) }, b.map { listOf(it.name, it.type, it.notNull, it.pk) })
                    assertEquals("chỉ mục $table", indices(ref, table), indices(conn, table))
                }
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT targetType, targetKey, kind, period, targetSeconds FROM goals WHERE targetKey='work' AND kind='MAX'").use { rs ->
                        assertTrue("Ngân sách 1.x phải được chuyển thành Goal MAX", rs.next())
                        assertEquals("CATEGORY", rs.getString("targetType"))
                        assertEquals("WEEK", rs.getString("period"))
                        assertEquals(72000L, rs.getLong("targetSeconds"))
                    }
                    st.executeQuery("SELECT targetSeconds FROM category_budgets WHERE categoryKey='work'").use { rs ->
                        assertTrue("Bảng category_budgets vẫn được giữ nguyên vẹn", rs.next())
                        assertEquals(72000L, rs.getLong("targetSeconds"))
                    }
                }
            }
        } finally {
            ref.close()
            migFile.delete()
        }
    }
}
