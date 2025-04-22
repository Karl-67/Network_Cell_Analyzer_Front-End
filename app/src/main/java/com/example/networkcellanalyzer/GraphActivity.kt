package com.example.networkcellanalyzer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.loginapp.AboutAppActivity
import com.example.loginapp.LoginActivity
import com.example.networkcellanalyzer.utils.SessionManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView

class GraphActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart
    private lateinit var returnBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("GraphActivity", "✅ onCreate called!")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        // Top App Bar
        val toolbar = findViewById<Toolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)

        // Drawer & Navigation
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.navigation_view)
        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)

        // Bottom Navigation
        bottomNav = findViewById(R.id.bottomNavigation)
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.navigation_radio -> {
                    startActivity(Intent(this, StatisticsActivity::class.java))
                    true
                }
                R.id.navigation_square -> {
                    finish()
                    true
                }
                R.id.navigation_help -> {
                    startActivity(Intent(this, AboutAppActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Views
        pieChart = findViewById(R.id.pieChart)
        barChart = findViewById(R.id.barChart)
        returnBtn = findViewById(R.id.returnBtn)

        returnBtn.setOnClickListener { finish() }

        loadChart()
    }

    private fun loadChart() {
        val title = intent.getStringExtra("title") ?: "Graph"
        val chartType = intent.getStringExtra("chart_type") ?: ""
        val labels = intent.getStringArrayListExtra("labels") ?: arrayListOf()
        val values = intent.getFloatArrayExtra("values") ?: floatArrayOf()

        supportActionBar?.title = title

        Log.d("GraphActivity", "Loading chart: $title ($chartType)")
        Log.d("GraphActivity", "Labels: $labels")
        Log.d("GraphActivity", "Values: ${values.joinToString()}")

        if (labels.isEmpty() || values.isEmpty() || labels.size != values.size) {
            Toast.makeText(this, "Chart data is missing or mismatched.", Toast.LENGTH_LONG).show()
            Log.e("GraphActivity", "Invalid chart data: Labels: $labels, Values: ${values.contentToString()}")
            return
        }

        when (chartType) {
            "pie" -> {
                pieChart.visibility = View.VISIBLE
                barChart.visibility = View.GONE

                val entries = mutableListOf<PieEntry>()
                var totalValue = 0f

                // First pass: calculate total for percentage
                for (value in values) {
                    if (value > 0f) totalValue += value
                }

                // Only create entries if we have a positive total
                if (totalValue > 0f) {
                    // Second pass: create the entries
                    for (i in labels.indices) {
                        val value = values[i]
                        // For pie charts, ensure values are positive
                        if (value > 0f) {
                            entries.add(PieEntry(value, labels[i]))
                            Log.d("GraphActivity", "Added pie entry: ${labels[i]} = $value")
                        }
                    }
                }

                if (entries.isEmpty()) {
                    Log.e("GraphActivity", "No entries for pie chart")
                    Toast.makeText(this, "No valid data for pie chart", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }

                val dataSet = PieDataSet(entries, "")
                dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
                dataSet.valueTextSize = 14f
                dataSet.valueFormatter = PercentFormatter(pieChart)

                val data = PieData(dataSet)
                pieChart.apply {
                    setUsePercentValues(true)
                    this.data = data
                    description = Description().apply { text = title }
                    setEntryLabelColor(android.graphics.Color.BLACK)
                    setEntryLabelTextSize(12f)
                    centerText = title
                    setCenterTextSize(16f)
                    legend.isEnabled = true
                    setDrawEntryLabels(false)
                    animateY(1000)
                    invalidate()
                }

                Log.d("GraphActivity", "Pie chart created with ${entries.size} entries")
            }

            "bar" -> {
                pieChart.visibility = View.GONE
                barChart.visibility = View.VISIBLE

                val entries = mutableListOf<BarEntry>()
                for (i in labels.indices) {
                    Log.d("GraphActivity", "Bar value for ${labels[i]}: ${values[i]}")
                    entries.add(BarEntry(i.toFloat(), values[i]))
                }

                val dataSet = BarDataSet(entries, "Values")
                dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
                dataSet.valueTextSize = 14f

                val barData = BarData(dataSet)
                barData.barWidth = 0.9f

                barChart.apply {
                    data = barData
                    setFitBars(true)
                    description = Description().apply { text = title }

                    val xAxis = this.xAxis
                    xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                    xAxis.granularity = 1f
                    xAxis.isGranularityEnabled = true
                    xAxis.labelRotationAngle = -45f
                    xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM

                    axisRight.isEnabled = false
                    legend.isEnabled = true
                    animateY(1000)
                    invalidate()
                }

                Log.d("GraphActivity", "Bar chart created with ${entries.size} entries")
            }

            else -> {
                Toast.makeText(this, "Unknown chart type: $chartType", Toast.LENGTH_SHORT).show()
                Log.e("GraphActivity", "Unknown chart type: $chartType")
            }
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