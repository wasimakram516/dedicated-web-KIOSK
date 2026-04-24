package com.example.sinankiosk

import android.content.Context
import android.util.Log
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

data class KioskConfiguration(
    val domain: String,
    val isPinConfigured: Boolean
) {
    val isConfigured: Boolean
        get() = domain.isNotBlank() && isPinConfigured
}

data class BootDiagnostics(
    val lastBroadcastAction: String? = null,
    val lastBroadcastAtMillis: Long? = null,
    val lastLaunchAttemptAtMillis: Long? = null,
    val lastLaunchAttemptSucceeded: Boolean? = null,
    val lastLaunchFailureReason: String? = null
)

class KioskSettings(context: Context) {
    private val appContext = context.applicationContext
    private val storageContext = appContext.createDeviceProtectedStorageContext()
    private val preferences by lazy {
        storageContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    }

    init {
        migrateLegacyPreferencesIfRequired()
    }

    fun loadBrightness(): Int = preferences.getInt(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS)

    fun saveBrightness(percent: Int) {
        preferences.edit().putInt(KEY_BRIGHTNESS, percent.coerceIn(5, 100)).apply()
    }

    fun loadConfiguration(): KioskConfiguration = KioskConfiguration(
        domain = preferences.getString(KEY_DOMAIN, "").orEmpty(),
        isPinConfigured = preferences.contains(KEY_PIN_HASH) && preferences.contains(KEY_PIN_SALT)
    )

    fun ensureDefaultPin() {
        if (preferences.contains(KEY_PIN_HASH) && preferences.contains(KEY_PIN_SALT)) {
            return
        }

        updatePin(DEFAULT_PIN)
    }

    fun saveConfiguration(domain: String?, pin: String) {
        val salt = generateSalt()
        val editor = preferences.edit()
        if (domain.isNullOrBlank()) {
            editor.remove(KEY_DOMAIN)
        } else {
            editor.putString(KEY_DOMAIN, requireNotNull(normalizeDomain(domain)))
        }
        editor
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hashPin(pin, salt))
            .apply()
    }

    fun saveDomain(domain: String?) {
        val editor = preferences.edit()
        if (domain.isNullOrBlank()) {
            editor.remove(KEY_DOMAIN)
        } else {
            editor.putString(KEY_DOMAIN, requireNotNull(normalizeDomain(domain)))
        }
        editor.apply()
    }

    fun updatePin(pin: String) {
        val salt = generateSalt()
        preferences.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hashPin(pin, salt))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = preferences.getString(KEY_PIN_SALT, null)
        val storedHash = preferences.getString(KEY_PIN_HASH, null)
        if (salt.isNullOrBlank() || storedHash.isNullOrBlank()) {
            return false
        }

        return hashPin(pin, salt) == storedHash
    }

    fun loadBootDiagnostics(): BootDiagnostics = BootDiagnostics(
        lastBroadcastAction = preferences.getString(KEY_LAST_BOOT_ACTION, null),
        lastBroadcastAtMillis = preferences.takeIf { it.contains(KEY_LAST_BOOT_AT) }
            ?.getLong(KEY_LAST_BOOT_AT, 0L)
            ?.takeIf { it > 0L },
        lastLaunchAttemptAtMillis = preferences.takeIf { it.contains(KEY_LAST_BOOT_LAUNCH_AT) }
            ?.getLong(KEY_LAST_BOOT_LAUNCH_AT, 0L)
            ?.takeIf { it > 0L },
        lastLaunchAttemptSucceeded = when {
            !preferences.contains(KEY_LAST_BOOT_LAUNCH_SUCCESS) -> null
            else -> preferences.getBoolean(KEY_LAST_BOOT_LAUNCH_SUCCESS, false)
        },
        lastLaunchFailureReason = preferences.getString(KEY_LAST_BOOT_LAUNCH_FAILURE, null)
    )

    fun recordBootBroadcast(action: String?) {
        preferences.edit()
            .putString(KEY_LAST_BOOT_ACTION, action)
            .putLong(KEY_LAST_BOOT_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordBootLaunchAttempt(
        succeeded: Boolean,
        failureReason: String? = null
    ) {
        preferences.edit()
            .putLong(KEY_LAST_BOOT_LAUNCH_AT, System.currentTimeMillis())
            .putBoolean(KEY_LAST_BOOT_LAUNCH_SUCCESS, succeeded)
            .putString(KEY_LAST_BOOT_LAUNCH_FAILURE, failureReason)
            .apply()
    }

    private fun migrateLegacyPreferencesIfRequired() {
        val deviceProtectedPreferences = storageContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        if (deviceProtectedPreferences.all.isNotEmpty()) {
            return
        }

        runCatching {
            storageContext.moveSharedPreferencesFrom(appContext, PREFERENCES_NAME)
        }.onFailure { throwable ->
            Log.w(
                TAG,
                "Failed to migrate kiosk settings to device-protected storage",
                throwable
            )
        }
    }

    companion object {
        private const val TAG = "KioskSettings"
        private const val PREFERENCES_NAME = "kiosk_settings"
        private const val KEY_DOMAIN = "domain"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_LAST_BOOT_ACTION = "last_boot_action"
        private const val KEY_LAST_BOOT_AT = "last_boot_at"
        private const val KEY_LAST_BOOT_LAUNCH_AT = "last_boot_launch_at"
        private const val KEY_LAST_BOOT_LAUNCH_SUCCESS = "last_boot_launch_success"
        private const val KEY_LAST_BOOT_LAUNCH_FAILURE = "last_boot_launch_failure"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val DEFAULT_BRIGHTNESS = 80
        private const val MINIMUM_PIN_LENGTH = 4
        internal const val DEFAULT_PIN = "1234"

        internal fun normalizeDomain(rawValue: String): String? {
            val trimmedValue = rawValue.trim()
            if (trimmedValue.isBlank()) {
                return null
            }

            if (trimmedValue.contains("://") &&
                !trimmedValue.startsWith("http://", ignoreCase = true) &&
                !trimmedValue.startsWith("https://", ignoreCase = true)
            ) {
                return null
            }

            val valueWithScheme = if (
                trimmedValue.startsWith("http://", ignoreCase = true) ||
                trimmedValue.startsWith("https://", ignoreCase = true)
            ) {
                trimmedValue
            } else {
                "https://$trimmedValue"
            }

            val parsedUri = try {
                URI(valueWithScheme)
            } catch (_: Exception) {
                return null
            }

            val scheme = parsedUri.scheme?.lowercase(Locale.US) ?: return null
            val host = parsedUri.host ?: return null
            if (scheme !in setOf("http", "https") || host.isBlank()) {
                return null
            }

            return parsedUri.toString()
        }

        internal fun isValidPin(pin: String): Boolean =
            pin.length >= MINIMUM_PIN_LENGTH && pin.all(Char::isDigit)

        internal fun hashPin(pin: String, salt: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
            return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }

        private fun generateSalt(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
