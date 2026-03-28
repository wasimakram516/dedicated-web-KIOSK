package com.example.sinankiosk

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

data class MainUiState(
    val configuredUrl: String = "",
    val isSetupRequired: Boolean = true,
    val isPinConfigured: Boolean = false,
    val showPinDialog: Boolean = false,
    val showAdminPanel: Boolean = false,
    val isPageLoading: Boolean = false,
    val pageError: String? = null,
    val isDeviceOwner: Boolean = false,
    val isLockTaskPermitted: Boolean = false,
    val isHomeAppPinned: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val bootDiagnostics: BootDiagnostics = BootDiagnostics()
)

data class FormSubmissionResult(
    val wasSuccessful: Boolean,
    val message: String? = null
) {
    companion object {
        fun success(message: String? = null) = FormSubmissionResult(
            wasSuccessful = true,
            message = message
        )

        fun failure(message: String) = FormSubmissionResult(
            wasSuccessful = false,
            message = message
        )
    }
}

private data class WebViewLoadState(
    val configuredUrl: String,
    val reloadToken: Int
)

@Composable
fun KioskScreen(
    uiState: MainUiState,
    reloadToken: Int,
    onWebViewCreated: (WebView) -> Unit,
    onInitialSetupSubmitted: (String, String, String) -> FormSubmissionResult,
    onPinSubmitted: (String) -> Boolean,
    onDismissPinPrompt: () -> Unit,
    onAdminSaveSubmitted: (String, String, String) -> FormSubmissionResult,
    onAdminDismissed: () -> Unit,
    onExitRequested: () -> Unit,
    onReloadRequested: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onPageError: (String) -> Unit
) {
    BackHandler(enabled = true) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ScreenGradientTop, ScreenBackground)
                )
            )
    ) {
        if (uiState.configuredUrl.isNotBlank()) {
            KioskWebView(
                url = uiState.configuredUrl,
                reloadToken = reloadToken,
                onWebViewCreated = onWebViewCreated,
                onPageStarted = onPageStarted,
                onPageFinished = onPageFinished,
                onPageError = onPageError
            )
        } else {
            PlaceholderWebPage(
                uiState = uiState,
                onWebViewCreated = onWebViewCreated
            )
        }

        if (uiState.pageError != null && !uiState.isSetupRequired) {
            PageErrorOverlay(
                message = uiState.pageError,
                onRetry = onReloadRequested
            )
        }

        if (uiState.isPageLoading && !uiState.isSetupRequired) {
            LoadingOverlay()
        }

        if (uiState.isSetupRequired) {
            InitialSetupOverlay(
                uiState = uiState,
                onSubmit = onInitialSetupSubmitted
            )
        }

        if (uiState.showPinDialog) {
            AdminPinDialog(
                onSubmit = onPinSubmitted,
                onDismiss = onDismissPinPrompt
            )
        }

        if (uiState.showAdminPanel) {
            AdminPanelDialog(
                uiState = uiState,
                onSubmit = onAdminSaveSubmitted,
                onDismiss = onAdminDismissed,
                onExitRequested = onExitRequested,
                onReloadRequested = onReloadRequested,
                onRequestIgnoreBatteryOptimizations = onRequestIgnoreBatteryOptimizations,
                onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                onOpenHomeSettings = onOpenHomeSettings,
                onOpenAppInfo = onOpenAppInfo
            )
        }
    }
}

@Composable
private fun PlaceholderWebPage(
    uiState: MainUiState,
    onWebViewCreated: (WebView) -> Unit
) {
    val html = buildKioskPlaceholderHtml(uiState)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                onWebViewCreated(this)
                setBackgroundColor(android.graphics.Color.parseColor("#F4F8FC"))
                WebView.setWebContentsDebuggingEnabled(
                    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                )
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    databaseEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    loadsImagesAutomatically = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    defaultTextEncodingName = "UTF-8"
                }
                isVerticalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                tag = html
                loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        },
        update = { view ->
            val previousHtml = view.tag as? String
            if (previousHtml != html) {
                view.tag = html
                view.loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }
    )
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = PanelSurface,
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp
                )
                Text(
                    text = "Loading website",
                    color = PanelText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PageErrorOverlay(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f)),
        contentAlignment = Alignment.Center
    ) {
        KioskPanelCard(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 440.dp)
        ) {
            PanelHeader(
                icon = Icons.Rounded.WarningAmber,
                title = "Website failed to load",
                subtitle = "The kiosk could not render the configured page."
            )
            StatusBanner(
                icon = Icons.Rounded.Info,
                text = message,
                accent = WarningColor,
                tint = WarningSoft
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun InitialSetupOverlay(
    uiState: MainUiState,
    onSubmit: (String, String, String) -> FormSubmissionResult
) {
    var domain by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val setupScroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB8E8EEF5)),
        contentAlignment = Alignment.Center
    ) {
        KioskPanelCard(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(setupScroll),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                PanelHeader(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = "Initial kiosk setup",
                    subtitle = "Create the admin PIN and optionally set the website entry URL."
                )
                DeviceStatusBanner(uiState = uiState)
                SectionCard(
                    icon = Icons.Rounded.Security,
                    title = "Admin access",
                    subtitle = "The admin entry method is intentionally hidden from kiosk users."
                ) {
                    KioskTextField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            errorMessage = null
                        },
                        label = "Admin PIN",
                        supporting = "At least 4 digits",
                        icon = Icons.Rounded.Password,
                        keyboardType = KeyboardType.NumberPassword,
                        isPassword = true
                    )
                    KioskTextField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it
                            errorMessage = null
                        },
                        label = "Confirm PIN",
                        supporting = "Repeat the same PIN",
                        icon = Icons.Rounded.Lock,
                        keyboardType = KeyboardType.NumberPassword,
                        isPassword = true
                    )
                }
                SectionCard(
                    icon = Icons.Rounded.Language,
                    title = "Website entry URL",
                    subtitle = "This is the first page that opens. After launch, the entire website can keep navigating inside the WebView."
                ) {
                    KioskTextField(
                        value = domain,
                        onValueChange = {
                            domain = it
                            errorMessage = null
                        },
                        label = "Website URL",
                        supporting = "Optional. Example: https://example.com",
                        icon = Icons.Rounded.Language,
                        keyboardType = KeyboardType.Uri
                    )
                }
                ShortcutBanner()
                if (errorMessage != null) {
                    StatusBanner(
                        icon = Icons.Rounded.WarningAmber,
                        text = errorMessage.orEmpty(),
                        accent = DangerColor,
                        tint = DangerSoft
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val result = onSubmit(domain, pin, confirmPin)
                        if (!result.wasSuccessful) {
                            errorMessage = result.message
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Save and start kiosk")
                }
            }
        }
    }
}

@Composable
private fun AdminPinDialog(
    onSubmit: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    KioskDialog(
        maxWidth = 420.dp,
        onDismiss = onDismiss
    ) {
        PanelHeader(
            icon = Icons.Rounded.Lock,
            title = "Admin access",
            subtitle = "Enter the admin PIN to open kiosk settings."
        )
        KioskTextField(
            value = pin,
            onValueChange = {
                pin = it
                errorMessage = null
            },
            label = "Admin PIN",
            supporting = "Use the hidden admin entry method, then enter the PIN.",
            icon = Icons.Rounded.Password,
            keyboardType = KeyboardType.NumberPassword,
            isPassword = true
        )
        if (errorMessage != null) {
            StatusBanner(
                icon = Icons.Rounded.WarningAmber,
                text = errorMessage.orEmpty(),
                accent = DangerColor,
                tint = DangerSoft
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onDismiss,
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentColor.copy(alpha = 0.34f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = PanelSurfaceRaised,
                    contentColor = AccentColor
                )
            ) {
                Text("Cancel")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (!onSubmit(pin)) {
                        errorMessage = "Incorrect PIN."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Confirm")
            }
        }
    }
}

@Composable
private fun AdminPanelDialog(
    uiState: MainUiState,
    onSubmit: (String, String, String) -> FormSubmissionResult,
    onDismiss: () -> Unit,
    onExitRequested: () -> Unit,
    onReloadRequested: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    var domain by remember { mutableStateOf(uiState.configuredUrl) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val panelScroll = rememberScrollState()

    KioskDialog(
        maxWidth = 560.dp,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 680.dp)
                .verticalScroll(panelScroll),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            PanelHeader(
                icon = Icons.Rounded.AdminPanelSettings,
                title = "Kiosk settings",
                subtitle = "Update kiosk configuration first, then manage launcher and startup behavior."
            )
            DeviceStatusBanner(uiState = uiState)
            SectionCard(
                icon = Icons.Rounded.Language,
                title = "Website",
                subtitle = "This URL is the entry page only. The website can continue across its internal routes inside the WebView."
            ) {
                KioskTextField(
                    value = domain,
                    onValueChange = {
                        domain = it
                        message = null
                    },
                    label = "Website URL",
                    supporting = "Optional. Leave blank to clear it.",
                    icon = Icons.Rounded.Language,
                    keyboardType = KeyboardType.Uri
                )
            }
            SectionCard(
                icon = Icons.Rounded.Security,
                title = "Security",
                subtitle = "Update the admin PIN used by the hidden unlock shortcut."
            ) {
                KioskTextField(
                    value = newPin,
                    onValueChange = {
                        newPin = it
                        message = null
                    },
                    label = "New PIN",
                    supporting = "Leave blank to keep the current PIN",
                    icon = Icons.Rounded.Password,
                    keyboardType = KeyboardType.NumberPassword,
                    isPassword = true
                )
                KioskTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = it
                        message = null
                    },
                    label = "Confirm new PIN",
                    supporting = "Repeat the new PIN",
                    icon = Icons.Rounded.Lock,
                    keyboardType = KeyboardType.NumberPassword,
                    isPassword = true
                )
            }
            ShortcutBanner()
            if (message != null) {
                StatusBanner(
                    icon = if (message == "Kiosk configuration saved.") {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.WarningAmber
                    },
                    text = message.orEmpty(),
                    accent = if (message == "Kiosk configuration saved.") SuccessColor else DangerColor,
                    tint = if (message == "Kiosk configuration saved.") SuccessSoft else DangerSoft
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val result = onSubmit(domain, newPin, confirmPin)
                    if (!result.wasSuccessful) {
                        message = result.message
                    } else {
                        message = "Kiosk configuration saved."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Save configuration")
            }
            SectionCard(
                icon = Icons.Rounded.Home,
                title = "Default Home app",
                subtitle = "Open Android's launcher chooser to switch between Sinan KIOSK and the phone's normal home screen."
            ) {
                StatusBanner(
                    icon = if (uiState.isHomeAppPinned) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.WarningAmber
                    },
                    text = if (uiState.isHomeAppPinned) {
                        "Sinan KIOSK is currently selected as the default Home app."
                    } else {
                        "Another launcher is currently selected. Choose Sinan KIOSK here if you want reboot to return into the kiosk."
                    },
                    accent = if (uiState.isHomeAppPinned) SuccessColor else WarningColor,
                    tint = if (uiState.isHomeAppPinned) SuccessSoft else WarningSoft
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenHomeSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Choose default Home app")
                }
            }
            SectionCard(
                icon = if (uiState.isIgnoringBatteryOptimizations) {
                    Icons.Rounded.CheckCircle
                } else {
                    Icons.Rounded.WarningAmber
                },
                title = "Background startup",
                subtitle = "Battery optimization and vendor battery controls can affect whether the kiosk relaunches after reboot."
            ) {
                StatusBanner(
                    icon = if (uiState.isIgnoringBatteryOptimizations) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.Info
                    },
                    text = if (uiState.isIgnoringBatteryOptimizations) {
                        "Battery optimization is already disabled for this kiosk app."
                    } else {
                        "Battery optimization is still enabled. Android may delay or suppress boot relaunch until the app is opened again."
                    },
                    accent = if (uiState.isIgnoringBatteryOptimizations) SuccessColor else WarningColor,
                    tint = if (uiState.isIgnoringBatteryOptimizations) SuccessSoft else WarningSoft
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!uiState.isIgnoringBatteryOptimizations) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onRequestIgnoreBatteryOptimizations,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Disable optimization")
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenBatteryOptimizationSettings,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentColor.copy(alpha = 0.34f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PanelSurfaceRaised,
                            contentColor = AccentColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (uiState.isIgnoringBatteryOptimizations) "Open settings" else "More options")
                    }
                }
            }
            SectionCard(
                icon = Icons.Rounded.PowerSettingsNew,
                title = "Admin actions",
                subtitle = "Refresh the kiosk or leave it temporarily when maintenance is needed."
            ) {
                if (uiState.isHomeAppPinned) {
                    StatusBanner(
                        icon = Icons.Rounded.WarningAmber,
                        text = "Sinan KIOSK is still the default Home app. To return to the phone launcher, switch the Home app first and then exit.",
                        accent = WarningColor,
                        tint = WarningSoft
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onReloadRequested,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentColor.copy(alpha = 0.34f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PanelSurfaceRaised,
                            contentColor = AccentColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Reload website")
                    }
                    if (uiState.isHomeAppPinned) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenHomeSettings,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentColor.copy(alpha = 0.34f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = PanelSurfaceRaised,
                                contentColor = AccentColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Switch Home app first")
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenAppInfo,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentColor.copy(alpha = 0.34f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PanelSurfaceRaised,
                            contentColor = AccentColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Open app info")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onExitRequested,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Exit app")
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentColor)
                    ) {
                        Text("Close settings")
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KioskWebView(
    url: String,
    reloadToken: Int,
    onWebViewCreated: (WebView) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onPageError: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                val webView = this
                onWebViewCreated(this)
                setBackgroundColor(android.graphics.Color.WHITE)
                
                // Force full size layout params
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                WebView.setWebContentsDebuggingEnabled(
                    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                )

                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(webView, true)
                }

                // Force LTR layout so Android system locale/RTL settings don't affect
                // the WebView's input method cursor direction.
                layoutDirection = View.LAYOUT_DIRECTION_LTR

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadsImagesAutomatically = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(true)
                    allowFileAccess = false
                    allowContentAccess = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    defaultTextEncodingName = "UTF-8"
                    textZoom = 100
                    
                    // Set a modern standard User Agent to avoid "mobile-lite" site versions
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                }

                tag = WebViewLoadState(
                    configuredUrl = url,
                    reloadToken = reloadToken
                )

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        val msg = consoleMessage?.message() ?: "null"
                        val line = consoleMessage?.lineNumber() ?: 0
                        val src = consoleMessage?.sourceId() ?: "unknown"
                        Log.d("KioskWebViewJS", "[$src:$line] $msg")
                        return true
                    }

                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        val parentWebView = view ?: return false
                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                        val popupWebView = WebView(parentWebView.context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetUrl = request?.url?.toString().orEmpty()
                                    if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                                        parentWebView.loadUrl(targetUrl)
                                    }
                                    view?.destroy()
                                    return true
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    val targetUrl = url.orEmpty()
                                    if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                                        parentWebView.loadUrl(targetUrl)
                                    }
                                    view?.destroy()
                                }
                            }
                        }
                        transport.webView = popupWebView
                        resultMsg.sendToTarget()
                        return true
                    }
                }
                
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onPageStarted()
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        Log.w("KioskWebView", "SSL error ${error?.primaryError} on ${error?.url}")
                        handler?.proceed()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onPageFinished()
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        if (request?.isForMainFrame != true) {
                            return false
                        }

                        return when (request.url?.scheme?.lowercase(Locale.US)) {
                            "http", "https" -> false
                            else -> true
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            onPageError(
                                error?.description?.toString()
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "The kiosk page could not be loaded."
                            )
                        }
                    }
                }
                loadUrl(url)
            }
        },
        update = { view ->
            val previousState = view.tag as? WebViewLoadState
            val targetState = WebViewLoadState(
                configuredUrl = url,
                reloadToken = reloadToken
            )

            when {
                previousState == null -> {
                    view.tag = targetState
                    view.loadUrl(url)
                }

                previousState.configuredUrl != url -> {
                    view.stopLoading()
                    view.clearHistory()
                    view.tag = targetState
                    view.loadUrl(url)
                }

                previousState.reloadToken != reloadToken -> {
                    view.tag = targetState
                    if (view.url.isNullOrBlank()) {
                        view.loadUrl(url)
                    } else {
                        view.reload()
                    }
                }
            }
        }
    )
}

private fun formatDiagnosticsTimestamp(timestamp: Long): String =
    java.text.SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(java.util.Date(timestamp))
