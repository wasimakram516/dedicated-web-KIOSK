package com.example.sinankiosk

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeUnlockSequenceDetectorTest {
    @Test
    fun matchingSequence_triggersUnlockOnce() {
        var triggerCount = 0
        val detector = VolumeUnlockSequenceDetector { triggerCount += 1 }

        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_UP)
        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_DOWN)
        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_UP)
        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_DOWN)
        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_UP)

        assertEquals(1, triggerCount)
    }

    @Test
    fun wrongSequence_resetsDetector() {
        var triggerCount = 0
        val detector = VolumeUnlockSequenceDetector { triggerCount += 1 }

        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_UP)
        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_UP)
        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_DOWN)
        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_UP)
        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_DOWN)
        detector.onKeyPressed(KeyEvent.KEYCODE_VOLUME_UP)

        assertEquals(1, triggerCount)
    }
}
