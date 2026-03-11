package com.example.sinankiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
