package com.example.sinankiosk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val ScreenBackground = Color(0xFFF1F5FA)
internal val ScreenGradientTop = Color(0xFFE5EDF6)
internal val PanelSurface = Color(0xFFF7FAFD)
internal val PanelSurfaceRaised = Color(0xFFFFFFFF)
internal val PanelBorder = Color(0xFFD9E3ED)
internal val PanelText = Color(0xFF13212E)
internal val PanelTextMuted = Color(0xFF607284)
internal val AccentColor = Color(0xFF0D94A6)
internal val AccentSoft = Color(0x1A0D94A6)
internal val SuccessColor = Color(0xFF228B62)
internal val SuccessSoft = Color(0x16228B62)
internal val WarningColor = Color(0xFFCC8A1D)
internal val WarningSoft = Color(0x16CC8A1D)
internal val DangerColor = Color(0xFFC4475D)
internal val DangerSoft = Color(0x16C4475D)

@Composable
internal fun KioskDialog(
    maxWidth: Dp,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        KioskPanelCard(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .widthIn(max = maxWidth),
            content = content
        )
    }
}

@Composable
internal fun KioskPanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = PanelSurface,
        border = BorderStroke(1.dp, PanelBorder)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), PanelSurface)
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun PanelHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconBadge(icon = icon, accent = AccentColor, tint = AccentSoft)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = PanelText,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = PanelTextMuted
            )
        }
    }
}

@Composable
internal fun DeviceStatusBanner(uiState: MainUiState) {
    val isDedicatedKioskReady =
        uiState.isDeviceOwner && uiState.isLockTaskPermitted && uiState.isHomeAppPinned

    StatusBanner(
        icon = if (isDedicatedKioskReady) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
        text = if (isDedicatedKioskReady) {
            "Dedicated-device kiosk mode is fully enforced on this phone."
        } else if (uiState.isDeviceOwner) {
            "Device owner is active. The app is still finalizing kiosk launcher or lock-task enforcement."
        } else {
            "Dedicated-device provisioning is not active yet. On an unmanaged phone, Home and Recents can still escape the app."
        },
        accent = if (isDedicatedKioskReady) SuccessColor else WarningColor,
        tint = if (isDedicatedKioskReady) SuccessSoft else WarningSoft
    )
}

@Composable
internal fun ShortcutBanner() {
    StatusBanner(
        icon = Icons.Rounded.Info,
        text = "Admin controls are intentionally hidden from kiosk users.",
        accent = AccentColor,
        tint = AccentSoft
    )
}

@Composable
internal fun StatusBanner(
    icon: ImageVector,
    text: String,
    accent: Color,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tint,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent)
            Text(text = text, color = PanelText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun SectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = PanelSurfaceRaised),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                IconBadge(icon = icon, accent = AccentColor, tint = AccentSoft)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = PanelText,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PanelTextMuted
                    )
                }
            }
            content()
        }
    }
}

@Composable
internal fun IconBadge(
    icon: ImageVector,
    accent: Color,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(color = tint, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent)
    }
}

@Composable
internal fun KioskTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(text = supporting, color = PanelTextMuted) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PanelText,
            unfocusedTextColor = PanelText,
            disabledTextColor = PanelTextMuted,
            focusedContainerColor = PanelSurfaceRaised,
            unfocusedContainerColor = PanelSurfaceRaised,
            disabledContainerColor = PanelSurfaceRaised,
            cursorColor = AccentColor,
            focusedBorderColor = AccentColor,
            unfocusedBorderColor = PanelBorder,
            disabledBorderColor = PanelBorder,
            focusedLabelColor = AccentColor,
            unfocusedLabelColor = PanelTextMuted,
            disabledLabelColor = PanelTextMuted,
            focusedLeadingIconColor = AccentColor,
            unfocusedLeadingIconColor = PanelTextMuted,
            disabledLeadingIconColor = PanelTextMuted,
            focusedSupportingTextColor = PanelTextMuted,
            unfocusedSupportingTextColor = PanelTextMuted,
            disabledSupportingTextColor = PanelTextMuted
        )
    )
}
