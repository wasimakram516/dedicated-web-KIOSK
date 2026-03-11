package com.example.sinankiosk

import android.view.KeyEvent

class VolumeUnlockSequenceDetector(
    private val onUnlockDetected: () -> Unit
) {
    private var sequenceIndex = 0

    fun onKeyPressed(keyCode: Int) {
        if (keyCode != EXPECTED_SEQUENCE[sequenceIndex]) {
            sequenceIndex = if (keyCode == EXPECTED_SEQUENCE.first()) 1 else 0
            return
        }

        sequenceIndex += 1
        if (sequenceIndex == EXPECTED_SEQUENCE.size) {
            sequenceIndex = 0
            onUnlockDetected()
        }
    }

    companion object {
        private val EXPECTED_SEQUENCE = listOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP
        )
    }
}
