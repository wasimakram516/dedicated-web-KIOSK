package com.example.sinankiosk

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
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

    private var webView: WebView? = null
    private var reloadToken by mutableIntStateOf(0)
    private var uiState by mutableStateOf(MainUiState())
    private var isAdminExitInProgress = false

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

        loadSavedConfiguration()
        syncDevicePolicyState()

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
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            unlockSequenceDetector.onKeyPressed(event.keyCode)
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
        uiState = uiState.copy(
            isDeviceOwner = devicePolicyController.isDeviceOwner(),
            isLockTaskPermitted = devicePolicyController.isLockTaskPermitted(),
            isHomeAppPinned = devicePolicyController.isHomeAppPinned()
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
    }
}
