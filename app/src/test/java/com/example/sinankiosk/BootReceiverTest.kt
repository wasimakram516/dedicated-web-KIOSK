package com.example.sinankiosk

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test
    fun shouldHandleAction_acceptsSupportedSystemStartupActions() {
        assertTrue(BootReceiver.shouldHandleAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(BootReceiver.shouldHandleAction(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertTrue(BootReceiver.shouldHandleAction(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun shouldHandleAction_rejectsUnknownActions() {
        assertFalse(BootReceiver.shouldHandleAction(Intent.ACTION_PACKAGE_ADDED))
        assertFalse(BootReceiver.shouldHandleAction(null))
    }
}
