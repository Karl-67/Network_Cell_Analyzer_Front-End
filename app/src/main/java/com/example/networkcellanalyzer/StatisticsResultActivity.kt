package com.example.networkcellanalyzer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.loginapp.AboutAppActivity
import com.example.loginapp.LoginActivity
import com.example.networkcellanalyzer.model.SignalPowerDeviceResponse
import com.example.networkcellanalyzer.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import utils.ApiClient
import java.text.SimpleDateFormat
import java.util.*

class StatisticsResultActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var operatorStatsValue: TextView
    private lateinit var networkTypeStatsValue: TextView
    private lateinit var signalPowerPerNetworkValue: TextView
    private lateinit var signalPowerPerDeviceValue: TextView
    private lateinit var sinrStatsValue: TextView

    private lateinit var token: String
    private lateinit var username: String
    private lateinit var deviceId: String
    private lateinit var startTime: String
    private lateinit var endTime: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics_result)

        val session = SessionManager(this)
        token = session.getAuthToken() ?: ""
        username = session.getUsername() ?: ""
        deviceId = session.getDeviceId() ?: ""

        startTime = intent.getStringExtra("start_time") ?: ""
        endTime = intent.getStringExtra("end_time") ?: ""

        setupUI()
        fetchAllStats()
    }

    private fun setupUI() {
        toolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.navigation_view)
        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)

        bottomNav = findViewById(R.id.bottomNavigation)
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.navigation_radio -> {
                    startActivity(Intent(this, StatisticsActivity::class.java).putExtras(getBundle()))
                    true
                }
                R.id.navigation_square -> {
                    finish()
                    true
                }
                R.id.navigation_help -> {
                    startActivity(Intent(this, AboutAppActivity::class.java).putExtras(getBundle()))
                    true
                }
                else -> false
            }
        }

        operatorStatsValue = findViewById(R.id.operatorStatsValue)
        networkTypeStatsValue = findViewById(R.id.networkTypeStatsValue)
        signalPowerPerNetworkValue = findViewById(R.id.signalPowerPerNetworkValue)
        signalPowerPerDeviceValue = findViewById(R.id.signalPowerPerDeviceValue)
        sinrStatsValue = findViewById(R.id.sinrStatsValue)

        findViewById<Button>(R.id.chooseAnotherDurationBtn).setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java).putExtras(getBundle()))
        }
    }

    private fun fetchAllStats() {
        val authHeader = "Bearer $token"
        val apiService = ApiClient.apiService
        val startISO = convertToIso(startTime)
        val endISO = convertToIso(endTime)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val opStats = apiService.getOperatorStats(authHeader, deviceId, startISO, endISO)
                val netStats = apiService.getNetworkTypeStats(authHeader, deviceId, startISO, endISO)
                val powerNet = apiService.getSignalPowerPerNetwork(authHeader, deviceId, startISO, endISO)
                val powerDev: SignalPowerDeviceResponse = apiService.getSignalPowerPerDevice(authHeader, deviceId, startISO, endISO)
                val sinr = apiService.getSINRStats(authHeader, deviceId, startISO, endISO)

                operatorStatsValue.text = "Alfa: ${opStats["Alfa"]}, touch: ${opStats["touch"]}"
                networkTypeStatsValue.text = "4G: ${netStats["4G"]}, 3G: ${netStats["3G"]}, 2G: ${netStats["2G"]}"
                signalPowerPerNetworkValue.text = "4G: ${powerNet["4G"]} dB, 3G: ${powerNet["3G"]} dB, 2G: ${powerNet["2G"]} dB"
                signalPowerPerDeviceValue.text = "Device: ${powerDev.device_id}, Power: ${powerDev.average_signal_power} dB"
                sinrStatsValue.text = "4G: ${sinr["4G"]} dB, 3G: ${sinr["3G"]} dB, 2G: ${sinr["2G"]} dB"

                findViewById<Button>(R.id.operatorStatsGraphBtn).setOnClickListener {
                    launchGraph(
                        "Average Connectivity Time Per Operator", "pie",
                        arrayListOf("Alfa", "Touch"),
                        floatArrayOf(
                            opStats["Alfa"]?.toString()?.toFloatOrNull() ?: 0f,
                            opStats["Touch"]?.toString()?.toFloatOrNull() ?: 0f
                        )
                    )
                }

                findViewById<Button>(R.id.networkTypeStatsGraphBtn).setOnClickListener {
                    launchGraph(
                        "Average Connectivity Time Per Network Type", "pie",
                        arrayListOf("2G", "3G", "4G"),
                        floatArrayOf(
                            netStats["2G"]?.toString()?.toFloatOrNull() ?: 0f,
                            netStats["3G"]?.toString()?.toFloatOrNull() ?: 0f,
                            netStats["4G"]?.toString()?.toFloatOrNull() ?: 0f
                        )
                    )
                }

                findViewById<Button>(R.id.signalPowerPerNetworkGraphBtn).setOnClickListener {
                    launchGraph(
                        "Average Signal Power Per Network Type", "pie",
                        arrayListOf("2G", "3G", "4G"),
                        floatArrayOf(
                            powerNet["2G"]?.toString()?.toFloatOrNull() ?: 0f,
                            powerNet["3G"]?.toString()?.toFloatOrNull() ?: 0f,
                            powerNet["4G"]?.toString()?.toFloatOrNull() ?: 0f
                        )
                    )
                }

                findViewById<Button>(R.id.signalPowerPerDeviceGraphBtn).setOnClickListener {
                    launchGraph(
                        "Average Signal Power Per Device", "bar",
                        arrayListOf(powerDev.device_id ?: "This Device"),
                        floatArrayOf(powerDev.average_signal_power?.toFloat() ?: 0f)
                    )
                }

                findViewById<Button>(R.id.sinrStatsGraphBtn).setOnClickListener {
                    launchGraph(
                        "Average SINR/SNR Per Network Type", "bar",
                        arrayListOf("2G", "3G", "4G"),
                        floatArrayOf(
                            sinr["2G"]?.toString()?.toFloatOrNull() ?: 0f,
                            sinr["3G"]?.toString()?.toFloatOrNull() ?: 0f,
                            sinr["4G"]?.toString()?.toFloatOrNull() ?: 0f
                        )
                    )
                }

            } catch (e: Exception) {
                Toast.makeText(this@StatisticsResultActivity, "Error loading stats: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun launchGraph(title: String, type: String, labels: ArrayList<String>, values: FloatArray) {
        val intent = Intent(this, GraphActivity::class.java)
        intent.putExtra("title", title)
        intent.putExtra("chart_type", type)
        intent.putStringArrayListExtra("labels", labels)
        intent.putExtra("values", values)
        startActivity(intent)
    }

    private fun convertToIso(dateStr: String): String {
        val inputFormat = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return outputFormat.format(inputFormat.parse(dateStr)!!)
    }

    private fun getBundle(): Bundle {
        return Bundle().apply {
            putString("token", token)
            putString("username", username)
            putString("device_id", deviceId)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.stats_top_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_help -> {
                startActivity(Intent(this, AboutStatsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_logout -> {
                SessionManager(this).clearSession()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                return true
            }
            R.id.nav_permissions -> {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                return true
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
