package com.example.sinankiosk

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.sinankiosk.ui.theme.SinanKIOSKTheme

class MainActivity : ComponentActivity() {
    private val kioskSettings by lazy { KioskSettings(this) }
    private val devicePolicyController by lazy { KioskDevicePolicyController(this) }
    private val unlockSequenceDetector = VolumeUnlockSequenceDetector { showPinPrompt() }
    private val foregroundHandler = Handler(Looper.getMainLooper())
    private val lockTaskEnforcer = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed || isAdminExitInProgress) {
                return
            }

            ensurePinnedKioskMode()
            foregroundHandler.postDelayed(this, LOCK_TASK_ENFORCEMENT_INTERVAL_MS)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled by WebChromeClient.onPermissionRequest */ }

    private var webView: WebView? = null
    private var reloadToken by mutableIntStateOf(0)
    private var uiState by mutableStateOf(MainUiState())
    private var isAdminExitInProgress = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Screen turned off — drop keep-screen-on so power button
                    // works as a proper toggle rather than being ignored.
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Screen woke up — restore keep-screen-on so the display
                    // never times out while the kiosk is in use.
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            }
        )

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        )

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        setMaxVolume()
        loadSavedConfiguration()
        syncDevicePolicyState()
        requestHomeRoleIfNeeded()
        loadSavedBrightness()

        setContent {
            SinanKIOSKTheme {
                KioskScreen(
                    uiState = uiState,
                    reloadToken = reloadToken,
                    onWebViewCreated = { view -> webView = view },
                    onInitialSetupSubmitted = ::handleInitialSetup,
                    onPinSubmitted = ::verifyAdminPin,
                    onDismissPinPrompt = { uiState = uiState.copy(showPinDialog = false) },
                    onAdminSaveSubmitted = ::handleAdminSave,
                    onAdminDismissed = ::dismissAdminUi,
                    onExitRequested = ::exitKioskApp,
                    onReloadRequested = ::reloadWebView,
                    onRequestIgnoreBatteryOptimizations = ::requestIgnoreBatteryOptimizations,
                    onOpenBatteryOptimizationSettings = ::openBatteryOptimizationSettings,
                    onOpenHomeSettings = ::openHomeSettings,
                    onOpenAppInfo = ::openAppInfo,
                    onBrightnessChanged = ::applyBrightness,
                    onLockScreenRequested = ::lockScreen,
                    onPageStarted = {
                        uiState = uiState.copy(
                            isPageLoading = true,
                            pageError = null
                        )
                    },
                    onPageFinished = {
                        uiState = uiState.copy(isPageLoading = false)
                    },
                    onPageError = { message ->
                        uiState = uiState.copy(
                            isPageLoading = false,
                            pageError = message
                        )
                    }
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                // Quick tap (repeatCount == 0) feeds the admin unlock sequence.
                // Held down (repeatCount > 0) increases brightness instead.
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        unlockSequenceDetector.onKeyPressed(event.keyCode)
                    } else {
                        applyBrightness((uiState.brightness + 2).coerceAtMost(100))
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        unlockSequenceDetector.onKeyPressed(event.keyCode)
                    } else {
                        applyBrightness((uiState.brightness - 2).coerceAtLeast(5))
                    }
                }
                return true
            }
            else -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    unlockSequenceDetector.onKeyPressed(event.keyCode)
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        isAdminExitInProgress = false
        applyImmersiveMode()
        syncDevicePolicyState()
        startKioskEnforcement()
    }

    override fun onPause() {
        stopKioskEnforcement()
        super.onPause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldForceReturnToKiosk()) {
            relaunchToForeground()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && shouldForceReturnToKiosk()) {
            relaunchToForeground()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
            ensurePinnedKioskMode()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        foregroundHandler.removeCallbacksAndMessages(null)
        webView?.let { view ->
            view.stopLoading()
            view.destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun loadSavedConfiguration() {
        kioskSettings.ensureDefaultPin()
        val configuration = kioskSettings.loadConfiguration()
        uiState = uiState.copy(
            configuredUrl = configuration.domain,
            isSetupRequired = !configuration.isPinConfigured,
            isPinConfigured = configuration.isPinConfigured,
            showPinDialog = false,
            showAdminPanel = false,
            isPageLoading = false,
            pageError = null
        )
    }

    private fun syncDevicePolicyState() {
        devicePolicyController.applyDedicatedDevicePolicies()
        val powerManager = getSystemService(PowerManager::class.java)
        val isIgnoringBatteryOptimizations =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        uiState = uiState.copy(
            isDeviceOwner = devicePolicyController.isDeviceOwner(),
            isLockTaskPermitted = devicePolicyController.isLockTaskPermitted(),
            isHomeAppPinned = devicePolicyController.isHomeAppPinned(),
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
            bootDiagnostics = kioskSettings.loadBootDiagnostics(),
            canWriteSettings = Settings.System.canWrite(this)
        )
    }

    private fun showPinPrompt() {
        if (uiState.isSetupRequired || uiState.showAdminPanel) {
            return
        }

        uiState = uiState.copy(showPinDialog = true)
    }

    private fun dismissAdminUi() {
        uiState = uiState.copy(
            showPinDialog = false,
            showAdminPanel = false
        )
    }

    private fun handleInitialSetup(
        domain: String,
        pin: String,
        confirmPin: String
    ): FormSubmissionResult {
        val normalizedDomain = if (domain.isBlank()) {
            null
        } else {
            KioskSettings.normalizeDomain(domain)
                ?: return FormSubmissionResult.failure(getString(R.string.error_domain_required))
        }
        val pinValidationError = validatePin(pin, confirmPin, pinIsRequired = true)
        if (pinValidationError != null) {
            return FormSubmissionResult.failure(pinValidationError)
        }

        kioskSettings.saveConfiguration(normalizedDomain, pin)
        loadSavedConfiguration()
        reloadWebView()

        return FormSubmissionResult.success()
    }

    private fun handleAdminSave(
        domain: String,
        newPin: String,
        confirmPin: String
    ): FormSubmissionResult {
        val normalizedDomain = if (domain.isBlank()) {
            null
        } else {
            KioskSettings.normalizeDomain(domain)
                ?: return FormSubmissionResult.failure(getString(R.string.error_domain_required))
        }
        val pinValidationError = validatePin(newPin, confirmPin, pinIsRequired = false)
        if (pinValidationError != null) {
            return FormSubmissionResult.failure(pinValidationError)
        }

        kioskSettings.saveDomain(normalizedDomain)
        if (newPin.isNotBlank()) {
            kioskSettings.updatePin(newPin)
        }

        loadSavedConfiguration()
        reloadWebView()

        return FormSubmissionResult.success()
    }

    private fun validatePin(
        pin: String,
        confirmPin: String,
        pinIsRequired: Boolean
    ): String? {
        if (pin.isBlank()) {
            return if (pinIsRequired) getString(R.string.error_pin_required) else null
        }
        if (!KioskSettings.isValidPin(pin)) {
            return getString(R.string.error_pin_format)
        }
        if (pin != confirmPin) {
            return getString(R.string.error_pin_mismatch)
        }

        return null
    }

    private fun verifyAdminPin(pin: String): Boolean {
        val isValidPin = kioskSettings.verifyPin(pin)
        if (isValidPin) {
            uiState = uiState.copy(
                showPinDialog = false,
                showAdminPanel = true
            )
        }

        return isValidPin
    }

    private fun reloadWebView() {
        reloadToken += 1
        if (uiState.configuredUrl.isBlank()) {
            webView?.stopLoading()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || uiState.isIgnoringBatteryOptimizations) {
            openBatteryOptimizationSettings()
            return
        }

        launchSystemSettings(
            primaryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            },
            fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        launchSystemSettings(
            primaryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
    }

    private fun openHomeSettings() {
        launchSystemSettings(
            primaryIntent = Intent(Settings.ACTION_HOME_SETTINGS),
            fallbackIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        )
    }

    private fun openAppInfo() {
        launchSystemSettings(
            primaryIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    private fun loadSavedBrightness() {
        val canWrite = Settings.System.canWrite(this)
        val saved = kioskSettings.loadBrightness()
        uiState = uiState.copy(brightness = saved, canWriteSettings = canWrite)
        if (canWrite) {
            setBrightnessInternal(saved)
        }
    }

    private fun applyBrightness(percent: Int) {
        if (!Settings.System.canWrite(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
            )
            return
        }
        kioskSettings.saveBrightness(percent)
        uiState = uiState.copy(brightness = percent, canWriteSettings = true)
        setBrightnessInternal(percent)
    }

    private fun setBrightnessInternal(percent: Int) {
        val value = (percent / 100f * 255).toInt().coerceIn(5, 255)
        runCatching {
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
            window.attributes = window.attributes.also { it.screenBrightness = value / 255f }
        }
    }

    private fun setMaxVolume() {
        val audioManager = getSystemService(android.media.AudioManager::class.java) ?: return
        listOf(
            android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.STREAM_RING,
            android.media.AudioManager.STREAM_NOTIFICATION
        ).forEach { stream ->
            audioManager.setStreamVolume(
                stream,
                audioManager.getStreamMaxVolume(stream),
                0
            )
        }
    }

    private fun lockScreen() {
        devicePolicyController.lockScreen()
    }

    private fun exitKioskApp() {
        isAdminExitInProgress = true
        stopKioskEnforcement()
        devicePolicyController.clearDedicatedDeviceRestrictions()
        devicePolicyController.stopLockTaskIfActive(this)
        finishAffinity()
    }

    private fun shouldForceReturnToKiosk(): Boolean {
        if (isAdminExitInProgress || uiState.showAdminPanel || uiState.showPinDialog) {
            return false
        }

        return true
    }

    private fun relaunchToForeground() {
        foregroundHandler.removeCallbacksAndMessages(null)
        foregroundHandler.post {
            if (isFinishing || isDestroyed || isAdminExitInProgress) {
                return@post
            }

            val relaunchIntent = intent?.let { Intent(it) } ?: intentForSelf()
            relaunchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            startActivity(relaunchIntent)
        }
    }

    private fun intentForSelf() = Intent(this, MainActivity::class.java)

    private fun launchSystemSettings(
        primaryIntent: Intent,
        fallbackIntent: Intent? = null
    ) {
        val primaryLaunch = runCatching {
            startActivity(primaryIntent)
        }

        if (primaryLaunch.isSuccess || fallbackIntent == null) {
            return
        }

        val exception = primaryLaunch.exceptionOrNull()
        if (exception !is ActivityNotFoundException) {
            return
        }

        runCatching {
            startActivity(fallbackIntent)
        }
    }

    private fun startKioskEnforcement() {
        ensurePinnedKioskMode()
        foregroundHandler.removeCallbacks(lockTaskEnforcer)
        foregroundHandler.postDelayed(lockTaskEnforcer, LOCK_TASK_ENFORCEMENT_INTERVAL_MS)
    }

    private fun stopKioskEnforcement() {
        foregroundHandler.removeCallbacks(lockTaskEnforcer)
    }

    private fun ensurePinnedKioskMode() {
        devicePolicyController.startLockTaskIfPermitted(this)
    }

    // On non-device-owner phones we cannot hide competing launchers, so the OS
    // may show a launcher chooser after reboot. RoleManager (API 29+) lets us
    // request the home role with a single-tap confirmation dialog instead of
    // the multi-option chooser. Once the user confirms, Android remembers the
    // choice reliably across reboots — no repeated prompts.
    private fun requestHomeRoleIfNeeded() {
        if (uiState.isDeviceOwner || uiState.isHomeAppPinned) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val roleManager = getSystemService(RoleManager::class.java) ?: return
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return
        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return

        runCatching {
            @Suppress("DEPRECATION")
            startActivityForResult(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                REQUEST_HOME_ROLE
            )
        }
    }

    @Deprecated("Required for RoleManager result on API < 34")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_HOME_ROLE) {
            syncDevicePolicyState()
        }
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        private const val LOCK_TASK_ENFORCEMENT_INTERVAL_MS = 1_000L
        private const val REQUEST_HOME_ROLE = 1001
    }
}
