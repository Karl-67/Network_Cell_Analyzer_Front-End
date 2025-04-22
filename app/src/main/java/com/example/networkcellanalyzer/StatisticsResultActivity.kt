package com.example.networkcellanalyzer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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

                // Log raw data from API
                Log.d("StatisticsResultActivity", "Raw operator stats: $opStats")
                Log.d("StatisticsResultActivity", "Raw network stats: $netStats")
                Log.d("StatisticsResultActivity", "Raw power network: $powerNet")
                Log.d("StatisticsResultActivity", "Raw power device: ${powerDev.average_signal_power}")
                Log.d("StatisticsResultActivity", "Raw SINR: $sinr")

                // Display % for Alfa & Touch
                val alfa = opStats["Alfa"]?.toString()?.toFloatOrNull() ?: 0f
                val touch = opStats["touch"]?.toString()?.toFloatOrNull() ?: 0f  // fallback to lowercase
                val touchAlt = opStats["Touch"]?.toString()?.toFloatOrNull() ?: 0f
                val touchFinal = if (touchAlt > 0f) touchAlt else touch

                val total = alfa + touchFinal
                val alfaPct = if (total > 0) (alfa / total) * 100 else 0f
                val touchPct = if (total > 0) (touchFinal / total) * 100 else 0f
                val twoGValue = netStats["2G"]?.toString()?.replace("%", "")?.trim()?.toFloatOrNull() ?: 0f
                val threeGValue = netStats["3G"]?.toString()?.replace("%", "")?.trim()?.toFloatOrNull() ?: 0f
                val fourGValue = netStats["4G"]?.toString()?.replace("%", "")?.trim()?.toFloatOrNull() ?: 0f


                operatorStatsValue.text = "Alfa: ${"%.1f".format(alfaPct)}%, Touch: ${"%.1f".format(touchPct)}%"

                networkTypeStatsValue.text = "4G: ${netStats["4G"]}, 3G: ${netStats["3G"]}, 2G: ${netStats["2G"]}"
                signalPowerPerNetworkValue.text = "4G: ${powerNet["4G"]} dB, 3G: ${powerNet["3G"]} dB, 2G: ${powerNet["2G"]} dB"
                signalPowerPerDeviceValue.text = "Device: ${powerDev.device_id}, Power: ${powerDev.average_signal_power} dB"
                sinrStatsValue.text = "4G: ${sinr["4G"]} dB, 3G: ${sinr["3G"]} dB, 2G: ${sinr["2G"]} dB"

                // Button: Graphs
                findViewById<Button>(R.id.operatorStatsGraphBtn).setOnClickListener {
                    launchGraph(
                        "Average Connectivity Time Per Operator", "pie",
                        arrayListOf("Alfa", "Touch"),
                        floatArrayOf(alfa, touch)
                    )
                }

                findViewById<Button>(R.id.networkTypeStatsGraphBtn).setOnClickListener {
                    launchGraph(
                        "Average Connectivity Time Per Network Type", "pie",
                        arrayListOf("2G", "3G", "4G"),
                        floatArrayOf(
                            twoGValue,
                            threeGValue,
                            fourGValue
                        )
                    )
                }

                findViewById<Button>(R.id.signalPowerPerNetworkGraphBtn).setOnClickListener {
                    // For signal power, we need to handle negative values for pie charts
                    val power2G = powerNet["2G"]?.toString()?.toFloatOrNull() ?: 0f
                    val power3G = powerNet["3G"]?.toString()?.toFloatOrNull() ?: 0f
                    val power4G = powerNet["4G"]?.toString()?.toFloatOrNull() ?: 0f

                    // For pie charts, convert to absolute values and add offset to ensure positivity
                    val chartType = if (power2G < 0 || power3G < 0 || power4G < 0) "bar" else "pie"

                    launchGraph(
                        "Average Signal Power Per Network Type", chartType,
                        arrayListOf("2G", "3G", "4G"),
                        floatArrayOf(power2G, power3G, power4G)
                    )
                }

                findViewById<Button>(R.id.signalPowerPerDeviceGraphBtn).setOnClickListener {
                    val powerValue = powerDev.average_signal_power?.toFloat() ?: 0f

                    // For signal power, we'll always use bar chart to handle negative values
                    launchGraph(
                        "Average Signal Power Per Device", "bar",
                        arrayListOf(powerDev.device_id ?: "This Device"),
                        floatArrayOf(powerValue)
                    )
                }

                findViewById<Button>(R.id.sinrStatsGraphBtn).setOnClickListener {
                    val sinr2G = sinr["2G"]?.toString()?.toFloatOrNull() ?: 0f
                    val sinr3G = sinr["3G"]?.toString()?.toFloatOrNull() ?: 0f
                    val sinr4G = sinr["4G"]?.toString()?.toFloatOrNull() ?: 0f

                    // For SINR, use bar chart to handle potential negative values
                    launchGraph(
                        "Average SINR/SNR Per Network Type", "bar",
                        arrayListOf("2G", "3G", "4G"),
                        floatArrayOf(sinr2G, sinr3G, sinr4G)
                    )
                }

            } catch (e: Exception) {
                Toast.makeText(this@StatisticsResultActivity, "Error loading stats: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("StatisticsResultActivity", "Error loading stats", e)
                e.printStackTrace()
            }
        }
    }

    private fun launchGraph(title: String, type: String, labels: ArrayList<String>, values: FloatArray) {
        // Log the values before any filtering
        Log.d("StatisticsResultActivity", "Before filter - Labels: $labels | Values: ${values.joinToString()}")

        // For pie charts, data points must be positive
        if (type == "pie" && values.all { it <= 0f }) {
            Toast.makeText(this, "No positive values available for pie chart", Toast.LENGTH_SHORT).show()
            Log.e("StatisticsResultActivity", "No positive values for pie chart")
            return
        }

        // For bar charts, we should have at least some non-zero data
        if (type == "bar" && values.all { it == 0f }) {
            Toast.makeText(this, "No data available for bar chart", Toast.LENGTH_SHORT).show()
            Log.e("StatisticsResultActivity", "No data for bar chart")
            return
        }


        val intent = Intent(this, GraphActivity::class.java).apply {
            putExtra("title", title)
            putExtra("chart_type", type)
            putStringArrayListExtra("labels", labels)
            putExtra("values", values)
        }
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