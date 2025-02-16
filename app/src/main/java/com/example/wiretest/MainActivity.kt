package com.example.wiretest

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wiretest.wifiService.WifiStateReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.text.InputType
import android.widget.EditText
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE_LOCATION_PERMISSION = 1001
    private var isReceiverRegistered = false

    // StateFlow to hold Wi-Fi scan results
    private val wifiScanResult = MutableStateFlow<List<ScanResult>>(emptyList())
    private lateinit var wifiReceiver: WifiStateReceiver


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("my-prefs-file", MODE_PRIVATE)
        var userId = prefs.getString("userId", null)
        var userPass = prefs.getString("userPass",null)

        if (userId == null || userPass == null) {
            askForUserId(prefs) // UI initializes only after credentials are saved
        } else {
            initializeUI(userId, userPass)
        }

// displays a dialog, which is asynchronous,



        // Initialize the receiver with the MutableStateFlow
        wifiReceiver = WifiStateReceiver(wifiScanResult)

        // Register the receiver to listen for Wi-Fi scan results and state changes
        registerWifiReceiver()


        // Check for location permission
        checkLocationPermission()




    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onResume() {
        super.onResume()
        registerWifiReceiver()
        checkLocationPermission()
    }

    private fun initializeUI(userId: String, userPass: String) {
        setContentView(R.layout.activity_main)

        wifiReceiver = WifiStateReceiver(wifiScanResult)
        registerWifiReceiver()
        checkLocationPermission()

        val webView: WebView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("http://192.168.2.253:8090")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.evaluateJavascript(
                    """
                document.getElementById('username').value = "$userId";
                document.getElementById('password').value = "$userPass";
                document.getElementById('login-button').click();
                """.trimIndent(), null
                )
            }
        }
    }

    private fun askForUserId(prefs: SharedPreferences) {
        val context = this
        val userIdBuilder = AlertDialog.Builder(context).setTitle("Enter your User ID")
        val inputUserId = EditText(context)
        inputUserId.hint = "User ID"
        userIdBuilder.setView(inputUserId)

        userIdBuilder.setPositiveButton("Next") { _, _ ->
            val enteredUserId = inputUserId.text.toString().trim()
            if (enteredUserId.isNotEmpty()) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        prefs.edit().putString("userId", enteredUserId).apply()
                    }
                    askForPassword(prefs)
                }
            }
        }
        userIdBuilder.setNegativeButton("Cancel", null)
        userIdBuilder.setCancelable(true).show()
    }

    private fun askForPassword(prefs: SharedPreferences) {
        val context = this
        val passwordBuilder = AlertDialog.Builder(context).setTitle("Enter your Password")
        val inputPassword = EditText(context)
        inputPassword.hint = "Password"
        inputPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordBuilder.setView(inputPassword)

        passwordBuilder.setPositiveButton("Save") { _, _ ->
            val enteredPassword = inputPassword.text.toString().trim()
            if (enteredPassword.isNotEmpty()) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        prefs.edit().putString("userPass", enteredPassword).apply()
                    }
                    val userId = prefs.getString("userId", null)
                    if (userId != null) initializeUI(userId, enteredPassword)
                }
            }
        }
        passwordBuilder.setNegativeButton("Cancel", null)
        passwordBuilder.setCancelable(true).show()
    }




    private fun registerWifiReceiver() {
        if (!isReceiverRegistered) {
            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            registerReceiver(wifiReceiver, intentFilter)
            isReceiverRegistered = true
        }
    }

    private fun observeWifiScanResults() {
        lifecycleScope.launch {
            wifiScanResult.collect { scanResults ->
                displayWifiResults(scanResults)
            }
        }
    }


    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_LOCATION_PERMISSION
            )
        }else{
            /// measn already give just look for scan results
            observeWifiScanResults()
        }
    }

    // Handling the permission request result
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, Wi-Fi scanning will automatically happen as already registered
                observeWifiScanResults()
                Log.d("WifiScan", "Permission granted")
            } else {
                // Permission denied, handle appropriately
                Log.d("WifiScan", "Permission denied")
            }
        }
    }

    private fun displayWifiResults(scanResults: List<ScanResult>) {
        val wifiDetails = scanResults.joinToString("\n\n") {
            "SSID: ${it.SSID}\nBSSID: ${it.BSSID}\nRSSI: ${it.level} dBm\nFrequency: ${it.frequency} MHz"
        }
       // findViewById<TextView>(R.id.wifiInfoText).text = wifiDetails
    }


    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver to avoid memory leaks
        unregisterReceiver(wifiReceiver)
    }
}