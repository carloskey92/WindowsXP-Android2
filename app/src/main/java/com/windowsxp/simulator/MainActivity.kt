package com.windowsxp.simulator

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.net.Uri

class MainActivity : Activity() {
    private lateinit var desktopView: WebView
    private val stateKey = "xp_webview_state"
    private val browserDialogs = mutableMapOf<String, Dialog>()
    private val browserViews = mutableMapOf<String, WebView>()
    private val browserAddresses = mutableMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        desktopView = createDesktopWebView()
        setContentView(desktopView)
        val restored = savedInstanceState?.getBundle(stateKey)
        val didRestore = restored != null && desktopView.restoreState(restored) != null
        if (!didRestore) desktopView.loadUrl("file:///android_asset/index.html")
        startKeepAliveService()
        requestNotificationPermission()
    }

    private fun createDesktopWebView(): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.rgb(8, 97, 174))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                userAgentString = userAgentString + " WindowsXPSimulatorAndroid"
            }
            addJavascriptInterface(AndroidBrowserBridge(), "AndroidBrowser")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    // Android puede cerrar el proceso que dibuja el WebView para recuperar memoria.
                    // Se crea uno nuevo y el almacenamiento local del simulador se conserva.
                    if (view.parent is android.view.ViewGroup) {
                        (view.parent as android.view.ViewGroup).removeView(view)
                    }
                    view.destroy()
                    desktopView = createDesktopWebView()
                    setContentView(desktopView)
                    desktopView.loadUrl("file:///android_asset/index.html")
                    return true
                }
            }
        }
    }

    private inner class AndroidBrowserBridge {
        @JavascriptInterface fun open(id: String, url: String, title: String) {
            runOnUiThread { showBrowser(id, url, title) }
        }
        @JavascriptInterface fun close(id: String) {
            runOnUiThread { closeBrowser(id) }
        }
        @JavascriptInterface fun back(id: String) {
            runOnUiThread { browserViews[id]?.let { if (it.canGoBack()) it.goBack() } }
        }
        @JavascriptInterface fun forward(id: String) {
            runOnUiThread { browserViews[id]?.let { if (it.canGoForward()) it.goForward() } }
        }
        @JavascriptInterface fun reload(id: String) {
            runOnUiThread { browserViews[id]?.reload() }
        }
    }

    private fun showBrowser(id: String, url: String, title: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        val existing = browserViews[id]
        if (existing != null) {
            browserAddresses[id]?.setText(url)
            existing.loadUrl(url)
            browserDialogs[id]?.show()
            return
        }

        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(238, 236, 226))
        }
        val heading = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 10, 14, 10)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(55, 145, 240), Color.rgb(13, 77, 183)))
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(5, 5, 5, 5)
        }
        fun control(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setPadding(4, 0, 4, 0)
            setOnClickListener { action() }
        }
        val address = EditText(this).apply {
            setSingleLine(true)
            textSize = 13f
            setText(url)
            hint = "https://sitio.com"
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
        }
        browserAddresses[id] = address
        val web = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = false
                allowContentAccess = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme ?: return true
                    return scheme != "http" && scheme != "https"
                }
                override fun onPageStarted(view: WebView, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                    if (!pageUrl.isNullOrBlank()) address.setText(pageUrl)
                }
                override fun onPageFinished(view: WebView, pageUrl: String?) {
                    if (!pageUrl.isNullOrBlank()) address.setText(pageUrl)
                }
            }
        }
        browserViews[id] = web
        address.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                var target = address.text.toString().trim()
                if (target.isNotEmpty()) {
                    if (!target.contains("://")) target = "https://$target"
                    if (target.startsWith("https://") || target.startsWith("http://")) web.loadUrl(target)
                }
                true
            } else false
        }
        controls.addView(control("‹") { if (web.canGoBack()) web.goBack() })
        controls.addView(control("›") { if (web.canGoForward()) web.goForward() })
        controls.addView(control("↻") { web.reload() })
        controls.addView(address, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(control("Cerrar") { dialog.dismiss() })
        root.addView(heading)
        root.addView(controls)
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(root)
        dialog.setOnDismissListener {
            browserViews.remove(id)?.destroy()
            browserAddresses.remove(id)
            browserDialogs.remove(id)
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && web.canGoBack()) {
                web.goBack(); true
            } else false
        }
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        browserDialogs[id] = dialog
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        web.loadUrl(url)
    }

    private fun closeBrowser(id: String) {
        browserDialogs[id]?.dismiss()
    }

    private fun startKeepAliveService() {
        val intent = Intent(this, KeepAliveService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo iniciar la notificación de sesión.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val webState = Bundle()
        desktopView.saveState(webState)
        outState.putBundle(stateKey, webState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        val active = browserDialogs.entries.lastOrNull()
        if (active != null) {
            val web = browserViews[active.key]
            if (web?.canGoBack() == true) web.goBack() else active.value.dismiss()
        } else if (desktopView.canGoBack()) desktopView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        // No detener el servicio al salir temporalmente de la pantalla.
        super.onDestroy()
    }
}
