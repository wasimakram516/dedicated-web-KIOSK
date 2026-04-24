package com.example.sinankiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager

class KioskDeviceAdminReceiver : DeviceAdminReceiver()

class KioskDevicePolicyController(context: Context) {
    private val appContext = context.applicationContext
    private val devicePolicyManager =
        appContext.getSystemService(DevicePolicyManager::class.java)
    private val adminComponent = ComponentName(appContext, KioskDeviceAdminReceiver::class.java)
    private val homeActivityComponent = ComponentName(appContext, MainActivity::class.java)

    fun isDeviceOwner(): Boolean = devicePolicyManager.isDeviceOwnerApp(appContext.packageName)

    fun isLockTaskPermitted(): Boolean =
        devicePolicyManager.isLockTaskPermitted(appContext.packageName)

    fun isHomeAppPinned(): Boolean {
        val resolvedActivity = appContext.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            0
        ) ?: return false

        return resolvedActivity.activityInfo?.packageName == appContext.packageName
    }

    fun applyDedicatedDevicePolicies() {
        if (!isDeviceOwner()) {
            return
        }

        runCatching {
            devicePolicyManager.setLockTaskPackages(
                adminComponent,
                arrayOf(appContext.packageName)
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                devicePolicyManager.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                )
            }
        }

        runCatching {
            devicePolicyManager.setStatusBarDisabled(adminComponent, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                devicePolicyManager.setKeyguardDisabled(adminComponent, true)
            }
        }

        dedicatedUserRestrictions.forEach { restriction ->
            runCatching {
                devicePolicyManager.addUserRestriction(adminComponent, restriction)
            }
        }

        runCatching {
            devicePolicyManager.clearPackagePersistentPreferredActivities(
                adminComponent,
                appContext.packageName
            )
            devicePolicyManager.addPersistentPreferredActivity(
                adminComponent,
                homeIntentFilter(),
                homeActivityComponent
            )
        }

        // On OEM phones (Samsung, Xiaomi, etc.) the persistent preferred activity
        // can be overridden or ignored after a reboot, causing the launcher chooser
        // to appear. Hiding every competing home app leaves Android with no choice
        // but to use Sinan KIOSK as the home — the chooser never appears.
        hideCompetingLaunchers()
    }

    fun clearDedicatedDeviceRestrictions() {
        if (!isDeviceOwner()) {
            return
        }

        runCatching {
            devicePolicyManager.setStatusBarDisabled(adminComponent, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                devicePolicyManager.setKeyguardDisabled(adminComponent, false)
            }
        }

        dedicatedUserRestrictions.forEach { restriction ->
            runCatching {
                devicePolicyManager.clearUserRestriction(adminComponent, restriction)
            }
        }

        runCatching {
            devicePolicyManager.clearPackagePersistentPreferredActivities(
                adminComponent,
                appContext.packageName
            )
        }

        // Restore competing launchers so the phone can return to normal after
        // the admin exits the kiosk.
        unhideCompetingLaunchers()
    }

    fun lockScreen() {
        runCatching { devicePolicyManager.lockNow() }
    }

    fun startLockTaskIfPermitted(activity: Activity) {
        if (isLockTaskActive(activity)) {
            return
        }

        runCatching {
            activity.startLockTask()
        }
    }

    fun stopLockTaskIfActive(activity: Activity) {
        if (!isLockTaskActive(activity)) {
            return
        }

        runCatching {
            activity.stopLockTask()
        }
    }

    fun isLockTaskActive(activity: Activity): Boolean {
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun hideCompetingLaunchers() {
        appContext.packageManager
            .queryIntentActivities(homeIntent(), 0)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != appContext.packageName }
            .forEach { pkg ->
                runCatching {
                    devicePolicyManager.setApplicationHidden(adminComponent, pkg, true)
                }
            }
    }

    private fun unhideCompetingLaunchers() {
        // MATCH_UNINSTALLED_PACKAGES is required to find apps that were hidden
        // via setApplicationHidden — they no longer appear in normal queries.
        appContext.packageManager
            .queryIntentActivities(homeIntent(), PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != appContext.packageName }
            .forEach { pkg ->
                runCatching {
                    devicePolicyManager.setApplicationHidden(adminComponent, pkg, false)
                }
            }
    }

    private fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    private fun homeIntentFilter(): IntentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    private companion object {
        val dedicatedUserRestrictions = listOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_CREATE_WINDOWS
        )
    }
}
