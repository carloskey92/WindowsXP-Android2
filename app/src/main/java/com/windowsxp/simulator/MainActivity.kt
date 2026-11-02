package com.windowsxp.simulator

import android.app.Activity
import android.app.Dialog
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
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

class MainActivity : Activity() {

    private lateinit var desktopView: WebView

    private val stateKey = "windows_xp_state"

    private val browserDialogs = mutableMapOf<String, Dialog>()
    private val browserViews = mutableMapOf<String, WebView>()
    private val browserAddresses = mutableMapOf<String, EditText>()

    private val videoStates = mutableMapOf<String, Boolean>()

    private var videoPlaying = false

    // ---------------------------------------------------------
    // INICIO DE LA APLICACIÓN
    // ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemBars()

        desktopView = createDesktopWebView()
        setContentView(desktopView)

        if (savedInstanceState != null) {
            val savedHtml = savedInstanceState.getString(stateKey)

            if (!savedHtml.isNullOrEmpty()) {
                desktopView.restoreState(savedInstanceState)
            } else {
                loadDesktop()
            }
        } else {
            loadDesktop()
        }

        startKeepAliveService()
        requestNotificationPermission()
    }

    private fun loadDesktop() {
        desktopView.loadUrl("file:///android_asset/index.html")
    }

    // ---------------------------------------------------------
    // WEBVIEW DEL ESCRITORIO WINDOWS XP
    // ---------------------------------------------------------

    private fun createDesktopWebView(): WebView {
        val web = WebView(this)

        configureWebView(web)

        web.addJavascriptInterface(
            AndroidBrowserBridge(),
            "AndroidBrowser"
        )

        web.addJavascriptInterface(
            VideoPipBridge(),
            "VideoPip"
        )

        web.webChromeClient = WebChromeClient()

        web.webViewClient = object : WebViewClient() {

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                try {
                    view.destroy()
                } catch (_: Exception) {
                }

                desktopView = createDesktopWebView()
                setContentView(desktopView)
                loadDesktop()

                return true
            }
        }

        return web
    }

    private fun configureWebView(web: WebView) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode =
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        web.setBackgroundColor(Color.WHITE)

        web.isFocusable = true
        web.isFocusableInTouchMode = true
    }

    // ---------------------------------------------------------
    // COMUNICACIÓN ENTRE JAVASCRIPT Y ANDROID
    // ---------------------------------------------------------

    inner class AndroidBrowserBridge {

        @JavascriptInterface
        fun open(id: String, url: String, title: String) {
            runOnUiThread {
                showBrowser(id, url, title)
            }
        }

        @JavascriptInterface
        fun close(id: String) {
            runOnUiThread {
                closeBrowser(id)
            }
        }

        @JavascriptInterface
        fun back(id: String) {
            runOnUiThread {
                browserViews[id]?.let { web ->
                    if (web.canGoBack()) {
                        web.goBack()
                    }
                }
            }
        }

        @JavascriptInterface
        fun forward(id: String) {
            runOnUiThread {
                browserViews[id]?.let { web ->
                    if (web.canGoForward()) {
                        web.goForward()
                    }
                }
            }
        }

        @JavascriptInterface
        fun reload(id: String) {
            runOnUiThread {
                browserViews[id]?.reload()
            }
        }
    }

    // ---------------------------------------------------------
    // BOTÓN PARA ACTIVAR PICTURE-IN-PICTURE
    // ---------------------------------------------------------

    inner class VideoPipBridge {

        @JavascriptInterface
        fun activar() {
            runOnUiThread {
                enterVideoPip()
            }
        }
    }

    // ---------------------------------------------------------
    // DETECCIÓN DE VIDEO EN LAS VENTANAS DEL NAVEGADOR
    // ---------------------------------------------------------

    inner class AndroidVideoBridge(
        private val browserId: String
    ) {

        @JavascriptInterface
        fun setVideoPlaying(playing: Boolean) {
            runOnUiThread {
                videoStates[browserId] = playing
                updateVideoPlayingState()
            }
        }
    }

    private fun updateVideoPlayingState() {
        videoPlaying = videoStates.values.any { it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updatePictureInPictureParams()
        }
    }

    private fun installVideoDetector(web: WebView) {
        val script = """
            (function() {
                if (window.__androidVideoDetectorInstalled) return;
                window.__androidVideoDetectorInstalled = true;

                function checkVideos() {
                    try {
                        var videos = document.querySelectorAll('video');
                        var playing = false;

                        for (var i = 0; i < videos.length; i++) {
                            var v = videos[i];

                            if (!v.paused && !v.ended &&
                                v.readyState >= 2) {
                                playing = true;
                                break;
                            }
                        }

                        if (window.AndroidVideo) {
                            window.AndroidVideo.setVideoPlaying(playing);
                        }
                    } catch (e) {}
                }

                document.addEventListener(
                    'play', checkVideos, true
                );

                document.addEventListener(
                    'pause', checkVideos, true
                );

                document.addEventListener(
                    'ended', checkVideos, true
                );

                setInterval(checkVideos, 1500);

                checkVideos();
            })();
        """.trimIndent()

        web.evaluateJavascript(script, null)
    }

    // ---------------------------------------------------------
    // VENTANAS NATIVAS DE INTERNET EXPLORER
    // ---------------------------------------------------------

    private fun showBrowser(
        id: String,
        initialUrl: String,
        title: String
    ) {
        // Evitar duplicar una ventana con el mismo identificador.
        browserDialogs[id]?.let { existing ->
            if (existing.isShowing) {
                existing.show()
                return
            }
        }

        val dialog = Dialog(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(235, 235, 235))
        }

        val heading = TextView(this).apply {
            text = if (title.isBlank()) {
                "Internet Explorer"
            } else {
                title
            }

            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 8, 14, 8)
            setBackgroundColor(Color.rgb(0, 75, 160))
        }

        val addressBar = EditText(this).apply {
            hint = "https://www.example.com"
            singleLine = true
            textSize = 14f
            setText(initialUrl)
            setPadding(10, 6, 10, 6)
        }

        browserAddresses[id] = addressBar

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 4, 4, 4)
        }

        fun addToolButton(
            label: String,
            action: () -> Unit
        ) {
            val button = Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setPadding(2, 0, 2, 0)
            }

            toolbar.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    44,
                    1f
                )
            )

            button.setOnClickListener {
                action()
            }
        }

        val web = WebView(this)

        configureWebView(web)

        web.addJavascriptInterface(
            AndroidVideoBridge(id),
            "AndroidVideo"
        )

        web.webChromeClient = WebChromeClient()

        addToolButton("←") {
            if (web.canGoBack()) web.goBack()
        }

        addToolButton("→") {
            if (web.canGoForward()) web.goForward()
        }

        addToolButton("↻") {
            web.reload()
        }

        addToolButton("Ir") {
            navigateBrowser(web, addressBar.text.toString())
        }

        addToolButton("✕") {
            closeBrowser(id)
        }

        root.addView(
            heading,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                42
            )
        )

        root.addView(
            addressBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                50
            )
        )

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48
            )
        )

        root.addView(
            web,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        dialog.setContentView(root)

        dialog.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )

            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }

        browserDialogs[id] = dialog
        browserViews[id] = web

        web.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()

                return if (
                    url.startsWith("https://") ||
                    url.startsWith("http://")
                ) {
                    false
                } else {
                    true
                }
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: android.graphics.Bitmap?
            ) {
                addressBar.setText(url)
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {
                addressBar.setText(url)
                installVideoDetector(view)
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                videoStates.remove(id)
                updateVideoPlayingState()

                browserViews.remove(id)
                browserAddresses.remove(id)

                try {
                    view.destroy()
                } catch (_: Exception) {
                }

                browserDialogs.remove(id)?.dismiss()

                Toast.makeText(
                    this@MainActivity,
                    "La ventana del navegador se cerró por un error.",
                    Toast.LENGTH_LONG
                ).show()

                return true
            }
        }

        dialog.setOnDismissListener {
            videoStates.remove(id)
            updateVideoPlayingState()

            browserViews.remove(id)
            browserAddresses.remove(id)
            browserDialogs.remove(id)

            try {
                web.stopLoading()
                web.loadUrl("about:blank")
                web.removeAllViews()
                web.destroy()
            } catch (_: Exception) {
            }
        }

        dialog.show()

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        navigateBrowser(web, initialUrl)
    }

    private fun navigateBrowser(web: WebView, input: String) {
        var url = input.trim()

        if (url.isEmpty()) {
            return
        }

        if (!url.contains("://")) {
            url = if (
                url.contains(".") &&
                !url.contains(" ")
            ) {
                "https://$url"
            } else {
                "https://www.google.com/search?q=" +
                    java.net.URLEncoder.encode(
                        url,
                        "UTF-8"
                    )
            }
        }

        if (
            url.startsWith("https://") ||
            url.startsWith("http://")
        ) {
            web.loadUrl(url)
        } else {
            Toast.makeText(
                this,
                "Dirección no compatible.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun closeBrowser(id: String) {
        browserDialogs[id]?.dismiss()
    }

    // ---------------------------------------------------------
    // PICTURE-IN-PICTURE
    // ---------------------------------------------------------

    private fun enterVideoPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(
                this,
                "El modo flotante requiere Android 8.0 o posterior.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (
            !packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE
            )
        ) {
            Toast.makeText(
                this,
                "Este dispositivo no admite Picture-in-Picture.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()

            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "No se pudo activar el modo flotante.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        try {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(videoPlaying)
            }

            setPictureInPictureParams(builder.build())
        } catch (_: Exception) {
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        // En Android 8 a Android 11, intentar entrar
        // automáticamente a PiP cuando se detecta video.
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            videoPlaying
        ) {
            enterVideoPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(
            isInPictureInPictureMode,
            newConfig
        )

        if (isInPictureInPictureMode) {
            // La ventana de la actividad pasa al modo flotante.
        } else {
            hideSystemBars()
        }
    }

    // ---------------------------------------------------------
    // SERVICIO EN SEGUNDO PLANO
    // ---------------------------------------------------------

    private fun startKeepAliveService() {
        try {
            val serviceIntent = android.content.Intent(
                this,
                KeepAliveService::class.java
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (
                checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ),
                    1001
                )
            }
        }
    }

    // ---------------------------------------------------------
    // PANTALLA COMPLETA
    // ---------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)

            window.insetsController?.let { controller ->
                controller.hide(
                    android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
                )

                controller.systemBarsBehavior =
                    android.view.WindowInsetsController
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }
    }

    // ---------------------------------------------------------
    // GUARDAR Y RESTAURAR ESTADO
    // ---------------------------------------------------------

    override fun onSaveInstanceState(outState: Bundle) {
        try {
            desktopView.saveState(outState)
        } catch (_: Exception) {
        }

        super.onSaveInstanceState(outState)
    }

    // ---------------------------------------------------------
    // BOTÓN ATRÁS
    // ---------------------------------------------------------

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            isInPictureInPictureMode
        ) {
            super.onBackPressed()
            return
        }

        val activeBrowser = browserViews.entries.lastOrNull()

        if (activeBrowser != null) {
            val web = activeBrowser.value

            if (web.canGoBack()) {
                web.goBack()
                return
            }

            closeBrowser(activeBrowser.key)
            return
        }

        if (::desktopView.isInitialized && desktopView.canGoBack()) {
            desktopView.goBack()
            return
        }

        super.onBackPressed()
    }

    // ---------------------------------------------------------
    // CIERRE
    // ---------------------------------------------------------

    override fun onDestroy() {
        for (dialog in browserDialogs.values.toList()) {
            try {
                dialog.dismiss()
            } catch (_: Exception) {
            }
        }

        browserDialogs.clear()
        browserViews.clear()
        browserAddresses.clear()
        videoStates.clear()

        try {
            if (::desktopView.isInitialized) {
                desktopView.stopLoading()
                desktopView.destroy()
            }
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
