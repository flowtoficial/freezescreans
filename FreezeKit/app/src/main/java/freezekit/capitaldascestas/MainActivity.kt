package freezekit.capitaldascestas

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import freezekit.capitaldascestas.config.BrowserConfig
import freezekit.capitaldascestas.web.BrowserUrlPolicy
import java.io.ByteArrayInputStream

class MainActivity : Activity() {
    private lateinit var config: BrowserConfig
    private lateinit var urlPolicy: BrowserUrlPolicy
    private lateinit var container: FrameLayout
    private lateinit var webView: WebView
    private lateinit var offlineMessage: TextView
    private lateinit var connectivityManager: ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var internetValidated = false
    private var offlineMessageVisible = false
    private var retryUrl: String? = null
    private var pendingCameraRequest: PermissionRequest? = null
    private var backInvokedCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        config = BrowserConfig(this)
        urlPolicy = BrowserUrlPolicy(config)
        connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        retryUrl = config.initialUrl

        container = FrameLayout(this)
        webView = createWebView()
        container.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        offlineMessage = TextView(this).apply {
            text = getString(R.string.offline_message)
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(35, 45, 40))
            setBackgroundColor(Color.rgb(244, 247, 245))
            visibility = View.GONE
        }
        container.addView(
            offlineMessage,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(container)
        registerBackCallbackForAndroid13AndAbove()
        hideSystemBars()
        loadUrlIfAllowed(config.initialUrl)
        handleRequestedUrl(intent)
    }

    override fun onStart() {
        super.onStart()
        startNetworkMonitoring()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            hideSystemBars()
            grantCameraPermissionIfDeviceOwner()
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            webView.onPause()
        }
        super.onPause()
    }

    override fun onStop() {
        stopNetworkMonitoring()
        super.onStop()
    }

    override fun onDestroy() {
        pendingCameraRequest?.deny()
        pendingCameraRequest = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
            backInvokedCallback = null
        }
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRequestedUrl(intent)
    }

    @Deprecated("Deprecated in the Android SDK; handled to keep Back within the WebView")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            ::webView.isInitialized && webView.canGoBack()
        ) {
            webView.goBack()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA_PERMISSION_REQUEST_CODE) {
            return
        }

        val request = pendingCameraRequest
        pendingCameraRequest = null
        val cameraGranted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (request != null && cameraGranted && isCameraRequestAllowed(request)) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        } else {
            request?.deny()
        }
    }

    private fun createWebView(): WebView = WebView(this).apply {
        isLongClickable = false
        setOnLongClickListener { true }

        settings.apply {
            setJavaScriptEnabled(true)
            setDomStorageEnabled(true)
            setSupportMultipleWindows(false)
            setJavaScriptCanOpenWindowsAutomatically(true)
            setAllowFileAccess(false)
            setAllowContentAccess(false)
            setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)
            setGeolocationEnabled(false)
            setSafeBrowsingEnabled(true)
        }

        setWebViewClient(createWebViewClient())
        setWebChromeClient(createWebChromeClient())
    }

    private fun createWebViewClient(): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = shouldBlockNavigation(request.url)

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            shouldBlockNavigation(Uri.parse(url))

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (request.isForMainFrame && !urlPolicy.isAllowed(request.url)) {
                showBlockedAddressNotice()
                return WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    403,
                    "Forbidden",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (urlPolicy.isAllowed(url)) {
                retryUrl = url
            }
            hideOfflineMessage()
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            hideOfflineMessage()
            super.onPageCommitVisible(view, url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                val failingUrl = request.url.toString()
                retryUrl = if (urlPolicy.isAllowed(failingUrl)) {
                    failingUrl
                } else {
                    config.initialUrl
                }
                showOfflineMessage()
            }
            super.onReceivedError(view, request, error)
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail
        ): Boolean {
            if (view !== webView) {
                return false
            }
            recreateWebViewAfterRendererExit(view)
            return true
        }
    }

    private fun createWebChromeClient(): WebChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread {
                handleWebPermissionRequest(request)
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingCameraRequest === request) {
                pendingCameraRequest = null
            }
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
            fileChooserParams: WebChromeClient.FileChooserParams
        ): Boolean = false

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: android.webkit.GeolocationPermissions.Callback
        ) {
            callback.invoke(origin, false, false)
        }
    }

    private fun shouldBlockNavigation(uri: Uri): Boolean {
        if (urlPolicy.isAllowed(uri)) {
            return false
        }
        showBlockedAddressNotice()
        return true
    }

    private fun loadUrlIfAllowed(url: String) {
        if (urlPolicy.isAllowed(url)) {
            retryUrl = url
            webView.loadUrl(url)
        } else {
            showBlockedAddressNotice()
        }
    }

    private fun handleRequestedUrl(intent: Intent?) {
        if (intent?.action != ACTION_REQUEST_URL) {
            return
        }
        val requestedUrl = intent.getStringExtra(EXTRA_REQUESTED_URL) ?: return
        loadUrlIfAllowed(requestedUrl)
    }

    private fun showBlockedAddressNotice() {
        runOnUiThread {
            if (!isFinishing) {
                Toast.makeText(
                    this,
                    getString(R.string.blocked_address_notice),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleWebPermissionRequest(request: PermissionRequest) {
        if (!isCameraRequestAllowed(request)) {
            request.deny()
            return
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            return
        }

        if (isDeviceOwner()) {
            request.deny()
            return
        }

        pendingCameraRequest?.takeIf { it !== request }?.deny()
        pendingCameraRequest = request
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun isCameraRequestAllowed(request: PermissionRequest): Boolean =
        request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
            urlPolicy.isAllowed(request.origin) &&
            urlPolicy.isCameraAllowed(request.origin)

    private fun grantCameraPermissionIfDeviceOwner() {
        if (!isDeviceOwner()) {
            return
        }

        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        devicePolicyManager.setPermissionGrantState(
            ComponentName(this, AdminReceiver::class.java),
            packageName,
            Manifest.permission.CAMERA,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )
    }

    private fun isDeviceOwner(): Boolean {
        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return devicePolicyManager.isDeviceOwnerApp(packageName)
    }

    private fun startNetworkMonitoring() {
        if (networkCallback != null) {
            return
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // O estado de internet é confirmado em onCapabilitiesChanged.
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasValidatedInternet =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                runOnUiThread {
                    val wasValidated = internetValidated
                    internetValidated = hasValidatedInternet
                    if (hasValidatedInternet && offlineMessageVisible) {
                        retryAfterNetworkReturns()
                    } else if (!hasValidatedInternet && wasValidated) {
                        rememberCurrentPageForRetry()
                        showOfflineMessage()
                    }
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    internetValidated = false
                    rememberCurrentPageForRetry()
                    showOfflineMessage()
                }
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        networkCallback = callback
    }

    private fun stopNetworkMonitoring() {
        val callback = networkCallback ?: return
        connectivityManager.unregisterNetworkCallback(callback)
        networkCallback = null
    }

    private fun rememberCurrentPageForRetry() {
        val currentUrl = if (::webView.isInitialized) webView.url else null
        retryUrl = if (!currentUrl.isNullOrBlank() && urlPolicy.isAllowed(currentUrl)) {
            currentUrl
        } else {
            config.initialUrl
        }
    }

    private fun showOfflineMessage() {
        if (!::offlineMessage.isInitialized) {
            return
        }
        offlineMessageVisible = true
        offlineMessage.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    private fun hideOfflineMessage() {
        if (!::offlineMessage.isInitialized) {
            return
        }
        offlineMessageVisible = false
        offlineMessage.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun retryAfterNetworkReturns() {
        if (!offlineMessageVisible) {
            return
        }
        val urlToRetry = retryUrl ?: config.initialUrl
        hideOfflineMessage()
        loadUrlIfAllowed(urlToRetry)
    }

    private fun recreateWebViewAfterRendererExit(oldWebView: WebView) {
        pendingCameraRequest?.deny()
        pendingCameraRequest = null
        (oldWebView.parent as? ViewGroup)?.removeView(oldWebView)
        oldWebView.destroy()

        retryUrl = config.initialUrl
        webView = createWebView()
        container.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        hideOfflineMessage()
        loadUrlIfAllowed(config.initialUrl)
    }

    private fun hideSystemBars() {
        val controller = window.insetsController ?: return
        controller.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsets.Type.systemBars())
    }

    private fun registerBackCallbackForAndroid13AndAbove() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val callback = OnBackInvokedCallback {
            if (::webView.isInitialized && webView.canGoBack()) {
                webView.goBack()
            }
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
        backInvokedCallback = callback
    }

    companion object {
        const val ACTION_REQUEST_URL = "freezekit.capitaldascestas.action.REQUEST_URL"
        const val EXTRA_REQUESTED_URL = "freezekit.capitaldascestas.extra.REQUESTED_URL"

        private const val CAMERA_PERMISSION_REQUEST_CODE = 201
    }
}
