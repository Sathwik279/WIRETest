package com.example.wiretest.wifiService

import LaunchAppWorker
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.core.content.ContextCompat
import com.example.wiretest.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.ContextWrapper
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit


class WifiStateReceiver(private val wifiScanResult: MutableStateFlow<List<ScanResult>>) : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
            val wifiState =
                intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
            when (wifiState) {
                WifiManager.WIFI_STATE_DISABLED -> {
                    val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    wifiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context?.startActivity(wifiIntent)
                }

                WifiManager.WIFI_STATE_ENABLED -> {
//                    checkConnectedWifi(context)
                    val manager =
                        context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val wifiInfo = manager.connectionInfo

                    if (wifiInfo.ssid != "\"WIFI@VRSEC\"") { // Check WiFi name
                        val workRequest = OneTimeWorkRequestBuilder<LaunchAppWorker>().build()
                        WorkManager.getInstance(context).enqueue(workRequest)
                    }
                }
            }
        }
        if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
            val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
            when (wifiState) {
                WifiManager.WIFI_STATE_ENABLED -> {
                    schedulePeriodicWork(context) // Schedule background task
                }
            }
        }
//        } else if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
//            if (ContextCompat.checkSelfPermission(
//                    context?.applicationContext ?: return,
//                    Manifest.permission.ACCESS_FINE_LOCATION
//                ) == PackageManager.PERMISSION_GRANTED
//            ) {
//                val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as WifiManager
//                val scanResults = wifiManager.scanResults
//                wifiScanResult.tryEmit(scanResults)
//                Log.d("wifiScanResultsDance", "Scan Results: ${scanResults.size} results available.")
//                checkConnectedWifi(context)
//            } else {
//                Log.d("wifiScanResultsDance", "Permission denied to access Wi-Fi scan results.")
//            }

    }


    private fun schedulePeriodicWork(context: Context?) {
        val workRequest = PeriodicWorkRequestBuilder<LaunchAppWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context!!).enqueue(workRequest)
    }

    private fun checkConnectedWifi(context: Context?) {
        val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

        if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val info = wifiManager.connectionInfo
            val ssid = info.ssid.replace("\"", "")

            Log.d("wifiConnection", "Connected to SSID: $ssid")

            if (ssid == "WIFI@VRSEC") {
                if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    // No internet, likely a captive portal, open the app
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context?.startActivity(appIntent)
                }
            }
        }
    }



}
