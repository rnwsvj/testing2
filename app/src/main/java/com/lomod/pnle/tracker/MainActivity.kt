package com.lomod.pnle.tracker

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorLayout: ViewGroup
    private lateinit var retryButton: ViewGroup
    private lateinit var errorMessage: TextView

    private val targetUrl = "https://lomod-pnle-study-tracker.replit.app"
    private var authDialog: Dialog? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Status and navigation bar matching dark theme
        window.statusBarColor = android.graphics.Color.parseColor("#090a0f")
        window.navigationBarColor = android.graphics.Color.parseColor("#090a0f")

        // Screen WakeLock during study sessions
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        errorLayout = findViewById(R.id.errorLayout)
        retryButton = findViewById(R.id.retryButton)
        errorMessage = findViewById(R.id.errorMessage)

        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()

        retryButton.setOnClickListener {
            if (isNetworkAvailable()) {
                errorLayout.visibility = ViewGroup.GONE
                webView.visibility = ViewGroup.VISIBLE
                webView.reload()
            } else {
                errorMessage.text = "No internet connection detected. Please connect to Wi-Fi or Mobile Data."
            }
        }

        // Check if opened via Google Sign-In deep link
        val appLinkData: Uri? = intent?.data
        if (appLinkData != null) {
            webView.loadUrl(appLinkData.toString())
        } else if (savedInstanceState == null) {
            webView.loadUrl(targetUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    // Handles the redirect return when Google Sign-in finishes in browser or Custom Tab
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        val uri: Uri? = intent?.data
        if (uri != null) {
            // Dismiss any open popup dialog
            authDialog?.dismiss()
            authDialog = null

            // Flush cookies and load the authenticated callback URL into WebView
            CookieManager.getInstance().flush()
            val returnUrl = uri.toString()
            webView.loadUrl(returnUrl)
        } else {
            // Returned to app: flush and reload
            authDialog?.dismiss()
            authDialog = null
            CookieManager.getInstance().flush()
            webView.reload()
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure cookies set by browser or login sheet are synchronized
        CookieManager.getInstance().flush()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = true

        // Crucial for Clerk & Google Sign-In popups
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        // Clean User Agent to prevent Google OAuth 'disallowed_useragent' error!
        val defaultUserAgent = settings.userAgentString
        val cleanUserAgent = defaultUserAgent
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
        settings.userAgentString = "$cleanUserAgent LOMOD-Android-App/1.0"

        // Cookie Manager setup
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = ViewGroup.VISIBLE
                errorLayout.visibility = ViewGroup.GONE
                webView.visibility = ViewGroup.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = ViewGroup.GONE
                swipeRefresh.isRefreshing = false
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                showErrorScreen("Unable to connect to LOMOD Study Tracker. ($description)")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // If Google OAuth URL, allow in-app or Custom Tab
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress >= 100) {
                    progressBar.visibility = ViewGroup.GONE
                }
            }

            // Handles Clerk Google Sign-in popup inside an in-app sheet
            @SuppressLint("SetJavaScriptEnabled")
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (resultMsg == null) return false

                val popupWebView = WebView(this@MainActivity)
                val popupSettings = popupWebView.settings
                popupSettings.javaScriptEnabled = true
                popupSettings.domStorageEnabled = true
                popupSettings.databaseEnabled = true
                popupSettings.setSupportMultipleWindows(true)
                popupSettings.javaScriptCanOpenWindowsAutomatically = true

                // Use sanitized Chrome User-Agent so Google OAuth doesn't block it
                popupSettings.userAgentString = settings.userAgentString

                CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true)

                val dialog = Dialog(this@MainActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(popupWebView)
                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                dialog.show()
                authDialog = dialog

                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        super.onCloseWindow(window)
                        closeAuthDialogAndSync()
                    }
                }

                popupWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        CookieManager.getInstance().flush()

                        // When Google Sign-In completes and redirects back to targetUrl:
                        if (url != null && url.contains(targetUrl) && !url.contains("accounts.google.com") && !url.contains("/sign-in")) {
                            closeAuthDialogAndSync()
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false

                        // Check if Google Sign-In completed redirect back to app
                        if (url.contains(targetUrl) && !url.contains("accounts.google.com") && !url.contains("/sign-in")) {
                            closeAuthDialogAndSync()
                            return true
                        }
                        return false
                    }
                }

                val transport = resultMsg.obj as? WebView.WebViewTransport
                transport?.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                closeAuthDialogAndSync()
            }
        }
    }

    private fun closeAuthDialogAndSync() {
        authDialog?.dismiss()
        authDialog = null
        CookieManager.getInstance().flush()
        // Reload main WebView so authenticated session is instantly active
        webView.reload()
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(android.graphics.Color.parseColor("#38bdf8"))
        swipeRefresh.setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#1e293b"))
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (authDialog != null && authDialog?.isShowing == true) {
                    closeAuthDialogAndSync()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun showErrorScreen(msg: String) {
        progressBar.visibility = ViewGroup.GONE
        swipeRefresh.isRefreshing = false
        webView.visibility = ViewGroup.GONE
        errorLayout.visibility = ViewGroup.VISIBLE
        errorMessage.text = msg
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val actNw = cm.getNetworkCapabilities(network) ?: return false
        return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}
