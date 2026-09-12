package com.example.privatebrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs: SharedPreferences

    private val homeActionScheme = "privatebrowserapp"

    private val blockedHosts = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "facebook.net",
        "connect.facebook.net",
        "amazon-adsystem.com",
        "adnxs.com",
        "scorecardresearch.com",
        "outbrain.com",
        "taboola.com",
        "criteo.com",
        "pubmatic.com",
        "rubiconproject.com",
        "moatads.com",
        "quantserve.com",
        "adsrvr.org",
        "mopub.com",
        "chartboost.com",
        "unityads.unity3d.com",
        "applovin.com",
        "vungle.com",
        "adcolony.com",
        "smartadserver.com",
        "openx.net",
        "bidswitch.net",
        "casalemedia.com"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("private_browser_prefs", MODE_PRIVATE)

        webView = findViewById(R.id.webView)
        addressBar = findViewById(R.id.addressBar)
        progressBar = findViewById(R.id.progressBar)
        val goButton: ImageButton = findViewById(R.id.goButton)
        val wipeButton: ImageButton = findViewById(R.id.wipeButton)

        wipeAllBrowsingData()

        configurePrivateWebView()

        goButton.setOnClickListener { loadFromAddressBar() }
        wipeButton.setOnClickListener {
            wipeAllBrowsingData()
            loadHomePage()
            addressBar.setText("")
            Toast.makeText(this, "All browsing data wiped", Toast.LENGTH_SHORT).show()
        }

        addressBar.setOnEditorActionListener { _, actionId, event ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            if (isGo) {
                loadFromAddressBar()
                true
            } else {
                false
            }
        }

        loadHomePage()

        checkPrivateDns()
    }

    private fun displayName(): String = prefs.getString("display_name", null) ?: "You"

    private fun isAdBlockEnabled(): Boolean = prefs.getBoolean("ad_block_enabled", true)

    private fun toggleAdBlock() {
        val newValue = !isAdBlockEnabled()
        prefs.edit().putBoolean("ad_block_enabled", newValue).apply()
        Toast.makeText(
            this,
            if (newValue) "Ad blocker turned on" else "Ad blocker turned off",
            Toast.LENGTH_SHORT
        ).show()
        loadHomePage()
    }

    private fun isBlockedHost(host: String?): Boolean {
        if (host == null || !isAdBlockEnabled()) return false
        return blockedHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }
    }

    private fun initialsFor(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts.last().take(1)).uppercase()
        }
    }

    private fun colorFor(name: String): String {
        val palette = listOf("#00C853", "#2979FF", "#FF6D00", "#D500F9", "#00B8D4", "#FFAB00")
        val index = (name.sumOf { it.code }) % palette.size
        return palette[index]
    }

    private fun promptRename() {
        val input = EditText(this)
        input.setText(displayName())
        input.hint = "Your name"

        AlertDialog.Builder(this)
            .setTitle("Set your name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim().ifEmpty { "You" }
                prefs.edit().putString("display_name", newName).apply()
                loadHomePage()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadHomePage() {
        val name = displayName()
        val initials = initialsFor(name)
        val avatarColor = colorFor(name)
        val adBlockOn = isAdBlockEnabled()
        val adBlockLabel = if (adBlockOn) "Ad blocker: ON" else "Ad blocker: OFF"
        val adBlockLinkColor = if (adBlockOn) "#00C853" else "#FF5252"

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body {
                        margin: 0;
                        background: #121212;
                        color: #FFFFFF;
                        font-family: sans-serif;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        padding: 24px;
                        box-sizing: border-box;
                    }
                    .avatar {
                        width: 84px;
                        height: 84px;
                        border-radius: 50%;
                        background: $avatarColor;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 32px;
                        font-weight: bold;
                        color: #121212;
                        margin-bottom: 12px;
                    }
                    .name {
                        font-size: 16px;
                        color: #AAAAAA;
                        margin-bottom: 20px;
                    }
                    .name a {
                        color: #00C853;
                        text-decoration: none;
                        margin-left: 8px;
                    }
                    .toggle {
                        margin-bottom: 32px;
                    }
                    .toggle a {
                        color: $adBlockLinkColor;
                        text-decoration: none;
                        font-size: 14px;
                        border: 1px solid $adBlockLinkColor;
                        border-radius: 20px;
                        padding: 8px 16px;
                    }
                    form {
                        width: 100%;
                        max-width: 420px;
                    }
                    input[type="text"] {
                        width: 100%;
                        padding: 14px;
                        border-radius: 8px;
                        border: none;
                        background: #1F1F1F;
                        color: #FFFFFF;
                        font-size: 16px;
                        box-sizing: border-box;
                    }
                    .tagline {
                        margin-top: 40px;
                        font-size: 13px;
                        color: #666666;
                        text-align: center;
                    }
                </style>
            </head>
            <body>
                <div class="avatar">$initials</div>
                <div class="name">$name <a href="$homeActionScheme://rename">rename</a></div>
                <div class="toggle"><a href="$homeActionScheme://toggle_adblock">$adBlockLabel</a></div>
                <form action="https://duckduckgo.com/html/" method="get">
                    <input type="text" name="q" placeholder="Search privately" autofocus>
                </form>
                <div class="tagline">No history. No cookies. No tracking.</div>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
        addressBar.setText("")
    }

    private fun checkPrivateDns() {
        val mode = Settings.Global.getString(contentResolver, "private_dns_mode")
        val isOn = mode == "hostname" || mode == "opportunistic"

        if (!isOn) {
            AlertDialog.Builder(this)
                .setTitle("DNS lookups aren't encrypted")
                .setMessage(
                    "Your network provider can currently see which sites you " +
                        "visit, even in this app, through unencrypted DNS. " +
                        "Turning on Private DNS (Settings > Network) hides " +
                        "that for your whole phone."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }
                .setNegativeButton("Not now", null)
                .show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurePrivateWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true

        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.saveFormData = false
        settings.domStorageEnabled = false
        settings.databaseEnabled = false

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(false)
        cookieManager.setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("$homeActionScheme://rename")) {
                    promptRename()
                    return true
                }
                if (url.startsWith("$homeActionScheme://toggle_adblock")) {
                    toggleAdBlock()
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val host = request?.url?.host
                if (isBlockedHost(host)) {
                    return WebResourceResponse("text/plain", "utf-8", null)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == "file:///android_asset/" || url == "about:blank") {
                    addressBar.setText("")
                } else {
                    addressBar.setText(url ?: "")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress in 1..99) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun loadFromAddressBar() {
        val input = addressBar.text.toString().trim()
        if (input.isEmpty()) return

        if (input.equals("home", ignoreCase = true)) {
            loadHomePage()
            return
        }

        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://duckduckgo.com/html/?q=${input.replace(" ", "+")}"
        }
        webView.loadUrl(url)
    }

    private fun wipeAllBrowsingData() {
        webView.clearCache(true)
        webView.clearFormData()
        webView.clearHistory()
        webView.clearSslPreferences()

        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        WebStorage.getInstance().deleteAllData()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        wipeAllBrowsingData()
        super.onDestroy()
    }
}
