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
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.timewallet.mvp.data.local.BatchConfirm
import com.timewallet.mvp.data.local.ConfirmResult
import com.timewallet.mvp.data.local.TimeWalletDatabase
import com.timewallet.mvp.data.local.TimerAndSettingsStore
import com.timewallet.mvp.data.local.WallyActionController
import com.timewallet.mvp.domain.model.TagAssignmentProposal
import com.timewallet.mvp.domain.model.TagAssignmentUndoToken
import com.timewallet.mvp.domain.model.ThemeModePreference
import com.timewallet.mvp.domain.model.TimeCategory
import com.timewallet.mvp.domain.usecase.ChatHistoryEntry
import com.timewallet.mvp.domain.usecase.CurrentWorkMode
import com.timewallet.mvp.domain.usecase.LedgerAnalyticsCalculator
import com.timewallet.mvp.domain.usecase.TagRules
import com.timewallet.mvp.domain.usecase.TimeRange
import com.timewallet.mvp.domain.usecase.WallyHubSnapshot
import java.util.UUID
import com.timewallet.mvp.ui.EditableProposalDraft
import com.timewallet.mvp.ui.screens.walletClickable
import com.timewallet.mvp.ui.theme.TimeWalletTheme
import com.timewallet.mvp.ui.theme.WalletBg
import com.timewallet.mvp.ui.theme.WalletCardBg
import com.timewallet.mvp.ui.theme.WalletDangerBg
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletDivider
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletIsDark
import com.timewallet.mvp.ui.theme.WalletMint
import com.timewallet.mvp.ui.theme.WalletMintAccent
import com.timewallet.mvp.ui.theme.WalletMintDark
import com.timewallet.mvp.ui.theme.WalletMintSoft
import com.timewallet.mvp.ui.theme.WalletMuted
import com.timewallet.mvp.ui.theme.WalletSoftNeutral
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

private enum class QuickPanelDrawer {
    OVERVIEW,
    ADD_TODO,
    SCHEDULE,
    AI
}

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
    private var mainAppInForeground = false

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

            ACTION_MAIN_APP_FOREGROUND -> {
                mainAppInForeground = true
                rootContainer?.let { container ->
                    runCatching {
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(container.windowToken, 0)
                    }
                    container.clearFocus()
                }
                if (uiModeState.value != FloatingUiMode.BUBBLE) {
                    switchMode(FloatingUiMode.BUBBLE)
                }
                rootContainer?.visibility = android.view.View.GONE
                return START_STICKY
            }

            ACTION_MAIN_APP_BACKGROUND -> {
                mainAppInForeground = false
                if (Settings.canDrawOverlays(this)) {
                    ensureOverlayAttached()
                    rootContainer?.visibility = android.view.View.VISIBLE
                    switchMode(FloatingUiMode.BUBBLE)
                }
            }

            ACTION_OPEN_QUICK_PANEL -> {
                if (Settings.canDrawOverlays(this)) {
                    mainAppInForeground = false
                    ensureOverlayAttached()
                    rootContainer?.visibility = android.view.View.VISIBLE
                    switchMode(FloatingUiMode.QUICK_PANEL)
                }
            }

            ACTION_OPEN_CHAT -> {
                if (Settings.canDrawOverlays(this)) {
                    mainAppInForeground = false
                    ensureOverlayAttached()
                    rootContainer?.visibility = android.view.View.VISIBLE
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
            windowParams.y = if (mode == FloatingUiMode.CHAT) {
                (8f * metrics.density).roundToInt()
            } else {
                (22f * metrics.density).roundToInt()
            }
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
        if (mainAppInForeground) {
            container.visibility = android.view.View.GONE
        }
        rootContainer = container
        isAttached = true
        runCatching {
            windowManager.addView(container, windowParams)
        }
    }

    private fun removeOverlay() {
        isAttached = false
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
        const val ACTION_MAIN_APP_FOREGROUND = "com.timewallet.mvp.overlay.ACTION_MAIN_APP_FOREGROUND"
        const val ACTION_MAIN_APP_BACKGROUND = "com.timewallet.mvp.overlay.ACTION_MAIN_APP_BACKGROUND"

        @Volatile
        var isAttached: Boolean = false
            private set

        fun onMainAppResumed(context: Context) {
            if (!isAttached) return
            runCatching {
                context.startService(Intent(context, WallyFloatingService::class.java).apply {
                    action = ACTION_MAIN_APP_FOREGROUND
                })
            }
        }

        fun onMainAppBackgrounded(context: Context) {
            val enabled = runCatching {
                TimerAndSettingsStore(context.applicationContext).loadFloatingAssistantPrefs().enabled
            }.getOrDefault(false)
            if (!enabled || !Settings.canDrawOverlays(context)) return
            if (!isAttached) return
            runCatching {
                context.startService(Intent(context, WallyFloatingService::class.java).apply {
                    action = ACTION_MAIN_APP_BACKGROUND
                })
            }
        }

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
                val ringTint = when {
                    isTimerRunning -> WalletMintAccent
                    isPaused -> WalletWarningText
                    else -> WalletMint.copy(alpha = 0.45f)
                }
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .semantics {
                            contentDescription = "Trợ lý Wally nổi"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isTimerRunning || isPaused) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(ringTint.copy(alpha = 0.16f))
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = WalletCardBg,
                        shadowElevation = 8.dp,
                        border = BorderStroke(
                            width = if (isTimerRunning) 2.dp else 1.2.dp,
                            color = ringTint
                        ),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(WalletMintSoft.copy(alpha = 0.35f), WalletCardBg)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_wally),
                                contentDescription = "Trợ lý Wally nổi",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
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
                                .background(ringTint)
                                .border(2.dp, WalletCardBg, CircleShape)
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
    var activeDrawer by remember { mutableStateOf(QuickPanelDrawer.OVERVIEW) }
    var quickTodoText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val quickInputFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isNarrowScreen = cfg.screenWidthDp < 360
    val panelMaxWidthDp = (cfg.screenWidthDp - 12).coerceIn(296, 348).dp
    val panelMaxHeightDp = when {
        activeDrawer == QuickPanelDrawer.ADD_TODO -> (cfg.screenHeightDp * 0.44f).coerceIn(240f, 300f).dp
        else -> (cfg.screenHeightDp * 0.72f).coerceIn(320f, 450f).dp
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = WalletCardBg,
        shadowElevation = 12.dp,
        border = BorderStroke(0.8.dp, WalletStroke),
        modifier = Modifier
            .widthIn(min = 292.dp, max = panelMaxWidthDp)
            .fillMaxWidth(0.95f)
            .heightIn(max = panelMaxHeightDp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (isNarrowScreen) 9.dp else 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Top bar siêu gọn 1 hàng — bảo toàn chữ "Wally" không bị cắt thành "Wall" ở 320dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 3.dp else 4.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_wally),
                    contentDescription = "Wally",
                    modifier = Modifier
                        .size(if (isNarrowScreen) 20.dp else 22.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = "Wally",
                    fontSize = if (isNarrowScreen) 12.5.sp else 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalletInk,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 42.dp)
                )
                FloatingPillButton(label = "Chat", accent = true) {
                    onSwitchMode(FloatingUiMode.CHAT)
                }
                FloatingPillButton(label = "Thu gọn", contentDescriptionLabel = "Thu gọn bảng nổi") {
                    onSwitchMode(FloatingUiMode.BUBBLE)
                }
                FloatingPillButton(
                    label = "Tắt nổi",
                    danger = true,
                    contentDescriptionLabel = "Tắt trợ lý nổi",
                    onClick = onStopAssistant
                )
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

            // 1. Đang làm & 2. Sắp làm — thẻ hero phân tầng surface rõ ràng, đồng hồ lớn dễ đọc ngay lập tức
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = WalletMintSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val cur = snapshot?.current
                    val titleDisplay = cur?.title ?: snapshot?.todoItems?.firstOrNull { !it.isDone }?.title ?: "Chưa có phiên đo"
                    val stateTag = when (cur?.mode) {
                        CurrentWorkMode.ACTIVE_TIMER -> "● Đang làm"
                        CurrentWorkMode.PAUSED_TIMER -> "⏸ Tạm dừng"
                        CurrentWorkMode.CURRENT_PLANNED -> "◷ Theo lịch"
                        else -> "✓ Sẵn sàng"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$stateTag · $titleDisplay",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val rightMetric = when {
                            cur?.mode == CurrentWorkMode.ACTIVE_TIMER || cur?.mode == CurrentWorkMode.PAUSED_TIMER ->
                                formatHms(cur.elapsedSeconds)
                            snapshot != null ->
                                "Ghi ${TimeRange.formatDuration(snapshot.actualRecordedMinutes)}"
                            else -> ""
                        }
                        if (rightMetric.isNotEmpty()) {
                            Text(
                                text = rightMetric,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = WalletMintAccent,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    if (snapshot != null && (cur?.mode == CurrentWorkMode.ACTIVE_TIMER || cur?.mode == CurrentWorkMode.PAUSED_TIMER)) {
                        Text(
                            text = "Ghi ${TimeRange.formatDuration(snapshot.actualRecordedMinutes)} · ${cur.subtitleVi}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WalletMintAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (activeDrawer != QuickPanelDrawer.ADD_TODO) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val primaryIcon = when (cur?.mode) {
                                CurrentWorkMode.ACTIVE_TIMER -> "⏸"
                                CurrentWorkMode.PAUSED_TIMER -> "▶"
                                else -> "▶"
                            }
                            val primaryAria = when (cur?.mode) {
                                CurrentWorkMode.ACTIVE_TIMER -> "⏸ Tạm dừng"
                                CurrentWorkMode.PAUSED_TIMER -> "▶ Tiếp tục"
                                else -> "▶ Đo ngay"
                            }
                            FloatingPillButton(
                                label = primaryIcon,
                                contentDescriptionLabel = primaryAria,
                                accent = true
                            ) {
                                if (busy) return@FloatingPillButton
                                busy = true
                                scope.launch {
                                    val res = when (cur?.mode) {
                                        CurrentWorkMode.ACTIVE_TIMER -> WallyActionController.pauseActiveTimer(serviceContext)
                                        CurrentWorkMode.PAUSED_TIMER -> WallyActionController.resumePausedTimer(serviceContext)
                                        CurrentWorkMode.CURRENT_PLANNED -> WallyActionController.startTimerForTaskOrPlan(
                                            serviceContext, cur.title, cur.category, taskId = cur.taskId, tagId = cur.tagId
                                        )
                                        else -> {
                                            val topTodo = snapshot?.todoItems?.firstOrNull { !it.isDone }
                                            if (topTodo != null) {
                                                WallyActionController.startTimerForTaskOrPlan(
                                                    serviceContext, topTodo.title, topTodo.category,
                                                    taskId = topTodo.id, targetMinutes = topTodo.estimatedMinutes,
                                                    tagId = topTodo.tagId
                                                )
                                            } else {
                                                onOpenMainApp("focus", false)
                                                null
                                            }
                                        }
                                    }
                                    // Chỉ hiện banner khi bị chặn/lỗi; khi thành công thì thẻ trạng thái bên dưới đã phản ánh trực tiếp
                                    onShowBanner(if (res is WallyActionController.ActionOutcome.Blocked) res.messageVi else null)
                                    onReload()
                                    busy = false
                                }
                            }
                            if (cur?.mode == CurrentWorkMode.ACTIVE_TIMER || cur?.mode == CurrentWorkMode.PAUSED_TIMER) {
                                FloatingPillButton(
                                    label = "⏹",
                                    contentDescriptionLabel = "⏹ Lưu"
                                ) {
                                    if (busy) return@FloatingPillButton
                                    busy = true
                                    scope.launch {
                                        val res = WallyActionController.stopAndSaveActiveTimer(serviceContext)
                                        onShowBanner(if (res is WallyActionController.ActionOutcome.Blocked) res.messageVi else null)
                                        onReload()
                                        busy = false
                                    }
                                }
                            }
                            val nextCompact = snapshot?.upcoming?.let { up ->
                                "Kế: ${up.startHm} ${up.title}"
                            } ?: snapshot?.nextFreeWindowLabel?.let {
                                "Trống: $it"
                            } ?: "Trống ${TimeRange.formatDuration(snapshot?.freeRemainingMinutes ?: 0)}"
                            Text(
                                text = nextCompact,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = WalletInk.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. Việc cần xử lý — chuyển đổi mượt giữa các drawer trên nền surface phân tầng không viền
            AnimatedContent(
                targetState = activeDrawer,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "floatingPanelDrawer",
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 228.dp)
            ) { drawer ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (drawer) {
                    QuickPanelDrawer.OVERVIEW -> {
                        val todos = snapshot?.todoItems.orEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (todos.isEmpty()) "Việc cần xử lý" else "Việc cần xử lý (${todos.size})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletMuted
                            )
                            FloatingPillButton(
                                label = "＋",
                                contentDescriptionLabel = "+ Thêm nhanh",
                                accent = true
                            ) {
                                activeDrawer = QuickPanelDrawer.ADD_TODO
                            }
                        }

                        if (todos.isEmpty()) {
                            Text(
                                text = "Chưa có To-do nào đang mở.",
                                fontSize = 11.5.sp,
                                color = WalletMuted,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        } else {
                            todos.take(4).forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(13.dp),
                                    color = WalletSoftNeutral,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FloatingPillButton(
                                            label = if (item.isDone) "✓" else "○",
                                            contentDescriptionLabel = if (item.isDone) "✓" else "○"
                                        ) {
                                            if (busy) return@FloatingPillButton
                                            busy = true
                                            scope.launch {
                                                val res = WallyActionController.toggleTaskDone(serviceContext, item.id)
                                                onShowBanner(if (res is WallyActionController.ActionOutcome.Blocked) res.messageVi else null)
                                                onReload()
                                                busy = false
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
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
                                            }.joinToString(" · ")
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                if (meta.isNotBlank()) {
                                                    Text(
                                                        text = meta,
                                                        fontSize = 10.sp,
                                                        color = WalletMuted,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                                if (item.hasRiskWarning && !item.riskLabel.isNullOrBlank()) {
                                                    val isOverdue = item.riskLabel.contains("Quá hạn", ignoreCase = true)
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(5.dp))
                                                            .background(if (isOverdue) WalletDangerBg else WalletWarningBg)
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = "⚠ ${item.riskLabel}",
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isOverdue) WalletDangerText else WalletWarningText,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }
                                            }
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
                                                    targetMinutes = item.estimatedMinutes,
                                                    tagId = item.tagId
                                                )
                                                onShowBanner(if (res is WallyActionController.ActionOutcome.Blocked) res.messageVi else null)
                                                onReload()
                                                busy = false
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingPillButton(
                                label = "Lịch hôm nay",
                                modifier = Modifier.weight(1f),
                                centerContent = true
                            ) {
                                activeDrawer = QuickPanelDrawer.SCHEDULE
                            }
                            FloatingPillButton(
                                label = "✦ Gợi ý",
                                modifier = Modifier.weight(1f),
                                centerContent = true
                            ) {
                                activeDrawer = QuickPanelDrawer.AI
                            }
                            if (todos.size > 4) {
                                FloatingPillButton(label = "Mở hết ↗") {
                                    onOpenMainApp("tasks", false)
                                }
                            } else {
                                FloatingPillButton(label = "Mở app ↗") {
                                    onOpenMainApp("schedule", false)
                                }
                            }
                        }
                    }

                    QuickPanelDrawer.ADD_TODO -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingPillButton(label = "← Việc cần làm") {
                                activeDrawer = QuickPanelDrawer.OVERVIEW
                            }
                        }
                        val hasInput = quickTodoText.isNotBlank()
                        val submitQuickTodo = {
                            if (!busy) {
                                val raw = quickTodoText.trim()
                                if (raw.isEmpty()) {
                                    runCatching { quickInputFocusRequester.requestFocus() }
                                } else {
                                    quickTodoText = ""
                                    busy = true
                                    scope.launch {
                                        val res = WallyActionController.quickAddNaturalTask(serviceContext, raw)
                                        onShowBanner(if (res is WallyActionController.ActionOutcome.Blocked) res.messageVi else null)
                                        onReload()
                                        activeDrawer = QuickPanelDrawer.OVERVIEW
                                        busy = false
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = quickTodoText,
                            onValueChange = { quickTodoText = it },
                            placeholder = { Text("Ví dụ: Soạn báo cáo mai 15h 45p P1", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitQuickTodo() }),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = WalletInk,
                                unfocusedTextColor = WalletInk,
                                focusedContainerColor = WalletSoftNeutral,
                                unfocusedContainerColor = WalletSoftNeutral,
                                focusedBorderColor = WalletMint,
                                unfocusedBorderColor = WalletStroke
                            ),
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (hasInput) WalletMint else WalletMintSoft)
                                        .semantics { contentDescription = "+" }
                                        .walletClickable(
                                            onClickLabel = if (hasInput) "Thêm công việc" else "Bắt đầu nhập công việc"
                                        ) {
                                            submitQuickTodo()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (hasInput) Color.White else WalletMintAccent
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(quickInputFocusRequester)
                        )
                    }

                    QuickPanelDrawer.SCHEDULE -> {
                        val timeline = snapshot?.todayTimeline.orEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingPillButton(label = "← Việc cần làm") {
                                activeDrawer = QuickPanelDrawer.OVERVIEW
                            }
                            FloatingPillButton(label = "Mở lịch ↗", accent = true) {
                                onOpenMainApp("schedule", false)
                            }
                        }
                        Text(
                            text = snapshot?.nextFreeWindowLabel?.let { "Khoảng trống kế: $it" }
                                ?: "Trống còn lại hôm nay: ${TimeRange.formatDuration(snapshot?.freeRemainingMinutes ?: 0)}",
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
                                    shape = RoundedCornerShape(11.dp),
                                    color = if (blk.isPlanned) WalletWarningBg else WalletSoftNeutral,
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

                    QuickPanelDrawer.AI -> {
                        FloatingPillButton(label = "← Việc cần làm") {
                            activeDrawer = QuickPanelDrawer.OVERVIEW
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = WalletSoftNeutral,
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
                                    FloatingPillButton(label = "Mở AI To-do") {
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
    val session = remember { WallyFloatingChatSession() }
    var snapshotData by remember { mutableStateOf<FloatingChatSnapshotData?>(null) }

    val defaultGreeting = remember {
        FloatingChatMsg(
            isUser = false,
            text = "Chào bạn! Hỏi tôi về lịch hôm nay, việc cần ưu tiên hoặc xếp lịch gắn Tag (mọi ghi chép đều cần bạn xác nhận)."
        )
    }

    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf(defaultGreeting)) }
    var pendingProposals by remember { mutableStateOf<List<EditableProposalDraft>>(emptyList()) }
    var pendingTagAssignments by remember { mutableStateOf<List<TagAssignmentProposal>>(emptyList()) }
    var expandedTagDraftId by remember { mutableStateOf<String?>(null) }
    var tagSearchQuery by remember(expandedTagDraftId) { mutableStateOf("") }
    val hubVersion by WallyActionController.hubStateVersion.collectAsState()

    fun syncSharedStateFromStore(fresh: FloatingChatSnapshotData?) {
        val store = TimerAndSettingsStore(serviceContext.applicationContext)
        val persistedDrafts = session.loadPersistedDrafts(serviceContext)
        val persistedTagAssigns = store.loadPendingTagAssignments()
        val persistedConv = store.loadChatConversationSnapshot()
        if (fresh != null) {
            session.coordinator.restorePersistedSnapshot(persistedConv, fresh.tags, fresh.perms)
        }
        pendingProposals = persistedDrafts
        pendingTagAssignments = persistedTagAssigns
        val persistedHistory = store.loadChatHistory()
        if (persistedHistory.isNotEmpty()) {
            val mapped = persistedHistory.takeLast(14).map { h ->
                FloatingChatMsg(
                    isUser = h.isUser,
                    text = buildString {
                        append(h.headline)
                        h.lines.forEach { append('\n').append("• ").append(it) }
                        h.notes.forEach { append('\n').append(it) }
                    },
                    badge = h.label,
                    isClarification = h.isClarification,
                    suggestions = if (h.isClarification && h == persistedHistory.lastOrNull()) {
                        persistedConv?.clarificationSuggestions.orEmpty()
                    } else emptyList()
                )
            }
            val currentUndoMsgs = messages.filter { it.undoToken != null && !it.undoToken.undone }
            messages = if (currentUndoMsgs.isNotEmpty()) {
                val lastH = mapped.lastOrNull()?.text
                val extraUndo = currentUndoMsgs.filter { u -> u.text != lastH }
                (mapped + extraUndo).takeLast(14)
            } else {
                mapped
            }
        } else if (persistedConv?.clarificationQuestion != null) {
            messages = listOf(
                defaultGreeting,
                FloatingChatMsg(
                    isUser = false,
                    text = persistedConv.clarificationQuestion,
                    badge = "Cần làm rõ lịch",
                    isClarification = true,
                    suggestions = persistedConv.clarificationSuggestions
                )
            )
        }
    }

    LaunchedEffect(hubVersion) {
        val fresh = runCatching { session.loadContextData(serviceContext) }.getOrNull()
        snapshotData = fresh
        if (!sending) {
            syncSharedStateFromStore(fresh)
        }
    }

    val scope = rememberCoroutineScope()

    fun buildHistoryEntryFromFloating(msg: FloatingChatMsg): ChatHistoryEntry = ChatHistoryEntry(
        id = UUID.randomUUID().toString(),
        isUser = msg.isUser,
        headline = msg.text.lineSequence().firstOrNull().orEmpty(),
        lines = msg.text.lineSequence().drop(1).map { it.removePrefix("• ").trim() }.filter { it.isNotEmpty() }.toList(),
        timeText = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
        isClarification = msg.isClarification,
        label = msg.badge ?: if (msg.isUser) "Wally đã xem ✓✓" else null
    )

    fun sendQuery(rawQuery: String) {
        val text = rawQuery.trim()
        if (text.isEmpty() || sending) return
        sending = true
        inputText = ""
        val userMsg = FloatingChatMsg(isUser = true, text = text)
        messages = (messages + userMsg).takeLast(14)
        scope.launch {
            val freshData = session.loadContextData(serviceContext)
            snapshotData = freshData
            val res = session.processTurn(
                userText = text,
                currentDrafts = pendingProposals,
                data = freshData
            )
            res.replyMessage?.let { msg ->
                messages = (messages + msg).takeLast(14)
            }
            pendingProposals = res.updatedDrafts
            if (res.tagAssignmentProposals.isNotEmpty()) {
                val newIds = res.tagAssignmentProposals.mapTo(HashSet()) { it.recordId }
                pendingTagAssignments = pendingTagAssignments.filterNot { it.recordId in newIds } + res.tagAssignmentProposals
            }
            if (expandedTagDraftId != null && pendingProposals.none { it.id == expandedTagDraftId }) {
                expandedTagDraftId = null
            }
            val addedHistory = buildList {
                if (res.outcome.fault == null) {
                    add(buildHistoryEntryFromFloating(userMsg))
                    res.replyMessage?.let { add(buildHistoryEntryFromFloating(it)) }
                }
            }
            session.persistSharedState(
                context = serviceContext,
                drafts = pendingProposals,
                pendingTagAssignments = pendingTagAssignments,
                perms = freshData.perms,
                clarificationQuestion = if (res.replyMessage?.isClarification == true) res.replyMessage.text else null,
                clarificationSuggestions = if (res.replyMessage?.isClarification == true) res.replyMessage.suggestions else emptyList(),
                newHistoryEntries = addedHistory
            )
            sending = false
        }
    }

    val cfg = LocalConfiguration.current
    val isShortViewport = cfg.screenHeightDp <= 600
    val chatMaxWidthDp = (cfg.screenWidthDp - 20).coerceIn(288, 342).dp
    val chatMaxHeightDp = if (isShortViewport) {
        310.dp
    } else {
        (cfg.screenHeightDp * 0.50f).coerceIn(300f, 410f).dp
    }
    val chatListScroll = rememberScrollState()
    LaunchedEffect(messages.size, pendingProposals.size, pendingTagAssignments.size, expandedTagDraftId) {
        chatListScroll.animateScrollTo(chatListScroll.maxValue)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = WalletCardBg,
        shadowElevation = 12.dp,
        border = BorderStroke(0.8.dp, WalletStroke),
        modifier = Modifier
            .widthIn(min = 280.dp, max = chatMaxWidthDp)
            .fillMaxWidth(0.94f)
            .heightIn(max = chatMaxHeightDp)
    ) {
        Column(
            modifier = Modifier.padding(if (isShortViewport) 9.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isShortViewport) 5.dp else 8.dp)
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 34.dp) {
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
                            text = "Chat Wally",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalletInk,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (snapshot?.current != null && !isShortViewport) {
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
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Hôm nay làm gì trước?", "Hôm nay đã ghi gì?", "Vừa học 30p").forEach { chip ->
                    FloatingPillButton(label = chip) {
                        sendQuery(chip)
                    }
                }
            }

            val activeTags = snapshotData?.tags?.filter { !it.isArchived }.orEmpty()
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = if (isShortViewport) 72.dp else 108.dp, max = if (isShortViewport) 136.dp else 218.dp)
                    .verticalScroll(chatListScroll),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                messages.forEachIndexed { msgIdx, m ->
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = if (m.isUser) WalletMintSoft else WalletSoftNeutral,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (!m.badge.isNullOrBlank()) {
                                Text(
                                    text = "✦ ${m.badge}",
                                    fontSize = 10.sp,
                                    color = if (m.isClarification) WalletWarningText else WalletMintAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = m.text,
                                fontSize = 12.sp,
                                color = WalletInk
                            )
                            val undoTok = m.undoToken
                            if (undoTok != null && !undoTok.undone) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    FloatingPillButton(label = "↶ Hoàn tác", danger = true) {
                                        scope.launch {
                                            val undoMsg = session.undoTagAssignment(serviceContext, undoTok)
                                            messages = (messages.mapIndexed { idx, item ->
                                                if (idx == msgIdx && item.undoToken != null) {
                                                    item.copy(undoToken = item.undoToken.copy(undone = true))
                                                } else item
                                            } + undoMsg).takeLast(14)
                                            val fresh = session.loadContextData(serviceContext)
                                            snapshotData = fresh
                                            session.persistSharedState(
                                                context = serviceContext,
                                                drafts = pendingProposals,
                                                pendingTagAssignments = pendingTagAssignments,
                                                perms = fresh.perms,
                                                newHistoryEntries = listOf(buildHistoryEntryFromFloating(undoMsg))
                                            )
                                            onReload()
                                        }
                                    }
                                }
                            }
                            if (m.suggestions.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    m.suggestions.forEach { suggestion ->
                                        FloatingPillButton(
                                            label = suggestion,
                                            accent = m.isClarification
                                        ) {
                                            sendQuery(suggestion)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                pendingTagAssignments.forEach { assign ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WalletMintSoft,
                        border = BorderStroke(1.dp, WalletMintAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Gắn Tag cho phiên đã lưu",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletMintAccent
                            )
                            Text(
                                text = assign.recordTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalletInk
                            )
                            Text(
                                text = "${assign.recordDateLocal} · ${assign.recordTimeLabel}\nTag: ${assign.previousTagName ?: "Chưa gắn"} → ${assign.newTagName ?: "Không gắn Tag"}",
                                fontSize = 10.5.sp,
                                color = WalletMuted
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FloatingPillButton(label = "✕ Bỏ") {
                                    val nextAssigns = pendingTagAssignments.filterNot { it.id == assign.id }
                                    pendingTagAssignments = nextAssigns
                                    snapshotData?.let { data ->
                                        session.persistSharedState(serviceContext, pendingProposals, nextAssigns, data.perms)
                                    }
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                FloatingPillButton(label = "✓ Duyệt gắn Tag", accent = true) {
                                    scope.launch {
                                        val resMsg = session.approveTagAssignment(serviceContext, assign)
                                        val nextAssigns = pendingTagAssignments.filterNot { it.id == assign.id }
                                        pendingTagAssignments = nextAssigns
                                        messages = (messages + resMsg).takeLast(14)
                                        val fresh = session.loadContextData(serviceContext)
                                        snapshotData = fresh
                                        session.persistSharedState(
                                            context = serviceContext,
                                            drafts = pendingProposals,
                                            pendingTagAssignments = nextAssigns,
                                            perms = fresh.perms,
                                            newHistoryEntries = listOf(buildHistoryEntryFromFloating(resMsg))
                                        )
                                        onReload()
                                    }
                                }
                            }
                        }
                    }
                }

                pendingProposals.forEach { draft ->
                    val draftTag = draft.tagId?.let { id -> activeTags.firstOrNull { it.id == id } }
                    val cat = TimeCategory.fromKeyOrDefault(draft.categoryKey)
                    val isTagOpen = expandedTagDraftId == draft.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WalletMintSoft,
                        border = BorderStroke(1.dp, WalletMintAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = draft.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WalletInk
                                    )
                                    Text(
                                        text = buildString {
                                            append("${cat.displayName} · ${draft.dateLocal} · ${draft.durationMinutes}p")
                                            if (draft.startLocal.isNotBlank() && draft.endLocal.isNotBlank()) {
                                                append(" (${draft.startLocal}–${draft.endLocal})")
                                            }
                                            if (draftTag != null) append(" · 🏷 ${draftTag.name}")
                                        },
                                        fontSize = 10.sp,
                                        color = WalletMuted
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FloatingPillButton(
                                    label = if (isTagOpen) "Đóng Tag" else (draftTag?.let { "🏷 ${it.name}" } ?: "+ Tag")
                                ) {
                                    expandedTagDraftId = if (isTagOpen) null else draft.id
                                }
                                FloatingPillButton(label = "✕") {
                                    val data = snapshotData
                                    val next = if (data != null) {
                                        session.removeDraft(pendingProposals, draft.id, data.tags, data.perms)
                                    } else {
                                        pendingProposals.filterNot { it.id == draft.id }
                                    }
                                    pendingProposals = next
                                    if (expandedTagDraftId == draft.id) expandedTagDraftId = null
                                    if (data != null) {
                                        session.persistSharedState(serviceContext, next, pendingTagAssignments, data.perms)
                                    }
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                FloatingPillButton(label = "✓ Xác nhận", accent = true) {
                                    scope.launch {
                                        val db = TimeWalletDatabase.getInstance(serviceContext)
                                        val store = TimerAndSettingsStore(serviceContext)
                                        val freshData = session.loadContextData(serviceContext)
                                        snapshotData = freshData
                                        val input = session.buildConfirmInput(draft)
                                        val res = try {
                                            BatchConfirm.run(
                                                dao = db.ledgerDao(),
                                                inputs = listOf(input),
                                                activeTimer = freshData.activeTimer ?: store.loadActiveTimer(),
                                                readActiveTimer = { store.loadActiveTimer() },
                                                nowEpochMs = System.currentTimeMillis(),
                                                zone = ZoneId.systemDefault()
                                            )
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (_: Throwable) {
                                            ConfirmResult.Aborted("Lỗi lưu cục bộ — vui lòng thử lại.")
                                        }
                                        val noticeMsg = when (res) {
                                            is ConfirmResult.Committed -> {
                                                val next = session.removeDraft(
                                                    pendingProposals, draft.id, freshData.tags, freshData.perms
                                                )
                                                pendingProposals = next
                                                if (expandedTagDraftId == draft.id) expandedTagDraftId = null
                                                val okMsg = FloatingChatMsg(
                                                    isUser = false,
                                                    text = "✓ Đã ghi \"${draft.title}\" (${res.savedCount} mục).",
                                                    badge = "Đã lưu"
                                                )
                                                session.persistSharedState(
                                                    context = serviceContext,
                                                    drafts = next,
                                                    pendingTagAssignments = pendingTagAssignments,
                                                    perms = freshData.perms,
                                                    newHistoryEntries = listOf(buildHistoryEntryFromFloating(okMsg))
                                                )
                                                okMsg
                                            }
                                            is ConfirmResult.Aborted -> FloatingChatMsg(
                                                isUser = false,
                                                text = "Lưu chưa thành công: ${res.message}\nĐề xuất \"${draft.title}\" vẫn được giữ nguyên (ngày/giờ/Tag) để bạn kiểm tra hoặc bấm ✓ Xác nhận lại.",
                                                badge = "Thử lại lưu",
                                                isClarification = true
                                            )
                                            is ConfirmResult.Invalid -> FloatingChatMsg(
                                                isUser = false,
                                                text = "Chưa thể lưu: ${res.message}\nĐề xuất \"${draft.title}\" vẫn được giữ nguyên — kiểm tra lại rồi bấm ✓ Xác nhận.",
                                                badge = "Kiểm tra lại",
                                                isClarification = true
                                            )
                                        }
                                        messages = (messages + noticeMsg).takeLast(14)
                                        WallyActionController.notifyAllWidgets(serviceContext)
                                        onReload()
                                    }
                                }
                            }
                            if (isTagOpen) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(WalletCardBg)
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (activeTags.isEmpty()) {
                                        Text(
                                            text = "Chưa có Tag cá nhân.",
                                            fontSize = 10.5.sp,
                                            color = WalletMuted
                                        )
                                    } else {
                                        if (activeTags.size > 4 || tagSearchQuery.isNotBlank()) {
                                            OutlinedTextField(
                                                value = tagSearchQuery,
                                                onValueChange = { tagSearchQuery = it },
                                                placeholder = { Text("Tìm Tag...", fontSize = 10.sp) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        val shownTags = TagRules.search(activeTags, tagSearchQuery, limit = 12)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (draft.tagId != null) {
                                                FloatingPillButton(label = "Bỏ Tag", danger = true) {
                                                    snapshotData?.let { data ->
                                                        val next = session.updateDraftTag(
                                                            pendingProposals, draft.id, null, data.tags, data.perms
                                                        )
                                                        pendingProposals = next
                                                        session.persistSharedState(serviceContext, next, pendingTagAssignments, data.perms)
                                                    }
                                                }
                                            }
                                            shownTags.forEach { t ->
                                                FloatingPillButton(
                                                    label = t.name,
                                                    accent = draft.tagId == t.id
                                                ) {
                                                    snapshotData?.let { data ->
                                                        val nextTag = if (draft.tagId == t.id) null else t.id
                                                        val next = session.updateDraftTag(
                                                            pendingProposals, draft.id, nextTag, data.tags, data.perms
                                                        )
                                                        pendingProposals = next
                                                        session.persistSharedState(serviceContext, next, pendingTagAssignments, data.perms)
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
            }

            val chatKeyboardController = LocalSoftwareKeyboardController.current
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WalletInk,
                        unfocusedTextColor = WalletInk,
                        focusedContainerColor = WalletSoftNeutral,
                        unfocusedContainerColor = WalletSoftNeutral,
                        focusedBorderColor = WalletMint,
                        unfocusedBorderColor = WalletStroke
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) chatKeyboardController?.show() }
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
    modifier: Modifier = Modifier,
    centerContent: Boolean = false,
    contentDescriptionLabel: String = label,
    onClick: () -> Unit
) {
    val isNarrow = LocalConfiguration.current.screenWidthDp < 360
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = when {
            accent -> WalletMintSoft
            danger -> WalletWarningBg
            else -> WalletSoftNeutral
        },
        border = if (accent) BorderStroke(1.dp, WalletMintAccent.copy(alpha = 0.5f)) else null,
        modifier = modifier
            .semantics { contentDescription = contentDescriptionLabel }
            .walletClickable(contentDescriptionLabel, onClick = onClick)
    ) {
        Box(
            contentAlignment = if (centerContent) Alignment.Center else Alignment.CenterStart,
            modifier = Modifier.padding(horizontal = if (isNarrow) 7.dp else 9.dp, vertical = 5.dp)
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
                maxLines = 1
            )
        }
    }
}

private fun formatHms(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    val sec = s % 60L
    return if (h > 0L) "%02d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
