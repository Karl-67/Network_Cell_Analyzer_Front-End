package com.example.networkcellanalyzer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.loginapp.AboutAppActivity
import com.example.loginapp.LoginActivity
import com.example.networkcellanalyzer.databinding.ActivityHomeBinding
import com.example.networkcellanalyzer.model.CellRecordSubmission
import com.example.networkcellanalyzer.model.NetworkData
import com.example.networkcellanalyzer.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import utils.ApiClient
import utils.DeviceInfoUtil
import utils.NetworkUtil
import utils.DeviceInfoUtil.getCurrentTimestamp

class HomeActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private const val SETTINGS_REQUEST_CODE = 102
    }

    private lateinit var deviceIdOutput: TextView
    private lateinit var macAddressOutput: TextView
    private lateinit var operatorOutput: TextView
    private lateinit var timeStampOutput: TextView
    private lateinit var sinrOutput: TextView
    private lateinit var networkTypeOutput: TextView
    private lateinit var frequencyBandOutput: TextView
    private lateinit var cellIdOutput: TextView

    private lateinit var sessionManager: SessionManager
    private val networkData = NetworkData(
        deviceId = "unknown",
        macAddress = "unknown",
        operator = "unknown",
        timestamp = "",
        sinr = 0.0,
        networkType = "unknown",
        frequencyBand = "unknown",
        cellId = "unknown",
        signalPower = 0.0
    )

    private lateinit var refreshOverlay: CardView
    private lateinit var refreshProgress: ProgressBar
    private lateinit var binding: ActivityHomeBinding
    private lateinit var noConnectionView: View
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 10000L
    private var isRefreshScheduled = false

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        setupBottomNavigation()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupNoConnectionView()

        deviceIdOutput = findViewById(R.id.deviceIdOutput)
        macAddressOutput = findViewById(R.id.macAddressOutput)
        operatorOutput = findViewById(R.id.operatorOutput)
        timeStampOutput = findViewById(R.id.timeStampOutput)
        sinrOutput = findViewById(R.id.sinrOutput)
        networkTypeOutput = findViewById(R.id.networkTypeOutput)
        frequencyBandOutput = findViewById(R.id.frequencyBandOutput)
        cellIdOutput = findViewById(R.id.cellIdOutput)

        refreshOverlay = findViewById(R.id.refresh_overlay)
        refreshProgress = findViewById(R.id.refresh_progress)
        refreshOverlay.visibility = View.GONE

        setupNavigationDrawer()
        startUpdatingTimestamp()

        findViewById<ImageView>(R.id.helpIcon).setOnClickListener {
            startActivity(Intent(this, AboutLiveViewActivity::class.java))
        }

        checkConnectionAndUpdateUI()
        startPeriodicConnectivityChecks()
        checkAndRequestPermissions()
    }

    private fun setupBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.selectedItemId = R.id.navigation_square

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_radio -> {
                    startActivity(Intent(this, StatisticsActivity::class.java))
                    true
                }
                R.id.navigation_square -> true
                R.id.navigation_help -> {
                    startActivity(Intent(this, AboutAppActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun startUpdatingTimestamp() { //this is to make sure that the timestamp is live
        val updateTimestampRunnable = object : Runnable {
            override fun run() {
                // Get current timestamp
                val timestamp = getCurrentTimestamp()
                // Update the TextView
                timeStampOutput.text = timestamp

                // Call this function again after 1 second (1000 milliseconds)
                handler.postDelayed(this, 1000)
            }
        }

        // Start the periodic updates
        handler.post(updateTimestampRunnable)
    }

    private fun checkAndRequestPermissions() { //requesting permissions from the user
        val locationPermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val phoneStatePermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        )

        // If both permissions are granted, load data
        if (locationPermission == PackageManager.PERMISSION_GRANTED &&
            phoneStatePermission == PackageManager.PERMISSION_GRANTED) {
            loadDeviceData()
            startUpdatingTimestamp()
            return
        }

        // Create a list of permissions to request
        val permissionsToRequest = mutableListOf<String>()

        if (locationPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (phoneStatePermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        // Request permissions
        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            REQUEST_PERMISSIONS
        )
    }

    // Handle permission result in one place
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted, load device data and continue the live time
                loadDeviceData()
                startUpdatingTimestamp()
            } else {
                startUpdatingTimestamp()
                // Load basic data without permissions
                val deviceId = sessionManager.getDeviceId() ?: DeviceInfoUtil.getDeviceId(this)
                val macAddress = sessionManager.getMacAddress() ?: DeviceInfoUtil.getMacAddress()
                val timestamp = DeviceInfoUtil.getCurrentTimestamp()

                cellIdOutput.text = "Permission required"
                operatorOutput.text = "Permission required"
                networkTypeOutput.text = "Permission required"
                frequencyBandOutput.text = "Permission required"
                sinrOutput.text = "N/A"

                deviceIdOutput.text = deviceId
                macAddressOutput.text = macAddress
                timeStampOutput.text = timestamp

                networkData.deviceId = deviceId
                networkData.macAddress = macAddress
                networkData.timestamp = timestamp

                // Check if user denied with "Don't ask again"
                val anyPermanentlyDenied = permissions.indices.any { i ->
                    grantResults[i] != PackageManager.PERMISSION_GRANTED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])
                }

                if (anyPermanentlyDenied) {
                    // Show settings dialog
                    showSettingsDialog()
                } else {
                    // Show permission explanation dialog
                    Toast.makeText(
                        this,
                        "Permissions are required for complete network information",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }



        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Location and Phone permissions are required to analyze network cells. Please enable them in app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivityForResult(intent, SETTINGS_REQUEST_CODE)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    private fun showLimitedFunctionality() {
        Toast.makeText(this, "Limited functionality due to missing permissions.", Toast.LENGTH_LONG).show()
        deviceIdOutput.text = DeviceInfoUtil.getDeviceId(this)
        macAddressOutput.text = DeviceInfoUtil.getMacAddress()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST_CODE) {
            // Check permissions again after returning from settings
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED) {
                startUpdatingTimestamp()
                loadDeviceData()
            } else {
                showSettingsDialog()

                // Still not granted
                deviceIdOutput.text = DeviceInfoUtil.getDeviceId(this)
                macAddressOutput.text = DeviceInfoUtil.getMacAddress()
                cellIdOutput.text = "Permission required"
                operatorOutput.text = "Permission required"
                networkTypeOutput.text = "Permission required"
                frequencyBandOutput.text = "Permission required"
                sinrOutput.text = "N/A"
            }
        }
    }

    private fun setupNoConnectionView() {
        noConnectionView = layoutInflater.inflate(R.layout.layout_no_connection, null)
        noConnectionView.findViewById<View>(R.id.buttonRetry).setOnClickListener {
            checkConnectionAndUpdateUI()
        }

        val bottomNav = noConnectionView.findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_radio -> {
                    startActivity(Intent(this, StatisticsActivity::class.java))
                    true
                }
                R.id.navigation_square -> true
                R.id.navigation_help -> {
                    startActivity(Intent(this, AboutAppActivity::class.java))
                    true
                }
                else -> false

            }
        }
    }

    private fun checkConnectionAndUpdateUI() {
        if (!NetworkUtil.isInternetAvailable(this)) {
            showNoConnectionView()
        } else {
            startUpdatingTimestamp()
            hideNoConnectionView()
            if (!::refreshOverlay.isInitialized || refreshOverlay.visibility != View.VISIBLE) {
                startPeriodicRefresh()
            }
        }
    }

    private fun startPeriodicRefresh() {
        if (isRefreshScheduled) return
        isRefreshScheduled = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (NetworkUtil.isInternetAvailable(this@HomeActivity)) {
                    refreshData()
                }
                handler.postDelayed(this, refreshInterval)
            }
        }, refreshInterval)
    }

    private fun refreshData() {
        refreshOverlay.visibility = View.VISIBLE
        handler.postDelayed({ refreshOverlay.visibility = View.GONE }, 1500)

    }

    private fun startPeriodicConnectivityChecks() {
        lifecycleScope.launch {
            while (true) {
                delay(5000)
                checkConnectionAndUpdateUI()
            }
        }
    }

    private fun showNoConnectionView() {
        if (noConnectionView.parent == null) {
            (binding.root as ViewGroup).addView(noConnectionView)
        }
        binding.scrollContent.visibility = View.GONE
        noConnectionView.visibility = View.VISIBLE
        handler.removeCallbacksAndMessages(null)
    }

    private fun hideNoConnectionView() {
        if (noConnectionView.parent != null) {
            (binding.root as ViewGroup).removeView(noConnectionView)
        }
        binding.scrollContent.visibility = View.VISIBLE
        getCurrentTimestamp()
    }

    private fun setupNavigationDrawer() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val navView = findViewById<NavigationView>(R.id.nav_view)

        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_permissions -> {
                    showPermissionsDialog()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_logout -> {
                    sessionManager.clearSession()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }
    }

    private fun showPermissionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("1. Location: Detect cell and signal\n2. Phone: Access phone status\n\nNo personal data is collected.")
            .setPositiveButton("OK") { _, _ -> }
            .show()
        getCurrentTimestamp()
    }

    private fun loadDeviceData() {
        val deviceId = sessionManager.getDeviceId() ?: DeviceInfoUtil.getDeviceId(this)
        val macAddress = sessionManager.getMacAddress() ?: DeviceInfoUtil.getMacAddress()
        val ipAddress = sessionManager.getIpAddress() ?: DeviceInfoUtil.getIPAddress()
        val timestamp = DeviceInfoUtil.getCurrentTimestamp()

        deviceIdOutput.text = deviceId
        macAddressOutput.text = macAddress
        timeStampOutput.text = timestamp

        networkData.deviceId = deviceId
        networkData.macAddress = macAddress
        networkData.timestamp = timestamp
        getCurrentTimestamp()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val cellId = DeviceInfoUtil.getCellId(this)
            cellIdOutput.text = if (cellId != -1) cellId.toString() else "Permission required"
            networkData.cellId = cellId.toString()

            val operatorName = DeviceInfoUtil.getOperatorName(this)
            operatorOutput.text = operatorName
            networkData.operator = operatorName

            val networkType = DeviceInfoUtil.getNetworkType(this)
            networkTypeOutput.text = networkType
            networkData.networkType = networkType

            val frequencyBand = DeviceInfoUtil.getFrequencyBand(this)
            frequencyBandOutput.text = frequencyBand
            networkData.frequencyBand = frequencyBand

            val (sinr, signalPower) = DeviceInfoUtil.getSignalMetrics(this)
            sinrOutput.text = String.format("%.1f dB", sinr)
            networkData.sinr = sinr
            networkData.signalPower = signalPower

            Log.d("DeviceInfo", "Device ID: $deviceId, MAC: $macAddress, IP: $ipAddress")
        } else {
            cellIdOutput.text = "Permission required"
            operatorOutput.text = "Permission required"
            networkTypeOutput.text = "Permission required"
            frequencyBandOutput.text = "Permission required"
            sinrOutput.text = "N/A"
        }

        submitNetworkData()
    }

    private fun submitNetworkData() {
        lifecycleScope.launch {
            try {
                val token = sessionManager.getAuthToken() ?: return@launch
                val submission = CellRecordSubmission(
                    operator = networkData.operator,
                    signal_power = networkData.signalPower,
                    sinr = networkData.sinr,
                    network_type = networkData.networkType,
                    frequency_band = networkData.frequencyBand,
                    cell_id = networkData.cellId,
                    timestamp = DeviceInfoUtil.getFormattedTimestampForApi(),
                    device_mac = networkData.macAddress,
                    device_id = networkData.deviceId
                )
                ApiClient.apiService.submitNetworkData(submission, "Bearer $token")
            } catch (e: Exception) {
                Log.e("SubmitError", e.toString())
            }
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        isRefreshScheduled = false
    }

    override fun onResume() {
        super.onResume()
        checkConnectionAndUpdateUI()
    }
}
