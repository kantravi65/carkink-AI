package com.example.voice

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.MainActivity

class VoiceKeyService : AccessibilityService() {
    
    companion object {
        private const val TAG = "VoiceKeyService"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val nodeInfo = event.source ?: return
        
        // Target YouTube app
        if (nodeInfo.packageName == "com.google.android.youtube") {
            clickNodeByText(nodeInfo, "Skip ad")
            clickNodeByText(nodeInfo, "Skip ads")
            clickNodeByText(nodeInfo, "Skip Ad")
        }
    }

    private fun clickNodeByText(node: AccessibilityNodeInfo, text: String) {
        val list = node.findAccessibilityNodeInfosByText(text)
        for (n in list) {
            if (n.isClickable) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked $text button")
            } else {
                var parent = n.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked parent of $text button")
                        break
                    }
                    parent = parent.parent
                }
            }
        }
    }

    override fun onInterrupt() {
        // Not used
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "Intercepted KeyEvent: keyCode=${event.keyCode}, action=${event.action}")
        
        // Only trigger on ACTION_UP to prevent spamming
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOICE_ASSIST,
                KeyEvent.KEYCODE_HEADSETHOOK, // Sometimes mapped to voice on car wheels
                KeyEvent.KEYCODE_SEARCH,      // Used by some Android boxes for voice
                84,                           // Hardcoded KEYCODE_SEARCH (just in case)
                KeyEvent.KEYCODE_MEDIA_RECORD // Another common car voice mapping
                -> {
                    Log.d(TAG, "Voice or Assistant key detected! Launching app...")
                    val intent = Intent(this, MainActivity::class.java).apply {
                        action = Intent.ACTION_VOICE_COMMAND
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    return true // Consume the event so the OS doesn't handle it
                }
            }
        }
        
        // If it's a down action for the same keys, consume it so it doesn't trigger default OS behavior on release
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOICE_ASSIST,
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_SEARCH,
                84,
                KeyEvent.KEYCODE_MEDIA_RECORD -> return true
            }
        }

        return super.onKeyEvent(event)
    }
}
