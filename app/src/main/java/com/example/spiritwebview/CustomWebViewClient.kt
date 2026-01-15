package com.example.spiritwebview

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class CustomWebViewClient(private val activity: BrowserActivity) : WebViewClient() {
    private val TAG = "CustomWebViewClient"

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        try {
            val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
            // Only attempt to strip CSP for top-level HTML documents
            if (request.isForMainFrame && request.url?.path?.endsWith(".html") == true || request.url?.lastPathSegment == null) {
                val original = super.shouldInterceptRequest(view, request)
                // If original response is available and looks like HTML, strip CSP headers
                if (original != null && original.mimeType?.contains("text/html") == true) {
                    val data = original.data.readBytes()
                    var content = String(data)
                    // Remove meta CSP tags
                    content = content.replace(Regex("<meta[^>]*http-equiv=\\\"Content-Security-Policy\\\"[^>]*>", RegexOption.IGNORE_CASE), "")
                    // Return response without CSP headers
                    val headers = original.responseHeaders?.toMutableMap() ?: mutableMapOf()
                    headers.remove("Content-Security-Policy")
                    headers.remove("content-security-policy")
                    return WebResourceResponse(original.mimeType, original.encoding, ByteArrayInputStream(content.toByteArray())).apply {
                        responseHeaders = headers
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "shouldInterceptRequest error: $e")
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Optionally notify the React frontend via postMessage or evaluateJavascript to indicate load complete
        try {
            view?.evaluateJavascript("window.dispatchEvent(new MessageEvent('message',{data:{type:'pageLoaded', url:'" + (url ?: "") + "'}}));", null)
        } catch (e: Exception) {
            Log.w(TAG, "onPageFinished eval error: $e")
        }
    }
}

