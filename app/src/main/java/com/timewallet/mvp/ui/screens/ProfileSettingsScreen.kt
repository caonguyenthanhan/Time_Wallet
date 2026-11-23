package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.timewallet.mvp.data.local.AiPreferences
import com.timewallet.mvp.data.local.WallyModelPreset
import com.timewallet.mvp.domain.model.AiReadPermissions
import com.timewallet.mvp.domain.model.CustomizationSettings
import com.timewallet.mvp.domain.model.FontScalePreference
import com.timewallet.mvp.domain.model.LocalCategoryRule
import com.timewallet.mvp.domain.model.ThemeModePreference
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.AppUpdateUiState
import com.timewallet.mvp.domain.usecase.FocusPinPolicy
import com.timewallet.mvp.domain.usecase.FocusPinState
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletDivider
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletIsDark
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMintTint
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
import com.timewallet.mvp.ui.theme.WalletStroke

/**
 * Trang Hồ sơ, Giới thiệu & Cài đặt toàn màn hình (Wave 3, lấy cảm hứng từ Rolly Settings + linh vật Wally).
 */
@Composable
fun ProfileSettingsScreen(
    aiEnabled: Boolean,
    aiPreferences: AiPreferences = AiPreferences(),
    onSelectWallyPreset: (WallyModelPreset) -> Unit = {},
    hasCrashReport: Boolean,
    tagsCount: Int,
    goalsCount: Int,
    onBack: () -> Unit,
    onOpenIntro: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAiSettings: () -> Unit,
    focusPinEnabled: Boolean = false,
    focusPinPreview: FocusPinState = FocusPinPolicy.resolve(emptyList(), null, System.currentTimeMillis()),
    onToggleFocusPin: (Boolean) -> Unit = {},
    customization: CustomizationSettings = CustomizationSettings(),
    aiReadPermissions: AiReadPermissions = AiReadPermissions(),
    localRules: List<LocalCategoryRule> = emptyList(),
    onUpdateCustomization: ((CustomizationSettings) -> CustomizationSettings) -> Unit = {},
    onUpdateAiPermissions: ((AiReadPermissions) -> AiReadPermissions) -> Unit = {},
    onAddLocalRule: (keyword: String, categoryKey: String, markWaste: Boolean) -> Unit = { _, _, _ -> },
    onDeleteLocalRule: (String) -> Unit = {},
    appUpdate: AppUpdateUiState = AppUpdateUiState(),
    onOpenAppUpdate: () -> Unit = {},
    floatingAssistantEnabled: Boolean = false,
    onToggleFloatingAssistant: (Boolean) -> Unit = {},
    onRequestOverlayPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canDrawOverlays by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = android.provider.Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val versionLabel = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.0"
        }.getOrDefault("2.2.0")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(WalletCardBg)
                        .border(1.dp, WalletStroke, CircleShape)
                        .semantics { contentDescription = "Đóng Hồ sơ & Cài đặt" }
                        .walletClickable(onClickLabel = "Đóng Hồ sơ & Cài đặt", onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(WalletGlyph.BACK, color = WalletInk, size = 18.dp)
                }
                Text(
                    text = "Hồ sơ & Cài đặt",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WalletInk
                )
            }

            val settingsScrollState = rememberScrollState()
            val focusManager = LocalFocusManager.current
            LaunchedEffect(Unit) {
                focusManager.clearFocus(force = true)
                settingsScrollState.scrollTo(0)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(settingsScrollState)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Thẻ nhận diện Wally gọn theo phong cách card nổi không viền.
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = WalletCardBg,
                    shadowElevation = if (WalletIsDark) 0.dp else 1.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WallyMascotAvatar(size = 52.dp, contentDescription = "Linh vật Wally")
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Time Wallet v$versionLabel",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WalletInk
                            )
                            Text(
                                text = "Hiểu cách bạn dùng 24 giờ — trung thực, không phán xét.",
                                fontSize = 12.sp,
                                color = WalletMuted
                            )
                        }
                    }
                }

                SettingsGroupSection(title = "Giao diện & Module") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Chủ đề giao diện", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemeModePreference.entries.forEach { mode ->
                                val isSel = customization.themeMode == mode
                                WalletPill(
                                    onClickLabel = "Chủ đề ${mode.labelVi}",
                                    containerColor = if (isSel) WalletMintDark else WalletSoftNeutral,
                                    borderColor = if (isSel) WalletMintDark else WalletStroke,
                                    selected = isSel,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    onClick = { onUpdateCustomization { it.copy(themeMode = mode) } }
                                ) {
                                    Text(
                                        text = mode.labelVi,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color.White else WalletInk,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = WalletDivider, modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cỡ chữ hiển thị", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                            Text(
                                text = "Hiện tại: ${customization.fontScale.labelVi}",
                                fontSize = 11.sp,
                                color = WalletMuted
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FontScalePreference.entries.forEach { scale ->
                                val isSel = customization.fontScale == scale
                                WalletPill(
                                    onClickLabel = "Cỡ chữ ${scale.labelVi}",
                                    containerColor = if (isSel) WalletMintDark else WalletSoftNeutral,
                                    borderColor = if (isSel) WalletMintDark else WalletStroke,
                                    selected = isSel,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                                    onClick = { onUpdateCustomization { it.copy(fontScale = scale) } }
                                ) {
                                    Text(
                                        text = scale.labelVi,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color.White else WalletInk,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = WalletDivider)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Công cụ trên tab Công việc & Tập trung",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMintAccent
                        )
                        SettingsSwitchRow(
                            title = "Bảng Kanban",
                            subtitle = "Quản lý việc theo cột Cần làm · Đang làm · Xong",
                            checked = customization.enableKanban,
                            onCheckedChange = { v -> onUpdateCustomization { it.copy(enableKanban = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Ma trận Eisenhower",
                            subtitle = "Sắp xếp việc theo mức Gấp & Quan trọng",
                            checked = customization.enableEisenhower,
                            onCheckedChange = { v -> onUpdateCustomization { it.copy(enableEisenhower = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Thói quen (Habits)",
                            subtitle = "Theo dõi chuỗi 7 ngày & tự đánh dấu từ Sổ cái",
                            checked = customization.enableHabits,
                            onCheckedChange = { v -> onUpdateCustomization { it.copy(enableHabits = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Chế độ Pomodoro",
                            subtitle = "Đặt mục tiêu phiên tập trung 25p / 45p",
                            checked = customization.enablePomodoro,
                            onCheckedChange = { v -> onUpdateCustomization { it.copy(enablePomodoro = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Xúc xắc thời gian",
                            subtitle = "Gợi ý phiên ngẫu nhiên theo mức năng lượng",
                            checked = customization.enableDice,
                            onCheckedChange = { v -> onUpdateCustomization { it.copy(enableDice = v) } }
                        )
                        HorizontalDivider(color = WalletDivider, modifier = Modifier.padding(vertical = 2.dp))
                        Text(
                            text = "Đồng hành Wally & màn hình chính",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMintAccent
                        )
                        SettingsSwitchRow(
                            title = "Trợ lý Wally AI",
                            subtitle = "Nút mở nhanh Wally ở thanh trên cùng",
                            checked = customization.enableAiAssistant,
                            onCheckedChange = { v -> onUpdateCustomization { it.copy(enableAiAssistant = v) } }
                        )
                        if (canDrawOverlays) {
                            SettingsSwitchRow(
                                title = "Trợ lý nổi trên ứng dụng khác",
                                subtitle = "Bong bóng Wally nổi · Chạm Chat · Nhấn giữ mở Bảng nhanh",
                                checked = floatingAssistantEnabled,
                                onCheckedChange = onToggleFloatingAssistant
                            )
                        } else {
                            HorizontalDivider(color = WalletDivider)
                            SettingsRowItem(
                                glyph = WalletGlyph.SETTINGS,
                                title = "Trợ lý nổi trên ứng dụng khác",
                                subtitle = "Chạm để cấp quyền Hiển thị trên ứng dụng khác (SYSTEM_ALERT_WINDOW)",
                                badgeText = "Cấp quyền",
                                onClick = onRequestOverlayPermission
                            )
                        }
                        HorizontalDivider(color = WalletDivider)
                        SettingsRowItem(
                            glyph = WalletGlyph.GOAL,
                            title = "Ghim Widget Wally lên màn hình chính",
                            subtitle = "Xem Đang làm, Sắp làm, thời gian trống & To-do ngay ngoài màn hình chính",
                            badgeText = "Widget",
                            onClick = {
                                val mgr = android.appwidget.AppWidgetManager.getInstance(context)
                                val provider = android.content.ComponentName(
                                    context,
                                    com.timewallet.mvp.widget.WallyHomeWidgetProvider::class.java
                                )
                                if (mgr.isRequestPinAppWidgetSupported) {
                                    mgr.requestPinAppWidget(provider, null, null)
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Nhấn giữ màn hình chính → chọn Widget → Wally · Lịch & To-do.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                }

                SettingsGroupSection(title = "Trợ lý AI & Quyền dữ liệu") {
                    SettingsRowItem(
                        glyph = WalletGlyph.SETTINGS,
                        title = "Cấu hình Wally AI",
                        subtitle = if (aiEnabled) {
                            "${aiPreferences.modeTitle()} (${aiPreferences.actualModelDisplay()}) · đã kết nối API"
                        } else {
                            "Chưa bật API · đang dùng phân tích cục bộ"
                        },
                        badgeText = if (aiEnabled) "Bật" else "Tắt",
                        onClick = onOpenAiSettings
                    )
                    HorizontalDivider(color = WalletDivider)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Chế độ Wally (Claude)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                        WallyModelModeSelector(
                            currentModelName = aiPreferences.modelName,
                            onSelectPreset = onSelectWallyPreset,
                            compact = false,
                            showAutoFutureHint = false
                        )
                    }
                    HorizontalDivider(color = WalletDivider)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Quyền đọc dữ liệu của AI", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
                        Text(
                            text = "Kiểm soát những phần dữ liệu nào AI được phép đọc khi tóm tắt hoặc đề xuất.",
                            fontSize = 11.5.sp,
                            color = WalletMuted
                        )
                        SettingsSwitchRow(
                            title = "Sổ cái thời gian (24h)",
                            subtitle = "Lịch sử các khối giờ đã thực hiện & dự kiến",
                            checked = aiReadPermissions.readLedger,
                            onCheckedChange = { v -> onUpdateAiPermissions { it.copy(readLedger = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Công việc & Inbox",
                            subtitle = "Danh sách việc cần xếp lịch hoặc chia nhỏ",
                            checked = aiReadPermissions.readTasks,
                            onCheckedChange = { v -> onUpdateAiPermissions { it.copy(readTasks = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Thói quen",
                            subtitle = "Tiến độ hoàn thành thói quen theo ngày",
                            checked = aiReadPermissions.readHabits,
                            onCheckedChange = { v -> onUpdateAiPermissions { it.copy(readHabits = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Mục tiêu & Phản tư",
                            subtitle = "Mục tiêu MIN/MAX và tiến độ kỳ",
                            checked = aiReadPermissions.readGoals,
                            onCheckedChange = { v -> onUpdateAiPermissions { it.copy(readGoals = v) } }
                        )
                    }
                    HorizontalDivider(color = WalletDivider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (focusPinEnabled) WalletMintSoft else WalletMintTint),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(glyph = WalletGlyph.NOTIFY, color = WalletMintAccent, size = 18.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ghim thông báo tập trung",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletInk
                            )
                            Text(
                                text = if (focusPinEnabled) focusPinPreview.headline else "Hiện việc theo kế hoạch hoặc gợi ý tuần trước · hôm qua · 3 ngày",
                                fontSize = 11.5.sp,
                                color = WalletMuted
                            )
                        }
                        Switch(
                            checked = focusPinEnabled,
                            onCheckedChange = onToggleFocusPin,
                            colors = walletSwitchColors(WalletMint)
                        )
                    }
                }

                var newRuleKw by rememberSaveable { mutableStateOf("") }
                var newRuleCatKey by rememberSaveable { mutableStateOf(TimeCategory.WORK.key) }
                var newRuleWaste by rememberSaveable { mutableStateOf(false) }
                SettingsGroupSection(title = "Quy tắc phân loại cục bộ (Offline)") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tự gán nhóm danh mục khi ghi nhanh có chứa từ khóa (không cần AI).",
                            fontSize = 11.5.sp,
                            color = WalletMuted
                        )
                        localRules.forEach { rule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(WalletSoftNeutral)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "\"${rule.keyword}\" → ${rule.category.displayName}${if (rule.markWasteByDefault) " (Lãng phí)" else ""}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WalletInk,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Xóa",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletDangerText,
                                    modifier = Modifier.walletClickable(onClickLabel = "Xóa quy tắc ${rule.keyword}") {
                                        onDeleteLocalRule(rule.id)
                                    }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = newRuleKw,
                                onValueChange = { newRuleKw = it },
                                placeholder = { Text("Từ khóa (VD: lướt tiktok)", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (newRuleKw.isNotBlank()) {
                                        onAddLocalRule(newRuleKw, newRuleCatKey, newRuleWaste)
                                        newRuleKw = ""
                                        newRuleWaste = false
                                    }
                                },
                                enabled = newRuleKw.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = WalletMintDark, contentColor = Color.White),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("+ Thêm", fontWeight = FontWeight.Bold)
                            }
                        }
                        CategoryChipRow(selectedKey = newRuleCatKey, onSelectCategory = { newRuleCatKey = it })
                        SettingsSwitchRow(
                            title = "Mặc định đánh dấu Lãng phí",
                            subtitle = "Tự đánh dấu tầng Lãng phí khi khớp từ khóa này",
                            checked = newRuleWaste,
                            onCheckedChange = { newRuleWaste = it }
                        )
                    }
                }

                SettingsGroupSection(title = "Dữ liệu & Tuỳ chỉnh") {
                    SettingsRowItem(
                        glyph = WalletGlyph.GOAL,
                        title = "Mục tiêu cá nhân",
                        subtitle = "Đang theo dõi $goalsCount mục tiêu",
                        onClick = onOpenGoals
                    )
                    HorizontalDivider(color = WalletDivider)
                    SettingsRowItem(
                        glyph = WalletGlyph.LIST,
                        title = "Quản lý Tag & Danh mục",
                        subtitle = "$tagsCount tag đang hoạt động",
                        onClick = onOpenTags
                    )
                    HorizontalDivider(color = WalletDivider)
                    SettingsRowItem(
                        glyph = WalletGlyph.MORE,
                        title = "Dữ liệu & Sao lưu JSON",
                        subtitle = "Xuất JSON, khôi phục sao lưu an toàn, nạp mẫu",
                        onClick = onOpenData
                    )
                }

                SettingsGroupSection(title = "Khám phá Time Wallet") {
                    SettingsRowItem(
                        glyph = WalletGlyph.SPARK,
                        title = "Giới thiệu ứng dụng & Triết lý",
                        subtitle = "Xem lại 4 trang hướng dẫn nhanh cách dùng app",
                        onClick = onOpenIntro
                    )
                    HorizontalDivider(color = WalletDivider)
                    SettingsRowItem(
                        glyph = WalletGlyph.BOOK,
                        title = "Cách hiểu 5 trạng thái & Lịch trình",
                        subtitle = "Chú giải sổ cái, nhật ký lỗi và thông tin bản dựng",
                        badgeText = if (hasCrashReport) "Có nhật ký" else null,
                        onClick = onOpenInfo
                    )
                    HorizontalDivider(color = WalletDivider)
                    val updateCand = appUpdate.availableCandidate
                    SettingsRowItem(
                        glyph = WalletGlyph.ARROW_UP,
                        title = "Cập nhật ứng dụng (GitHub Releases)",
                        subtitle = if (updateCand != null) {
                            "Có bản mới ${updateCand.tagName} · hiện tại v$versionLabel"
                        } else {
                            "Kiểm tra phiên bản mới · hiện tại v$versionLabel"
                        },
                        badgeText = if (updateCand != null) "Mới" else null,
                        onClick = onOpenAppUpdate
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WalletInk)
            Text(subtitle, fontSize = 11.sp, color = WalletMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = walletSwitchColors(WalletMintDark)
        )
    }
}

@Composable
private fun SettingsGroupSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = WalletMuted,
            modifier = Modifier.padding(start = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = WalletCardBg,
            shadowElevation = if (WalletIsDark) 0.dp else 1.5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRowItem(
    glyph: WalletGlyph,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .walletClickable(onClickLabel = title, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(WalletMintTint),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph = glyph, color = WalletMintAccent, size = 20.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = WalletInk)
            Text(text = subtitle, fontSize = 12.sp, color = WalletMuted, lineHeight = 16.sp)
        }
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(WalletMintSoft)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(badgeText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WalletMintAccent)
            }
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(WalletSoftNeutral),
            contentAlignment = Alignment.Center
        ) {
            Text("›", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WalletMuted)
        }
    }
}

private data class IntroSlide(
    val stepTitle: String,
    val headline: String,
    val body: String,
    val highlight: String
)

/**
 * Onboarding 4 bước giới thiệu triết lý & cách hoạt động của Time Wallet (luôn có nút Bỏ qua).
 */
@Composable
fun AppIntroDialog(
    onDismiss: () -> Unit
) {
    val slides = remember {
        listOf(
            IntroSlide(
                stepTitle = "1 / 4 · Triết lý 24 giờ",
                headline = "Hiểu 24 giờ của bạn, không phán xét nghỉ ngơi",
                body = "Mỗi ngày đều có 24 giờ. Time Wallet tách rõ thời gian cho Mục tiêu, sinh hoạt phục hồi (ngủ, nghỉ ngơi, giải trí vừa đủ) và chỉ tính Lãng phí khi bạn tự đánh dấu.",
                highlight = "Cân bằng giữa mục tiêu và phục hồi bền vững"
            ),
            IntroSlide(
                stepTitle = "2 / 4 · Sổ cái trung thực",
                headline = "Không bao giờ tự bịa giờ bắt đầu hay kết thúc",
                body = "Hoạt động có mốc giờ hiển thị thẳng trên Lịch trình. Việc chỉ có thời lượng (như \"Đọc sách 30p\") nằm ở khay riêng để bạn tự đặt giờ sau.",
                highlight = "Tách bạch rõ ràng: Đã đo · Có giờ · Chưa gắn giờ · Dự kiến"
            ),
            IntroSlide(
                stepTitle = "3 / 4 · Dự kiến vs Thực tế",
                headline = "Soi chiếu kế hoạch với điều đã diễn ra",
                body = "Xem Ngày, 3 ngày, Tuần hoặc Tháng. Chạm giữ để kéo ô giờ trống, đổi giờ trực tiếp và đối chiếu Kế hoạch vs Thực tế song song.",
                highlight = "Giờ dự kiến không bao giờ tự ghi đè giờ thực tế"
            ),
            IntroSlide(
                stepTitle = "4 / 4 · Wally đồng hành mọi lúc",
                headline = "Tập trung có chủ đích cùng trợ lý AI",
                body = "Wally hỗ trợ qua Widget, Bảng nổi và AI To-do — gợi ý việc cần ưu tiên, mọi thay đổi luôn chờ bạn bấm xác nhận.",
                highlight = "Wally chỉ đề xuất · Bạn giữ toàn quyền quyết định"
            )
        )
    }
    var page by remember { mutableIntStateOf(0) }
    val current = slides[page]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WalletBg)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = WalletCardBg,
                border = BorderStroke(1.dp, WalletStroke),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = current.stepTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WalletMintAccent
                        )
                        Text(
                            text = "Bỏ qua",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .walletClickable(onClickLabel = "Bỏ qua giới thiệu ứng dụng", onClick = onDismiss)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    WallyMascotAvatar(size = 76.dp, contentDescription = "Wally")

                    Text(
                        text = current.headline,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalletInk,
                        textAlign = TextAlign.Center,
                        lineHeight = 25.sp
                    )

                    Text(
                        text = current.body,
                        fontSize = 13.5.sp,
                        color = WalletMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.5.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = WalletMintTint,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✦ ${current.highlight}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMintAccent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        slides.indices.forEach { idx ->
                            Box(
                                modifier = Modifier
                                    .width(if (idx == page) 22.dp else 8.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (idx == page) WalletMint else WalletStroke)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (page > 0) {
                            OutlinedButton(
                                onClick = { page-- },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(0.9f).heightIn(min = 48.dp)
                            ) {
                                Text("‹ Trước", fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = {
                                if (page < slides.lastIndex) page++ else onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.4f).heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = if (page < slides.lastIndex) "Tiếp theo ›" else "Bắt đầu ngay",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
