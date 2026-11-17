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
 * v1.1.0 (L3): migration 3 -> 4 phải (1) tạo ĐÚNG lược đồ mà Room kỳ vọng (đối chiếu với `app/schemas/.../4.json` do chính Room xuất khi build,
 * không viết tay), và (2) bảo toàn 100% dữ liệu cũ, kể cả chuỗi 1 -> 2 -> 3 -> 4. Không có Room runtime trong JVM test: DDL chạy trên sqlite-jdbc.
 */
class V110MigrationSchemaTest {

    private val schemaFile = File("schemas/com.timewallet.mvp.data.local.TimeWalletDatabase/4.json")

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

    /** (name, unique, columns) của các chỉ mục do người dùng tạo (không tính sqlite_autoindex). */
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

    private fun loadSchema(): JSONObject {
        assertTrue("thiếu ${schemaFile.absolutePath}: build một lần để Room xuất schema (exportSchema = true)", schemaFile.exists())
        return JSONObject(schemaFile.readText(Charsets.UTF_8))
    }

    private val v3LedgerSql = """
        CREATE TABLE IF NOT EXISTS `ledger_records` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `layer` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
        `userConfirmedAt` INTEGER NOT NULL, `category` TEXT NOT NULL, `title` TEXT NOT NULL, `date` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL,
        `startedAt` INTEGER, `endedAt` INTEGER, `timezone` TEXT NOT NULL, `status` TEXT NOT NULL, `idempotencyKey` TEXT NOT NULL, PRIMARY KEY(`id`))
    """.trimIndent()

    @Test
    fun `schema version 4 is exported and lists tags goals and the new ledger column`() {
        val json = loadSchema()
        assertEquals(4, json.getJSONObject("database").getInt("version"))
        val tables = entitySql(json).map { it.first }.toSet()
        assertTrue(tables.containsAll(setOf("ledger_records", "category_budgets", "daily_reviews", "activity_review_notes", "tags", "goals")))
        assertTrue(entitySql(json).first { it.first == "ledger_records" }.second.contains("`tagId` TEXT"))
    }

    @Test
    fun `migration 3 to 4 produces exactly the Room v4 schema and keeps every old row`() {
        val json = loadSchema()
        val reference = DriverManager.getConnection("jdbc:sqlite::memory:")
        val migrated = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            // Tham chiếu: lược đồ v4 do Room xuất.
            entitySql(json).forEach { (_, sql) -> reference.createStatement().use { it.execute(sql) } }
            indexSql(json).forEach { sql -> reference.createStatement().use { it.execute(sql) } }

            // v3 thật: ledger_records không có tagId, cùng 3 bảng/ chỉ mục cũ; thêm dữ liệu rồi chạy migration 3 -> 4.
            migrated.createStatement().use { st ->
                st.execute(v3LedgerSql)
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_records_idempotencyKey` ON `ledger_records` (`idempotencyKey`)")
                st.execute("CREATE INDEX IF NOT EXISTS `index_ledger_records_date` ON `ledger_records` (`date`)")
                st.execute("CREATE INDEX IF NOT EXISTS `index_ledger_records_startedAt_endedAt` ON `ledger_records` (`startedAt`, `endedAt`)")
                entitySql(json).filter { it.first in setOf("category_budgets", "daily_reviews", "activity_review_notes") }.forEach { st.execute(it.second) }
                st.execute("INSERT INTO ledger_records VALUES ('a','timer','MEASURED',1,2,'learn','Học','2026-11-11',3600,10,3610000,'Asia/Ho_Chi_Minh','CONFIRMED','k1')")
                st.execute("INSERT INTO ledger_records VALUES ('b','chat','REPORTED_UNTIMED',1,2,'work','Viết','2026-11-11',1800,NULL,NULL,'Asia/Ho_Chi_Minh','CONFIRMED','k2')")
                st.execute("INSERT INTO category_budgets VALUES ('learn','DAILY',7200,1,1,5)")
                st.execute("INSERT INTO daily_reviews VALUES ('2026-11-11','s','q1','q2','a1','a2','n',9)")
                st.execute("INSERT INTO activity_review_notes VALUES ('a','2026-11-11','o','f',9)")
            }
            TimeWalletDatabase.MIGRATION_3_4.migrate(proxy(migrated))

            for (table in listOf("ledger_records", "category_budgets", "daily_reviews", "activity_review_notes", "tags", "goals")) {
                val a = columns(reference, table); val b = columns(migrated, table)
                assertEquals("cột $table", a.map { listOf(it.name, it.type, it.notNull, it.pk) }, b.map { listOf(it.name, it.type, it.notNull, it.pk) })
                assertEquals("chỉ mục $table", indices(reference, table), indices(migrated, table))
            }

            // Dữ liệu cũ nguyên vẹn; bản ghi cũ chưa gắn Tag (NULL), không bị suy diễn.
            migrated.createStatement().use { st ->
                st.executeQuery("SELECT id, title, category, layer, durationSeconds, tagId FROM ledger_records ORDER BY id").use { rs ->
                    assertTrue(rs.next()); assertEquals("a", rs.getString("id")); assertEquals("Học", rs.getString("title")); assertEquals(3600L, rs.getLong("durationSeconds")); assertNull(rs.getString("tagId"))
                    assertTrue(rs.next()); assertEquals("b", rs.getString("id")); assertEquals("REPORTED_UNTIMED", rs.getString("layer")); assertNull(rs.getString("tagId"))
                    assertTrue(!rs.next())
                }
                st.executeQuery("SELECT COUNT(*) FROM category_budgets").use { rs -> rs.next(); assertEquals(1, rs.getInt(1)) }
                st.executeQuery("SELECT COUNT(*) FROM daily_reviews").use { rs -> rs.next(); assertEquals(1, rs.getInt(1)) }
                st.executeQuery("SELECT COUNT(*) FROM activity_review_notes").use { rs -> rs.next(); assertEquals(1, rs.getInt(1)) }
                // Bảng mới dùng được: Tag trùng nameKey bị chỉ mục duy nhất chặn (ABORT), không ghi đè Tag khác.
                st.execute("INSERT INTO tags VALUES ('t1','Chạy bộ','chay bo','health',NULL,1,1)")
                var duplicateRejected = false
                try { st.execute("INSERT INTO tags VALUES ('t2','CHẠY BỘ','chay bo','health',NULL,2,2)") } catch (_: java.sql.SQLException) { duplicateRejected = true }
                assertTrue(duplicateRejected)
                st.execute("UPDATE ledger_records SET tagId='t1' WHERE id='a'")
                st.executeQuery("SELECT tagId FROM ledger_records WHERE id='a'").use { rs -> rs.next(); assertEquals("t1", rs.getString(1)) }
            }
        } finally {
            reference.close(); migrated.close()
        }
    }

    @Test
    fun `chained migration 1 2 3 4 on a file database keeps the oldest rows and survives a restart`() {
        val json = loadSchema()
        val f = File.createTempFile("timewallet_migration_v1_to_v4_", ".db")
        try {
            val url = "jdbc:sqlite:${f.absolutePath}"
            DriverManager.getConnection(url).use { conn ->
                conn.createStatement().use { st ->
                    st.execute(v3LedgerSql)
                    st.execute("INSERT INTO ledger_records VALUES ('old','chat','REPORTED_UNPLACED',1,2,'work','Việc cũ','2026-11-01',1800,NULL,NULL,'Asia/Ho_Chi_Minh','CONFIRMED','kold')")
                }
                val db = proxy(conn)
                TimeWalletDatabase.MIGRATION_1_2.migrate(db)
                TimeWalletDatabase.MIGRATION_2_3.migrate(db)
                TimeWalletDatabase.MIGRATION_3_4.migrate(db)
            }
            DriverManager.getConnection(url).use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT layer, tagId, title FROM ledger_records WHERE id='old'").use { rs ->
                        assertTrue(rs.next()); assertEquals("REPORTED_UNTIMED", rs.getString("layer")); assertNull(rs.getString("tagId")); assertEquals("Việc cũ", rs.getString("title"))
                    }
                    st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('tags','goals')").use { rs ->
                        var n = 0; while (rs.next()) n++; assertEquals(2, n)
                    }
                }
            }
            assertTrue(json.getJSONObject("database").getInt("version") == 4)
        } finally {
            f.delete()
        }
    }
}
