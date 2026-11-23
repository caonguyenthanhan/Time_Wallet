package com.timewallet.mvp.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.timewallet.mvp.MainActivity
import com.timewallet.mvp.R
import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.data.local.TimeWalletDatabase
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.data.local.WallyActionController
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.model.ThemeModePreference
import com.timewallet.mvp.domain.usecase.BatchRecordInput
import com.timewallet.mvp.domain.usecase.CurrentWorkMode
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.domain.usecase.WallyHubSnapshot
import com.timewallet.mvp.ui.screens.walletClickable
import com.timewallet.mvp.ui.theme.TimeWalletTheme
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletDivider
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletStroke
import com.timewallet.mvp.ui.theme.WalletWarningBg
import com.timewallet.mvp.ui.theme.WalletWarningText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class FloatingUiMode {
    BUBBLE,
    QUICK_PANEL,
    CHAT
}

private enum class QuickPanelTab(val labelVi: String) {
    TODO("To-do"),
    SCHEDULE("Lịch trình"),
    AI("Wally AI")
}

private data class FloatingChatMsg(
    val isUser: Boolean,
    val text: String,
    val badge: String? = null
)

private class WallyOverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

/**
 * Trợ lý nổi Wally hoạt động trên mọi ứng dụng (opt-in qua SYSTEM_ALERT_WINDOW):
 * - Chạm mascot: mở nhanh Chat Wally.
 * - Nhấn giữ mascot: mở Bảng điều khiển nhanh (Đang làm, Sắp làm, Lịch trình, To-do, Chat, Mở app).
 * - Kéo mascot: di chuyển linh hoạt và tự hít mép trái/phải.
 * - Thu gọn: trở về mascot nhỏ, tuyệt đối không làm mất bộ đếm đang chạy.
 */
class WallyFloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var windowParams: WindowManager.LayoutParams
    private var rootContainer: FrameLayout? = null
    private var lifecycleOwner: WallyOverlayLifecycleOwner? = null

    private val uiModeState = mutableStateOf(FloatingUiMode.BUBBLE)
    private val refreshTick = mutableIntStateOf(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                TimerAndSettingsStore(applicationContext).let { store ->
                    val cur = store.loadFloatingAssistantPrefs()
                    store.saveFloatingAssistantPrefs(cur.copy(enabled = false), immediate = true)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_OPEN_QUICK_PANEL -> {
                if (Settings.canDrawOverlays(this)) {
                    ensureOverlayAttached()
                    switchMode(FloatingUiMode.QUICK_PANEL)
                }
            }

            ACTION_OPEN_CHAT -> {
                if (Settings.canDrawOverlays(this)) {
                    ensureOverlayAttached()
                    switchMode(FloatingUiMode.CHAT)
                }
            }

            ACTION_COLLAPSE -> {
                if (Settings.canDrawOverlays(this)) {
                    ensureOverlayAttached()
                    switchMode(FloatingUiMode.BUBBLE)
                }
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }

        ensureOverlayAttached()
        refreshTick.intValue++
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun switchMode(nextMode: FloatingUiMode) {
        uiModeState.value = nextMode
        updateWindowLayoutForMode(nextMode)
        refreshTick.intValue++
    }

    private fun updateWindowLayoutForMode(mode: FloatingUiMode) {
        val container = rootContainer ?: return
        val metrics = resources.displayMetrics
        val store = TimerAndSettingsStore(applicationContext)
        val savedPrefs = store.loadFloatingAssistantPrefs()

        if (mode == FloatingUiMode.BUBBLE) {
            val bubblePx = (58f * metrics.density).roundToInt()
            val maxX = (metrics.widthPixels - bubblePx).coerceAtLeast(0)
            val maxY = (metrics.heightPixels - bubblePx * 2).coerceAtLeast(100)
            windowParams.gravity = Gravity.TOP or Gravity.START
            windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            windowParams.x = if (savedPrefs.edgeLeft) (8 * metrics.density).roundToInt() else (maxX - (8 * metrics.density).roundToInt()).coerceAtLeast(0)
            windowParams.y = (savedPrefs.yFraction * maxY).roundToInt().coerceIn(80, maxY)
            windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            windowParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            windowParams.x = 0
            windowParams.y = (56f * metrics.density).roundToInt()
            windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        runCatching {
            windowManager.updateViewLayout(container, windowParams)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureOverlayAttached() {
        if (rootContainer != null) return

        val owner = WallyOverlayLifecycleOwner().also { it.onCreate() }
        lifecycleOwner = owner

        val metrics = resources.displayMetrics
        val bubblePx = (58f * metrics.density).roundToInt()
        val savedPrefs = TimerAndSettingsStore(applicationContext).loadFloatingAssistantPrefs()
        val maxX = (metrics.widthPixels - bubblePx).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - bubblePx * 2).coerceAtLeast(100)

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedPrefs.edgeLeft) (8 * metrics.density).roundToInt() else (maxX - (8 * metrics.density).roundToInt()).coerceAtLeast(0)
            y = (savedPrefs.yFraction * maxY).roundToInt().coerceIn(80, maxY)
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        val container = object : FrameLayout(this) {
            private var downRawX = 0f
            private var downRawY = 0f
            private var startWindowX = 0
            private var startWindowY = 0
            private var dragging = false
            private var longPressTriggered = false
            private val longPressRunnable = Runnable {
                if (!dragging && uiModeState.value == FloatingUiMode.BUBBLE) {
                    longPressTriggered = true
                    switchMode(FloatingUiMode.QUICK_PANEL)
                }
            }

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                return uiModeState.value == FloatingUiMode.BUBBLE
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (uiModeState.value != FloatingUiMode.BUBBLE) {
                    return super.onTouchEvent(event)
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startWindowX = windowParams.x
                        startWindowY = windowParams.y
                        dragging = false
                        longPressTriggered = false
                        removeCallbacks(longPressRunnable)
                        postDelayed(longPressRunnable, longPressTimeout)
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!dragging && hypot(dx, dy) > touchSlop) {
                            dragging = true
                            removeCallbacks(longPressRunnable)
                        }
                        if (dragging && !longPressTriggered) {
                            val dm = resources.displayMetrics
                            val maxDragX = (dm.widthPixels - bubblePx).coerceAtLeast(0)
                            val maxDragY = (dm.heightPixels - bubblePx - 24).coerceAtLeast(80)
                            windowParams.x = (startWindowX + dx.roundToInt()).coerceIn(0, maxDragX)
                            windowParams.y = (startWindowY + dy.roundToInt()).coerceIn(48, maxDragY)
                            runCatching { windowManager.updateViewLayout(this, windowParams) }
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        removeCallbacks(longPressRunnable)
                        if (dragging) {
                            val dm = resources.displayMetrics
                            val centerX = windowParams.x + bubblePx / 2
                            val edgeLeft = centerX < dm.widthPixels / 2
                            val snapX = if (edgeLeft) {
                                (8 * dm.density).roundToInt()
                            } else {
                                (dm.widthPixels - bubblePx - (8 * dm.density).roundToInt()).coerceAtLeast(0)
                            }
                            windowParams.x = snapX
                            runCatching { windowManager.updateViewLayout(this, windowParams) }
                            val maxDragY = (dm.heightPixels - bubblePx * 2).coerceAtLeast(100)
                            val yFrac = (windowParams.y.toFloat() / maxDragY.toFloat()).coerceIn(0.08f, 0.85f)
                            TimerAndSettingsStore(applicationContext).saveFloatingAssistantPrefs(
                                TimerAndSettingsStore.FloatingAssistantPrefs(
                                    enabled = true,
                                    edgeLeft = edgeLeft,
                                    yFraction = yFrac
                                )
                            )
                        } else if (!longPressTriggered && event.actionMasked == MotionEvent.ACTION_UP) {
                            switchMode(FloatingUiMode.CHAT)
                        }
                        dragging = false
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }

        container.setViewTreeLifecycleOwner(owner)
        container.setViewTreeViewModelStoreOwner(owner)
        container.setViewTreeSavedStateRegistryOwner(owner)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val mode by uiModeState
                val nonce by refreshTick
                FloatingAssistantRootContent(
                    serviceContext = applicationContext,
                    mode = mode,
                    refreshNonce = nonce,
                    onSwitchMode = ::switchMode,
                    onOpenMainApp = { tab, openChat -> openMainApp(tab, openChat) },
                    onStopAssistant = {
                        TimerAndSettingsStore(applicationContext).let { store ->
                            val cur = store.loadFloatingAssistantPrefs()
                            store.saveFloatingAssistantPrefs(cur.copy(enabled = false), immediate = true)
                        }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                )
            }
        }
        container.addView(composeView)
        rootContainer = container
        runCatching {
            windowManager.addView(container, windowParams)
        }
    }

    private fun removeOverlay() {
        val container = rootContainer ?: return
        rootContainer = null
        runCatching { windowManager.removeViewImmediate(container) }
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    private fun openMainApp(tab: String, openChat: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(WallyActionController.EXTRA_OPEN_TAB, tab)
            putExtra(WallyActionController.EXTRA_OPEN_CHAT, openChat)
        }
        startActivity(intent)
        switchMode(FloatingUiMode.BUBBLE)
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Trợ lý nổi Wally",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Giữ linh vật Wally nổi trên các ứng dụng để điều khiển timer và To-do."
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            5101,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPanelIntent = PendingIntent.getService(
            this,
            5102,
            Intent(this, WallyFloatingService::class.java).apply { action = ACTION_OPEN_QUICK_PANEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            5103,
            Intent(this, WallyFloatingService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_wally_monochrome)
            .setContentTitle("Wally đang đồng hành")
            .setContentText("Chạm bong bóng để chat · Nhấn giữ để mở bảng nhanh")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, "Bảng nhanh", openPanelIntent)
            .addAction(0, "Tắt nổi", stopIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "wally_floating_channel"
        const val NOTIFICATION_ID = 4208

        const val ACTION_STOP_SERVICE = "com.timewallet.mvp.overlay.ACTION_STOP_SERVICE"
        const val ACTION_OPEN_QUICK_PANEL = "com.timewallet.mvp.overlay.ACTION_OPEN_QUICK_PANEL"
        const val ACTION_OPEN_CHAT = "com.timewallet.mvp.overlay.ACTION_OPEN_CHAT"
        const val ACTION_COLLAPSE = "com.timewallet.mvp.overlay.ACTION_COLLAPSE"

        fun start(context: Context, initialAction: String? = null) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, WallyFloatingService::class.java).apply {
                if (initialAction != null) action = initialAction
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WallyFloatingService::class.java))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingAssistantRootContent(
    serviceContext: Context,
    mode: FloatingUiMode,
    refreshNonce: Int,
    onSwitchMode: (FloatingUiMode) -> Unit,
    onOpenMainApp: (tab: String, openChat: Boolean) -> Unit,
    onStopAssistant: () -> Unit
) {
    val store = remember(serviceContext) { TimerAndSettingsStore(serviceContext) }
    val custom = remember(refreshNonce) { store.loadCustomizationSettings() }
    val systemDark = (serviceContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val isDark = when (custom.themeMode) {
        ThemeModePreference.DARK -> true
        ThemeModePreference.LIGHT -> false
        ThemeModePreference.SYSTEM -> systemDark
    }

    var snapshot by remember { mutableStateOf<WallyHubSnapshot?>(null) }
    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var reloadCounter by remember { mutableIntStateOf(0) }
    var actionBanner by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshNonce, reloadCounter) {
        snapshot = WallyActionController.loadHubSnapshot(serviceContext)
    }

    LaunchedEffect(snapshot?.current?.mode, mode) {
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            if (mode != FloatingUiMode.BUBBLE && (snapshot?.current?.mode == CurrentWorkMode.ACTIVE_TIMER)) {
                snapshot = WallyActionController.loadHubSnapshot(serviceContext, nowEpochMs)
            }
            delay(1000L)
        }
    }

    TimeWalletTheme(darkTheme = isDark, fontScaleMultiplier = custom.fontScale.multiplier) {
        when (mode) {
            FloatingUiMode.BUBBLE -> {
                val isTimerRunning = snapshot?.current?.mode == CurrentWorkMode.ACTIVE_TIMER
                val isPaused = snapshot?.current?.mode == CurrentWorkMode.PAUSED_TIMER
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .semantics {
                            contentDescription = "Trợ lý Wally nổi"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = WalletCardBg,
                        shadowElevation = 6.dp,
                        border = BorderStroke(
                            width = if (isTimerRunning) 2.5.dp else 1.5.dp,
                            color = if (isTimerRunning) WalletMintAccent else if (isPaused) WalletWarningText else WalletStroke
                        ),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_wally),
                                contentDescription = "Trợ lý Wally nổi",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                    if (isTimerRunning || isPaused) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isTimerRunning) WalletMintAccent else WalletWarningText)
                                .border(1.5.dp, WalletCardBg, CircleShape)
                        )
                    }
                }
            }

            FloatingUiMode.QUICK_PANEL -> {
                FloatingQuickPanelCard(
                    serviceContext = serviceContext,
                    snapshot = snapshot,
                    actionBanner = actionBanner,
                    onShowBanner = { actionBanner = it },
                    onReload = { reloadCounter++ },
                    onSwitchMode = onSwitchMode,
                    onOpenMainApp = onOpenMainApp,
                    onStopAssistant = onStopAssistant
                )
            }

            FloatingUiMode.CHAT -> {
                FloatingChatCard(
                    serviceContext = serviceContext,
                    snapshot = snapshot,
                    onReload = { reloadCounter++ },
                    onSwitchMode = onSwitchMode,
                    onOpenMainApp = onOpenMainApp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingQuickPanelCard(
    serviceContext: Context,
    snapshot: WallyHubSnapshot?,
    actionBanner: String?,
    onShowBanner: (String?) -> Unit,
    onReload: () -> Unit,
    onSwitchMode: (FloatingUiMode) -> Unit,
    onOpenMainApp: (tab: String, openChat: Boolean) -> Unit,
    onStopAssistant: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(QuickPanelTab.TODO) }
    var quickTodoText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = WalletCardBg,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, WalletStroke),
        modifier = Modifier
            .widthIn(min = 326.dp, max = 358.dp)
            .heightIn(max = 530.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_wally),
                    contentDescription = "Wally",
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wally",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (snapshot != null) {
                        Text(
                            text = "Thực tế ${TimeRange.formatDuration(snapshot.actualRecordedMinutes)} · Trống ${TimeRange.formatDuration(snapshot.freeRemainingMinutes)}",
                            fontSize = 10.sp,
                            color = WalletMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FloatingPillButton(label = "Chat", accent = true) {
                    onSwitchMode(FloatingUiMode.CHAT)
                }
                FloatingPillButton(label = "App") {
                    onOpenMainApp("schedule", false)
                }
                FloatingPillButton(label = "—") {
                    onSwitchMode(FloatingUiMode.BUBBLE)
                }
                FloatingPillButton(label = "Tắt", danger = true, onClick = onStopAssistant)
            }

            if (!actionBanner.isNullOrBlank()) {
                Text(
                    text = actionBanner,
                    fontSize = 11.sp,
                    color = WalletMintAccent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
            }

            // Current + Upcoming card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = WalletMintSoft,
                border = BorderStroke(1.dp, WalletStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val cur = snapshot?.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (cur?.mode) {
                                CurrentWorkMode.ACTIVE_TIMER -> "● ĐANG LÀM"
                                CurrentWorkMode.PAUSED_TIMER -> "⏸ TẠM DỪNG"
                                CurrentWorkMode.CURRENT_PLANNED -> "◷ THEO LỊCH"
                                else -> "✓ SẴN SÀNG"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletMintAccent
                        )
                        if (cur?.mode == CurrentWorkMode.ACTIVE_TIMER || cur?.mode == CurrentWorkMode.PAUSED_TIMER) {
                            Text(
                                text = formatHms(cur.elapsedSeconds),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletInk
                            )
                        }
                    }
                    Text(
                        text = cur?.title ?: snapshot?.todoItems?.firstOrNull { !it.isDone }?.title ?: "Chưa có phiên đo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = cur?.subtitleVi ?: snapshot?.nextFreeWindowLabel?.let { "Khoảng trống kế: $it" } ?: "Chạm ▶ để đo ngay",
                        fontSize = 11.sp,
                        color = WalletMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val primaryLabel = when (cur?.mode) {
                            CurrentWorkMode.ACTIVE_TIMER -> "⏸ Tạm dừng"
                            CurrentWorkMode.PAUSED_TIMER -> "▶ Tiếp tục"
                            else -> "▶ Bắt đầu đo"
                        }
                        Button(
                            onClick = {
                                if (busy) return@Button
                                busy = true
                                scope.launch {
                                    val res = when (cur?.mode) {
                                        CurrentWorkMode.ACTIVE_TIMER -> WallyActionController.pauseActiveTimer(serviceContext)
                                        CurrentWorkMode.PAUSED_TIMER -> WallyActionController.resumePausedTimer(serviceContext)
                                        CurrentWorkMode.CURRENT_PLANNED -> WallyActionController.startTimerForTaskOrPlan(
                                            serviceContext, cur.title, cur.category, taskId = cur.taskId
                                        )
                                        else -> {
                                            val topTodo = snapshot?.todoItems?.firstOrNull { !it.isDone }
                                            if (topTodo != null) {
                                                WallyActionController.startTimerForTaskOrPlan(
                                                    serviceContext, topTodo.title, topTodo.category,
                                                    taskId = topTodo.id, targetMinutes = topTodo.estimatedMinutes
                                                )
                                            } else {
                                                onOpenMainApp("focus", false)
                                                null
                                            }
                                        }
                                    }
                                    if (res != null) onShowBanner(res.messageVi)
                                    onReload()
                                    busy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(primaryLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (cur?.mode == CurrentWorkMode.ACTIVE_TIMER || cur?.mode == CurrentWorkMode.PAUSED_TIMER) {
                            OutlinedButton(
                                onClick = {
                                    if (busy) return@OutlinedButton
                                    busy = true
                                    scope.launch {
                                        val res = WallyActionController.stopAndSaveActiveTimer(serviceContext)
                                        onShowBanner(res.messageVi)
                                        onReload()
                                        busy = false
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text("⏹ Lưu", fontSize = 11.sp, color = WalletInk)
                            }
                        }
                    }

                    snapshot?.upcoming?.let { up ->
                        HorizontalDivider(color = WalletDivider, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            text = "Sắp làm: ${up.startHm} · ${up.title} (còn ${up.startsInMinutes}p)",
                            fontSize = 11.sp,
                            color = WalletInk,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Tab selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickPanelTab.entries.forEach { tab ->
                    FloatingPillButton(
                        label = tab.labelVi,
                        accent = selectedTab == tab
                    ) {
                        selectedTab = tab
                    }
                }
            }

            // Tab Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (selectedTab) {
                    QuickPanelTab.TODO -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = quickTodoText,
                                onValueChange = { quickTodoText = it },
                                placeholder = { Text("Thêm To-do (vd: Soạn báo cáo mai 15h 45p)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (quickTodoText.isBlank() || busy) return@Button
                                    val raw = quickTodoText
                                    quickTodoText = ""
                                    busy = true
                                    scope.launch {
                                        val res = WallyActionController.quickAddNaturalTask(serviceContext, raw)
                                        onShowBanner(res.messageVi)
                                        onReload()
                                        busy = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val todos = snapshot?.todoItems.orEmpty()
                        if (todos.isEmpty()) {
                            Text(
                                text = "Chưa có To-do nào đang mở.",
                                fontSize = 12.sp,
                                color = WalletMuted,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            todos.take(8).forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = WalletBg,
                                    border = BorderStroke(1.dp, WalletStroke),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FloatingPillButton(label = if (item.isDone) "✓" else "○") {
                                            if (busy) return@FloatingPillButton
                                            busy = true
                                            scope.launch {
                                                val res = WallyActionController.toggleTaskDone(serviceContext, item.id)
                                                onShowBanner(res.messageVi)
                                                onReload()
                                                busy = false
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = WalletInk,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val meta = buildList {
                                                add(item.priority.badgeText)
                                                item.dueLabel?.let { add(it) }
                                                item.estimatedMinutes?.let { add("${it}p") }
                                                if (item.hasRiskWarning && !item.riskLabel.isNullOrBlank()) {
                                                    add("⚠ ${item.riskLabel}")
                                                }
                                            }.joinToString(" · ")
                                            Text(
                                                text = meta,
                                                fontSize = 10.sp,
                                                color = if (item.hasRiskWarning) WalletWarningText else WalletMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        FloatingPillButton(label = "▶", accent = true) {
                                            if (busy) return@FloatingPillButton
                                            busy = true
                                            scope.launch {
                                                val res = WallyActionController.startTimerForTaskOrPlan(
                                                    serviceContext,
                                                    item.title,
                                                    item.category,
                                                    taskId = item.id,
                                                    targetMinutes = item.estimatedMinutes
                                                )
                                                onShowBanner(res.messageVi)
                                                onReload()
                                                busy = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    QuickPanelTab.SCHEDULE -> {
                        val timeline = snapshot?.todayTimeline.orEmpty()
                        Text(
                            text = snapshot?.nextFreeWindowLabel?.let { "Khoảng trống sắp tới: $it" }
                                ?: "Tổng trống còn lại hôm nay: ${TimeRange.formatDuration(snapshot?.freeRemainingMinutes ?: 0)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WalletMintAccent
                        )
                        if (timeline.isEmpty()) {
                            Text(
                                text = "Chưa có hoạt động nào trên timeline hôm nay.",
                                fontSize = 12.sp,
                                color = WalletMuted
                            )
                        } else {
                            timeline.forEach { blk ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (blk.isPlanned) WalletWarningBg else WalletBg,
                                    border = BorderStroke(1.dp, WalletStroke),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .walletClickable("Mở lịch trình") {
                                            onOpenMainApp("schedule", false)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${blk.startHm}–${blk.endHm} · ${blk.title}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = WalletInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (blk.isPlanned) "Dự kiến" else "Thực tế",
                                            fontSize = 10.sp,
                                            color = if (blk.isPlanned) WalletWarningText else WalletMintAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    QuickPanelTab.AI -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = WalletBg,
                            border = BorderStroke(1.dp, WalletStroke),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (snapshot?.isCloudAiInsight == true) "✦ Nhận xét từ Wally AI" else "⚡ Tổng hợp nhanh trên máy",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletMintAccent
                                )
                                Text(
                                    text = snapshot?.wallyInsightText ?: "Đang tải trạng thái...",
                                    fontSize = 12.sp,
                                    color = WalletInk
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FloatingPillButton(label = "💬 Chat Wally", accent = true) {
                                        onSwitchMode(FloatingUiMode.CHAT)
                                    }
                                    FloatingPillButton(label = "Mở kế hoạch AI To-do") {
                                        onOpenMainApp("tasks", false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingChatCard(
    serviceContext: Context,
    snapshot: WallyHubSnapshot?,
    onReload: () -> Unit,
    onSwitchMode: (FloatingUiMode) -> Unit,
    onOpenMainApp: (tab: String, openChat: Boolean) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var messages by remember {
        mutableStateOf(
            listOf(
                FloatingChatMsg(
                    isUser = false,
                    text = "Chào bạn! Hỏi tôi về lịch hôm nay, việc cần ưu tiên hoặc ghi nhanh hoạt động (mọi ghi chép đều cần bạn xác nhận)."
                )
            )
        )
    }
    var pendingProposals by remember { mutableStateOf<List<ActivityProposal>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun sendQuery(rawQuery: String) {
        val text = rawQuery.trim()
        if (text.isEmpty() || sending) return
        sending = true
        inputText = ""
        messages = messages + FloatingChatMsg(isUser = true, text = text)
        scope.launch {
            val store = TimerAndSettingsStore(serviceContext)
            val aiPrefs = store.loadAiPreferences()
            val outcome = GeminiProposalClient.analyzeInput(
                userText = text,
                today = LocalDate.now(),
                nowTime = LocalTime.now(),
                timezoneId = ZoneId.systemDefault().id,
                optInEnabled = aiPrefs.optInEnabled,
                baseUrl = aiPrefs.baseUrl,
                apiKey = aiPrefs.apiKey,
                modelName = aiPrefs.actualModelDisplay(),
                trustedHost = aiPrefs.trustedHost
            )
            val replyText = when {
                outcome.source == OutcomeSource.NOT_CONFIGURED ->
                    "Chưa bật/cấu hình API key Wally Cloud AI trong Cài đặt. Bạn có thể mở app để nhập API key hoặc dùng To-do nhanh ở Bảng điều khiển."
                !outcome.reply.isNullOrBlank() -> outcome.reply
                outcome.proposals.isNotEmpty() ->
                    "Wally gợi ý ${outcome.proposals.size} hoạt động dưới đây — chạm ✓ Xác nhận để lưu vào Sổ cái."
                outcome.warnings.isNotEmpty() -> outcome.warnings.first()
                else -> GeminiProposalClient.SAFE_FALLBACK_WARNING_VI
            }
            messages = (messages + FloatingChatMsg(
                isUser = false,
                text = replyText,
                badge = if (outcome.source == OutcomeSource.CLOUD) "Wally AI" else null
            )).takeLast(12)
            pendingProposals = outcome.proposals
            sending = false
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = WalletCardBg,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, WalletStroke),
        modifier = Modifier
            .widthIn(min = 310.dp, max = 342.dp)
            .heightIn(max = 460.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_wally),
                    contentDescription = "Wally",
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chat nhanh · Wally",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalletInk
                    )
                    if (snapshot?.current != null) {
                        Text(
                            text = "${snapshot.current.title} · Trống ${TimeRange.formatDuration(snapshot.freeRemainingMinutes)}",
                            fontSize = 10.sp,
                            color = WalletMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FloatingPillButton(label = "Bảng nhanh") {
                    onSwitchMode(FloatingUiMode.QUICK_PANEL)
                }
                FloatingPillButton(label = "—") {
                    onSwitchMode(FloatingUiMode.BUBBLE)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Hôm nay làm gì trước?", "Hôm nay đã ghi gì?", "Vừa học 30p").forEach { chip ->
                    FloatingPillButton(label = chip) {
                        sendQuery(chip)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 120.dp, max = 210.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                messages.forEach { m ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (m.isUser) WalletMintSoft else WalletBg,
                        border = BorderStroke(1.dp, WalletStroke),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            if (!m.badge.isNullOrBlank()) {
                                Text(
                                    text = "✦ ${m.badge}",
                                    fontSize = 10.sp,
                                    color = WalletMintAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = m.text,
                                fontSize = 12.sp,
                                color = WalletInk
                            )
                        }
                    }
                }

                pendingProposals.forEach { prop ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WalletMintSoft,
                        border = BorderStroke(1.dp, WalletMintAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prop.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalletInk
                                )
                                Text(
                                    text = "${prop.dateLocal} · ${prop.durationSeconds / 60L}p" +
                                        if (prop.startLocal != null && prop.endLocal != null) " (${prop.startLocal}–${prop.endLocal})" else "",
                                    fontSize = 10.sp,
                                    color = WalletMuted
                                )
                            }
                            FloatingPillButton(label = "✓ Xác nhận", accent = true) {
                                scope.launch {
                                    val db = TimeWalletDatabase.getInstance(serviceContext)
                                    val store = TimerAndSettingsStore(serviceContext)
                                    val input = BatchRecordInput(
                                        id = prop.id,
                                        source = "chat",
                                        rawTitle = prop.title,
                                        rawCategory = prop.category,
                                        dateLocal = prop.dateLocal,
                                        durationMinutes = (prop.durationSeconds / 60L).toInt().coerceAtLeast(1),
                                        startHm = prop.startLocal,
                                        endHm = prop.endLocal,
                                        convertFutureToPlanned = prop.isFuturePlanned,
                                        idempotencyKey = prop.id
                                    )
                                    val res = BatchConfirm.run(
                                        dao = db.ledgerDao(),
                                        inputs = listOf(input),
                                        activeTimer = store.loadActiveTimer(),
                                        readActiveTimer = { store.loadActiveTimer() },
                                        nowEpochMs = System.currentTimeMillis(),
                                        zone = ZoneId.systemDefault()
                                    )
                                    val notice = when (res) {
                                        is ConfirmResult.Committed -> "✓ Đã ghi \"${prop.title}\" (${res.savedCount} mục)."
                                        is ConfirmResult.Aborted -> "Không ghi được: ${res.message}"
                                        is ConfirmResult.Invalid -> "Không hợp lệ: ${res.message}"
                                    }
                                    pendingProposals = pendingProposals.filterNot { it.id == prop.id }
                                    messages = (messages + FloatingChatMsg(isUser = false, text = notice)).takeLast(12)
                                    WallyActionController.notifyAllWidgets(serviceContext)
                                    onReload()
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Nhắn Wally...", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { sendQuery(inputText) },
                    enabled = !sending && inputText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletMint, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (sending) "..." else "Gửi", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Mở Chat đầy đủ trong Time Wallet ↗",
                    fontSize = 11.sp,
                    color = WalletMintAccent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.walletClickable("Mở trong app") {
                        onOpenMainApp("schedule", true)
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingPillButton(
    label: String,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = when {
            accent -> WalletMintSoft
            danger -> WalletWarningBg
            else -> WalletBg
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (accent) WalletMintAccent else WalletStroke
        ),
        modifier = Modifier.walletClickable(label, onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (accent || danger) FontWeight.Bold else FontWeight.Medium,
            color = when {
                danger -> WalletDangerText
                accent -> WalletMintAccent
                else -> WalletInk
            },
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            maxLines = 1
        )
    }
}

private fun formatHms(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    val sec = s % 60L
    return if (h > 0L) "%02d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
