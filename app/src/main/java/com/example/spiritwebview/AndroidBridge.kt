package com.example.spiritwebview

import android.util.Log
import android.webkit.JavascriptInterface
import java.lang.ref.WeakReference

@Suppress("unused")
class AndroidBridge(activity: BrowserActivity) {
    private val activityRef = WeakReference(activity)

    companion object {
        private const val TAG = "AndroidBridge"
    }

    @JavascriptInterface
    fun navigate(payloadJson: String?) {
        Log.d(TAG, "navigate: $payloadJson")
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            try {
                activity.navigate(payloadJson ?: "{}")
            } catch (e: Exception) {
                Log.e(TAG, "navigate handler failed", e)
            }
        }
    }

    @JavascriptInterface
    fun injectLayers(payloadJson: String?) {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            try {
                activity.injectLayers(payloadJson ?: "{}")
            } catch (e: Exception) {
                Log.e(TAG, "injectLayers handler failed", e)
            }
        }
    }

    @JavascriptInterface
    fun goBack(payloadJson: String?) {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            try {
                activity.goBack(payloadJson ?: "{}")
            } catch (e: Exception) {
                Log.e(TAG, "goBack handler failed", e)
            }
        }
    }

    @JavascriptInterface
    fun reload(payloadJson: String?) {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            try {
                activity.reload(payloadJson ?: "{}")
            } catch (e: Exception) {
                Log.e(TAG, "reload handler failed", e)
            }
        }
    }

    @JavascriptInterface
    fun toggleSplit(payloadJson: String?) {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            try {
                activity.toggleSplit(payloadJson ?: "{}")
            } catch (e: Exception) {
                Log.e(TAG, "toggleSplit handler failed", e)
            }
        }
    }

    @JavascriptInterface
    fun setFallback(payloadJson: String?) {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            try {
                activity.setFallback(payloadJson ?: "{}")
            } catch (e: Exception) {
                Log.e(TAG, "setFallback handler failed", e)
            }
        }
    }
}
