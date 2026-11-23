package com.timewallet.mvp

import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.AesGcmKeystoreVault
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** WALLY A15: khóa API không ở dạng rõ trong lưu trữ mới, không vào backup/export/log. */
class WallySecurityStaticTest {

    private val key = "sk-synthetic-security-check-key"

    @Test
    fun `stored value is ciphertext and no preference value contains the plaintext key`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs, InMemoryApiKeyVault())
        assertTrue(store.saveAiPreferences(AiPreferences(optInEnabled = true, apiKey = key)))
        prefs.all.values.forEach { assertFalse(it.toString().contains(key)) }
        assertTrue(prefs.getString(TimerAndSettingsStore.KEY_AI_API_KEY_ENCRYPTED, "")!!.startsWith(AesGcmKeystoreVault.PAYLOAD_PREFIX))
        assertFalse(prefs.contains(TimerAndSettingsStore.KEY_AI_API_KEY_LEGACY))
    }

    @Test
    fun `backup is disabled and data extraction rules exclude preferences and database`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        val backup = File("src/main/res/xml/backup_rules.xml").readText()
        assertTrue(backup.contains("domain=\"sharedpref\"") && backup.contains("domain=\"database\""))
        val extraction = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(extraction.contains("sharedpref") && extraction.contains("database"))
        assertFalse("không cho phép lưu lưu lượng không mã hóa", manifest.contains("usesCleartextTraffic=\"true\""))
        val perms = Regex("""<uses-permission[^>]*android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        val allowedPerms = setOf(
            "android.permission.INTERNET",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
        )
        assertTrue("chỉ xin quyền hợp lệ tối thiểu cho mạng/thông báo/cài APK cập nhật/tiện ích nổi, phát hiện: $perms", perms.isNotEmpty() && perms.all { it in allowedPerms })
        assertFalse("không dùng AccessibilityService đọc màn hình ngoài app", manifest.contains("BIND_ACCESSIBILITY_SERVICE"))
        assertFalse("không xin quyền quét danh sách gói ứng dụng khác", manifest.contains("QUERY_ALL_PACKAGES") || manifest.contains("PACKAGE_USAGE_STATS"))
    }

    @Test
    fun `the JSON exporter and the diagnostics never touch AI preferences or the key`() {
        val store = File("src/main/java/com/timewallet/mvp/data/local/TimerAndSettingsStore.kt").readText()
        val exporter = store.substring(store.indexOf("object LedgerJsonExporter"))
        assertFalse(exporter.contains("apiKey") || exporter.contains("AiPreferences") || exporter.contains("KEY_AI_"))
        File("src/main/java/com/timewallet/mvp/data/diagnostics").listFiles()!!.forEach {
            val t = it.readText()
            assertFalse("${it.name} không được tham chiếu khóa API", Regex("""apiKey|Authorization|Bearer""").containsMatchIn(t))
        }
    }

    @Test
    fun `no key literal is shipped in sources tests fixtures or build files`() {
        val offenders = listOf("src/main", "src/test", "build.gradle.kts").map(::File).flatMap { f ->
            (if (f.isDirectory) f.walkTopDown().filter { it.isFile }.toList() else listOf(f))
        }.filter { file ->
            file.extension in setOf("kt", "xml", "kts", "properties", "json") &&
                Regex("""sk-[A-Za-z0-9]{24,}""").containsMatchIn(file.readText())
        }.map { it.name }
        assertTrue("không có khóa thật trong nguồn: $offenders", offenders.isEmpty())
    }

    @Test
    fun `no release keystore or signing secret file resides inside repository tree`() {
        val repoRoot = File(".").canonicalFile.let { if (it.name == "app") it.parentFile else it }
        val forbidden = repoRoot.walkTopDown()
            .onEnter { dir -> dir.name !in setOf(".git", ".gradle", "build", "out") }
            .filter { file ->
                file.isFile && (
                    file.name.equals("keystore.properties", ignoreCase = true) ||
                        file.extension.lowercase() in setOf("jks", "keystore", "p12", "pkcs12")
                    )
            }
            .map { it.relativeTo(repoRoot).path }
            .toList()
        assertTrue("không đặt keystore/signing secret trong repo: $forbidden", forbidden.isEmpty())
        val gradle = File("build.gradle.kts").readText()
        assertFalse(gradle.contains("storePassword = \""))
        assertFalse(gradle.contains("keyPassword = \""))
    }
}
