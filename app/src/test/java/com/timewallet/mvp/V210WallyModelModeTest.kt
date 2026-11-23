package com.timewallet.mvp

import com.timewallet.mvp.data.local.AesGcmKeystoreVault
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.data.local.WallyModelPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V210WallyModelModeTest {

    @Test
    fun `default Wally mode is Pro backed by Claude Sonnet 4_6 and Flash maps to Claude Haiku 4_5`() {
        val def = AiPreferences()
        assertEquals("cinb/claude-sonnet-4-6", def.modelName)
        assertEquals(WallyModelPreset.PRO, def.resolvedPreset())
        assertEquals("Wally Pro", def.modeTitle())
        assertEquals("Claude Sonnet 4.6", def.actualModelDisplay())

        val flash = def.copy(modelName = AiPreferences.FLASH_AI_MODEL)
        assertEquals("hoang/claude-haiku-4.5", flash.modelName)
        assertEquals(WallyModelPreset.FLASH, flash.resolvedPreset())
        assertEquals("Wally Flash", flash.modeTitle())
        assertEquals("Claude Haiku 4.5", flash.actualModelDisplay())
        assertFalse(flash.actualModelDisplay().contains("Gemini", ignoreCase = true))

        val custom = def.copy(modelName = "custom/model-x")
        assertNull(custom.resolvedPreset())
        assertEquals("custom/model-x", custom.actualModelDisplay())
    }

    @Test
    fun `switching between Wally Pro and Wally Flash preserves encrypted API key and origin binding`() {
        val prefs = InMemorySharedPreferences()
        val store = TimerAndSettingsStore(prefs, InMemoryApiKeyVault())
        val syntheticKey = "sk-synthetic-model-mode-check"

        assertTrue(
            store.saveAiPreferences(
                AiPreferences(
                    optInEnabled = true,
                    baseUrl = "https://api.foza.ai/v1",
                    apiKey = syntheticKey
                )
            )
        )
        val cipherBefore = prefs.getString(TimerAndSettingsStore.KEY_AI_API_KEY_ENCRYPTED, null)
        assertTrue(cipherBefore != null && cipherBefore.startsWith(AesGcmKeystoreVault.PAYLOAD_PREFIX))

        assertTrue(store.saveAiModelName(WallyModelPreset.FLASH.modelId))
        val afterFlash = store.loadAiPreferences()
        assertEquals(AiPreferences.FLASH_AI_MODEL, afterFlash.modelName)
        assertEquals(WallyModelPreset.FLASH, afterFlash.resolvedPreset())
        assertEquals(syntheticKey, afterFlash.apiKey)
        assertEquals("api.foza.ai", afterFlash.trustedHost)
        assertTrue(afterFlash.optInEnabled)
        assertEquals(cipherBefore, prefs.getString(TimerAndSettingsStore.KEY_AI_API_KEY_ENCRYPTED, null))

        assertTrue(store.saveAiModelName("   "))
        val fallbackPro = store.loadAiPreferences()
        assertEquals(AiPreferences.DEFAULT_AI_MODEL, fallbackPro.modelName)
        assertEquals(WallyModelPreset.PRO, fallbackPro.resolvedPreset())
    }

    @Test
    fun `UI exposes Wally Pro and Wally Flash with real Claude model labels underneath and keeps Auto locked`() {
        val chat = File("src/main/java/com/timewallet/mvp/ui/screens/ChatScreen.kt").readText()
        val dialogs = File("src/main/java/com/timewallet/mvp/ui/screens/ComponentsAndDialogs.kt").readText()
        val profile = File("src/main/java/com/timewallet/mvp/ui/screens/ProfileSettingsScreen.kt").readText()
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        val main = File("src/main/java/com/timewallet/mvp/MainActivity.kt").readText()

        assertTrue(chat.contains("internal fun WallyModelModeSelector("))
        assertTrue(Regex("""text\s*=\s*preset\.displayTitle""").containsMatchIn(chat))
        assertTrue(Regex("""text\s*=\s*preset\.actualModelName""").containsMatchIn(chat))
        assertTrue(chat.contains("Auto (Tự chọn Pro/Flash): Tạm khóa"))

        val aiDlg = dialogs.substring(dialogs.indexOf("fun AiSettingsDialog("), dialogs.indexOf("fun ProductInfoDialog("))
        assertFalse("AiSettingsDialog không còn chip Gemini 3.8", aiDlg.contains("cinb/gemini-3.8-flash"))
        assertTrue(aiDlg.contains("WallyModelModeSelector("))

        assertTrue(profile.contains("Chế độ Wally (Claude)") && profile.contains("WallyModelModeSelector("))
        assertTrue(vm.contains("fun selectWallyPreset(preset: WallyModelPreset)"))
        assertTrue(main.contains("onSelectWallyPreset = viewModel::selectWallyPreset"))
    }

    @Test
    fun `AiProviderPreset detects known OpenAI-compatible hosts and GeminiProposalClient parses models JSON`() {
        assertEquals(
            com.timewallet.mvp.data.local.AiProviderPreset.OPENAI,
            com.timewallet.mvp.data.local.AiProviderPreset.detectFromBaseUrl("https://api.openai.com/v1")
        )
        assertEquals(
            com.timewallet.mvp.data.local.AiProviderPreset.GEMINI,
            com.timewallet.mvp.data.local.AiProviderPreset.detectFromBaseUrl("https://generativelanguage.googleapis.com/v1beta/openai")
        )
        assertEquals(
            com.timewallet.mvp.data.local.AiProviderPreset.CUSTOM,
            com.timewallet.mvp.data.local.AiProviderPreset.detectFromBaseUrl("https://llm.internal.example/v1")
        )
        val parsed = com.timewallet.mvp.data.ai.GeminiProposalClient.parseModelIdsJson(
            """{"data":[{"id":"gpt-4o-mini"},{"id":"gpt-4o"},{"id":"gpt-4o-mini"}]}"""
        )
        assertEquals(listOf("gpt-4o-mini", "gpt-4o"), parsed)
    }
}
