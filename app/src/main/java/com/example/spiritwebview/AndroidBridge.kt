package com.example.spiritwebview

import android.util.Log
import android.webkit.JavascriptInterface
import java.lang.ref.WeakReference

class AndroidBridge(activity: BrowserActivity) {
    private val activityRef = WeakReference(activity)
    private val TAG = "AndroidBridge"

    @JavascriptInterface
    fun navigate(payloadJson: String?) {
        Log.d(TAG, "navigate: $payloadJson")
        activityRef.get()?.runOnUiThread {
            activityRef.get()?.navigate(payloadJson ?: "{}")
        }
    }

    @JavascriptInterface
    fun injectLayers(payloadJson: String?) {
        activityRef.get()?.runOnUiThread {
            activityRef.get()?.injectLayers(payloadJson ?: "{}")
        }
    }

    @JavascriptInterface
    fun goBack(payloadJson: String?) {
        activityRef.get()?.runOnUiThread {
            activityRef.get()?.goBack(payloadJson ?: "{}")
        }
    }

    @JavascriptInterface
    fun reload(payloadJson: String?) {
        activityRef.get()?.runOnUiThread {
            activityRef.get()?.reload(payloadJson ?: "{}")
        }
    }

    @JavascriptInterface
    fun toggleSplit(payloadJson: String?) {
        activityRef.get()?.runOnUiThread {
            activityRef.get()?.toggleSplit(payloadJson ?: "{}")
        }
    }

    @JavascriptInterface
    fun setFallback(payloadJson: String?) {
        activityRef.get()?.runOnUiThread {
            activityRef.get()?.setFallback(payloadJson ?: "{}")
        }
    }
}
