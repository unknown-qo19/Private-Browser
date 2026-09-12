package com.example.privatebrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
            webView.loadUrl("about:blank")
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

        webView.loadUrl("https://duckduckgo.com")

        checkPrivateDns()
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
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                addressBar.setText(url ?: "")
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
