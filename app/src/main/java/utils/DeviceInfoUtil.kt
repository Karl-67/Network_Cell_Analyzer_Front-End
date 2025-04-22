package utils

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceInfoUtil {

    fun getCurrentTimestamp(): String {
        val now = System.currentTimeMillis()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(now)) // Format the current time
    }

    // Secure device ID
    fun getDeviceId(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("DeviceInfoUtil", "Device ID: $id")
        return id
    }

    // MAC Address (will be dummy on Android 10+)
    fun getMacAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val mac = nif.hardwareAddress
                if (mac != null) {
                    return mac.joinToString(":") { String.format("%02X", it) }
                }
            }
            "02:00:00:00:00:00 - Function not supported by Android version"
        } catch (e: Exception) {
            "02:00:00:00:00:00"
        }
    }

    // IPv4 Address (e.g., 192.168.x.x)
    fun getIPAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val addresses = nif.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getCellId(context: Context): Int {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cellInfoList = telephonyManager.allCellInfo
            val cellInfo: CellInfo? = cellInfoList?.firstOrNull()
            Log.d("DeviceInfoUtil", "Cell info list size: ${cellInfoList?.size ?: 0}")

            return when (cellInfo) {
                is CellInfoLte -> cellInfo.cellIdentity.ci
                is CellInfoGsm -> cellInfo.cellIdentity.cid
                is CellInfoWcdma -> cellInfo.cellIdentity.cid
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                        val nrCell = cellInfo.cellIdentity as? CellIdentityNr
                        return nrCell?.nci?.toInt() ?: -1
                    } else {
                        return -1
                    }
                }
            }
        } catch (e: Exception) {
            return -1 // fallback if permission denied or error occurs
        }
    }

    // Get operator name
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getOperatorName(context: Context): String {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val operatorName = telephonyManager.networkOperatorName
            return if (operatorName.isNotEmpty()) operatorName else "Unknown"
        } catch (e: Exception) {
            return "Unknown"
        }
    }

    // Get network type
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE])
    fun getNetworkType(context: Context): String {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            return getNetworkTypeString(telephonyManager.dataNetworkType)
        } catch (e: Exception) {
            return "Unknown"
        }
    }

    // Helper method to convert network type int to string
    private fun getNetworkTypeString(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA -> "2G"

            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G"

            TelephonyManager.NETWORK_TYPE_LTE -> "4G"

            TelephonyManager.NETWORK_TYPE_NR -> "5G"

            else -> "Unknown"
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getFrequencyBand(context: Context): String {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cellInfoList = telephonyManager.allCellInfo

            if (!cellInfoList.isNullOrEmpty()) {
                val cellInfo = cellInfoList[0]  // Just get the first cell info as in original version

                return when (cellInfo) {
                    is CellInfoLte -> {
                        val lteCell = cellInfo.cellIdentity as CellIdentityLte
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val earfcn = lteCell.earfcn
                            val frequency = calculateLteFrequency(earfcn)
                            "$frequency MHz"
                        } else {
                            "4G LTE"
                        }
                    }

                    is CellInfoGsm -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val arfcn = cellInfo.cellIdentity.arfcn
                            val frequency = calculateGsmFrequency(arfcn)
                            "$frequency MHz"
                        } else {
                            "2G GSM"
                        }
                    }

                    is CellInfoWcdma -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val uarfcn = cellInfo.cellIdentity.uarfcn
                            val frequency = calculateWcdmaFrequency(uarfcn)
                            val band = getWcdmaBand(uarfcn)

                            if (band > 0) {
                                "$band $frequency MHz"
                            } else {
                                "$frequency MHz"
                            }
                        } else {
                            "3G WCDMA"
                        }
                    }

                    else -> {
                        // Handle 5G NR
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                            val nrCell = cellInfo.cellIdentity as CellIdentityNr
                            val nrarfcn = nrCell.nrarfcn
                            val frequency = calculateNrFrequency(nrarfcn)
                            "$frequency MHz"
                        } else {
                            "Unknown"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceInfoUtil", "Error getting frequency band", e)
        }
        return "Unknown"
    }

    // Helper method to calculate LTE frequency from EARFCN
    private fun calculateLteFrequency(earfcn: Int): Double {
        // Downlink frequency calculation
        return when {
            earfcn in 0..599 -> 2110.0 + 0.1 * (earfcn - 0)
            earfcn in 600..1199 -> 1930.0 + 0.1 * (earfcn - 600)
            earfcn in 1200..1949 -> 1805.0 + 0.1 * (earfcn - 1200)
            earfcn in 1950..2399 -> 2110.0 + 0.1 * (earfcn - 1950)
            earfcn in 2400..2649 -> 869.0 + 0.1 * (earfcn - 2400)
            earfcn in 2650..2749 -> 875.0 + 0.1 * (earfcn - 2650)
            earfcn in 2750..3449 -> 2620.0 + 0.1 * (earfcn - 2750)
            earfcn in 3450..3799 -> 925.0 + 0.1 * (earfcn - 3450)
            earfcn in 3800..4149 -> 1844.9 + 0.1 * (earfcn - 3800)
            earfcn in 4150..4749 -> 758.0 + 0.1 * (earfcn - 4150)
            earfcn in 4750..4999 -> 734.0 + 0.1 * (earfcn - 4750)
            earfcn in 5000..5179 -> 860.0 + 0.1 * (earfcn - 5000)
            earfcn in 5180..5279 -> 1930.0 + 0.1 * (earfcn - 5180)
            earfcn in 5280..5379 -> 875.0 + 0.1 * (earfcn - 5280)
            earfcn in 5730..5849 -> 734.0 + 0.1 * (earfcn - 5730)
            else -> 0.0
        }
    }

    // Helper method to calculate GSM frequency from ARFCN
    private fun calculateGsmFrequency(arfcn: Int): Double {
        return when {
            arfcn in 0..124 -> 890.0 + 0.2 * arfcn
            arfcn in 128..251 -> 1710.2 + 0.2 * (arfcn - 128)
            arfcn in 512..885 -> 1805.2 + 0.2 * (arfcn - 512)
            arfcn in 975..1023 -> 890.0 + 0.2 * (arfcn - 1024 + 0)
            else -> 0.0
        }
    }

    // Helper method to calculate WCDMA frequency from UARFCN
    private fun calculateWcdmaFrequency(uarfcn: Int): Double {
        return when {
            uarfcn in 10562..10838 -> 2110.0 + 0.2 * (uarfcn - 10562)  // Band I
            uarfcn in 9662..9938 -> 1930.0 + 0.2 * (uarfcn - 9662)     // Band II
            uarfcn in 1162..1513 -> 1805.0 + 0.2 * (uarfcn - 1162)     // Band III
            uarfcn in 4357..4458 -> 869.0 + 0.2 * (uarfcn - 4357)      // Band V
            uarfcn in 4387..4413 -> 875.0 + 0.2 * (uarfcn - 4387)      // Band VI
            uarfcn in 2937..3088 -> 2620.0 + 0.2 * (uarfcn - 2937)     // Band VII
            uarfcn in 2712..2863 -> 925.0 + 0.2 * (uarfcn - 2712)      // Band VIII
            uarfcn in 3487..3687 -> 1844.9 + 0.2 * (uarfcn - 3487)     // Band IX
            uarfcn in 3112..3388 -> 2110.0 + 0.2 * (uarfcn - 3112)     // Band X
            uarfcn in 3712..3787 -> 1475.9 + 0.2 * (uarfcn - 3712)     // Band XI
            uarfcn in 3842..3903 -> 729.0 + 0.2 * (uarfcn - 3842)      // Band XII
            uarfcn in 4017..4043 -> 746.0 + 0.2 * (uarfcn - 4017)      // Band XIII
            uarfcn in 4117..4143 -> 758.0 + 0.2 * (uarfcn - 4117)      // Band XIV
            uarfcn in 712..763 -> 875.0 + 0.2 * (uarfcn - 712)         // Band XIX
            uarfcn in 4512..4638 -> 791.0 + 0.2 * (uarfcn - 4512)      // Band XX
            uarfcn in 862..912 -> 1495.9 + 0.2 * (uarfcn - 862)        // Band XXI
            uarfcn in 4662..4938 -> 3510.0 + 0.2 * (uarfcn - 4662)     // Band XXII
            else -> {
                // Generic calculation based on WCDMA standard, may not be accurate
                // for all bands but provides a reasonable estimate
                if (uarfcn > 10000) {
                    // Typical downlink frequency calculation
                    2100.0 + 0.2 * (uarfcn - 10000)
                } else {
                    // Typical uplink frequency calculation
                    1900.0 + 0.2 * uarfcn
                }
            }
        }
    }

    // Helper method to calculate NR (5G) frequency from NRARFCN
    private fun calculateNrFrequency(nrarfcn: Int): Double {
        return when {
            nrarfcn in 123400..130400 -> 850.0
            nrarfcn in 143400..145600 -> 900.0
            nrarfcn in 151600..160600 -> 1800.0
            nrarfcn in 499200..537999 -> 3500.0
            nrarfcn in 620000..680000 -> 4500.0
            nrarfcn in 693334..733333 -> 26000.0
            nrarfcn in 733334..748333 -> 28000.0
            else -> 5000.0 // Default frequency for 5G
        }
    }

    // Helper method to determine LTE band number from EARFCN
    private fun getLteBand(earfcn: Int): Int {
        return when {
            earfcn in 0..599 -> 1
            earfcn in 600..1199 -> 2
            earfcn in 1200..1949 -> 3
            earfcn in 1950..2399 -> 4
            earfcn in 2400..2649 -> 5
            earfcn in 2650..2749 -> 6
            earfcn in 2750..3449 -> 7
            earfcn in 3450..3799 -> 8
            earfcn in 3800..4149 -> 9
            earfcn in 4150..4749 -> 10
            earfcn in 4750..4999 -> 11
            earfcn in 5000..5179 -> 12
            earfcn in 5180..5279 -> 13
            earfcn in 5280..5379 -> 14
            earfcn in 5730..5849 -> 17
            earfcn in 5850..5999 -> 18
            earfcn in 6000..6149 -> 19
            earfcn in 6150..6449 -> 20
            earfcn in 6450..6599 -> 21
            earfcn in 6600..7399 -> 22
            earfcn in 7500..7699 -> 23
            earfcn in 7700..8039 -> 24
            earfcn in 8040..8689 -> 25
            earfcn in 8690..9039 -> 26
            else -> 0
        }
    }

    // Helper method to determine GSM band from ARFCN
    private fun getGsmBand(arfcn: Int): String {
        return when {
            arfcn in 0..124 -> "GSM 900"
            arfcn in 128..251 -> "GSM 850"
            arfcn in 512..885 -> "GSM 1800"
            arfcn in 975..1023 -> "GSM 900"
            else -> "Unknown"
        }
    }

    // Helper method to determine WCDMA band from UARFCN
    private fun getWcdmaBand(uarfcn: Int): Int {
        return when {
            uarfcn in 10562..10838 -> 1  // Band I (2100)
            uarfcn in 9662..9938 -> 2     // Band II (1900)
            uarfcn in 1162..1513 -> 3     // Band III (1800)
            uarfcn in 4357..4458 -> 5     // Band V (850)
            uarfcn in 4387..4413 -> 6     // Band VI (800)
            uarfcn in 2937..3088 -> 7     // Band VII (2600)
            uarfcn in 2712..2863 -> 8     // Band VIII (900)
            uarfcn in 3487..3687 -> 9     // Band IX (1800)
            uarfcn in 3112..3388 -> 10    // Band X (2100/1700)
            uarfcn in 3712..3787 -> 11    // Band XI (1500)
            uarfcn in 3842..3903 -> 12    // Band XII (700)
            uarfcn in 4017..4043 -> 13    // Band XIII (700)
            uarfcn in 4117..4143 -> 14    // Band XIV (700)
            uarfcn in 712..763 -> 19      // Band XIX (850)
            uarfcn in 4512..4638 -> 20    // Band XX (800)
            uarfcn in 862..912 -> 21      // Band XXI (1500)
            uarfcn in 4662..4938 -> 22    // Band XXII (3500)
            else -> -1                     // Unknown band
        }
    }

    // Get signal metrics (SINR and signal power)
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getSignalMetrics(context: Context): Pair<Double, Double> {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cellInfoList = telephonyManager.allCellInfo

            if (!cellInfoList.isNullOrEmpty()) {
                val signalStrength: Double
                val sinr: Double

                when (val cellInfo = cellInfoList[0]) {
                    is CellInfoLte -> {
                        // LTE metrics
                        signalStrength = cellInfo.cellSignalStrength.dbm.toDouble()
                        sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            cellInfo.cellSignalStrength.rssnr.toDouble()
                        } else {
                            0.0
                        }
                    }
                    is CellInfoGsm -> {
                        // GSM metrics
                        signalStrength = cellInfo.cellSignalStrength.dbm.toDouble()
                        sinr = 0.0  // GSM doesn't have SINR
                    }
                    is CellInfoWcdma -> {
                        // WCDMA metrics
                        signalStrength = cellInfo.cellSignalStrength.dbm.toDouble()
                        sinr = 0.0  // Simplified
                    }
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                            // 5G metrics
                            signalStrength = cellInfo.cellSignalStrength.dbm.toDouble()
                            sinr = 0.0  // Simplified
                        } else {
                            signalStrength = 0.0
                            sinr = 0.0
                        }
                    }
                }
                return Pair(sinr, signalStrength)
            }
        } catch (e: Exception) {
            Log.e("DeviceInfo", "Error getting signal metrics", e)
        }
        return Pair(0.0, 0.0)
    }

    // Format timestamp for API submission
    fun getFormattedTimestampForApi(): String {
        val now = Date()
        val formatter = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US)
        return formatter.format(now)
    }
}


/*package utils

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceInfoUtil {

    // Secure device ID
    fun getDeviceId(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("DeviceInfoUtil", "Device ID: $id")
        return id
    }

    // MAC Address (will be dummy on Android 10+)
    fun getMacAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val mac = nif.hardwareAddress
                if (mac != null) {
                    return mac.joinToString(":") { String.format("%02X", it) }
                }
            }
            "02:00:00:00:00:00"
        } catch (e: Exception) {
            "02:00:00:00:00:00"
        }
    }

    // IPv4 Address (e.g., 192.168.x.x)
    fun getIPAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val addresses = nif.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getCellId(context: Context): Int {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cellInfoList = telephonyManager.allCellInfo
            val cellInfo: CellInfo? = cellInfoList?.firstOrNull()
    Log.d("DeviceInfoUtil", "Cell info list size: ${cellInfoList?.size ?: 0}")

            return when (cellInfo) {
                is CellInfoLte -> cellInfo.cellIdentity.ci
                is CellInfoGsm -> cellInfo.cellIdentity.cid
                is CellInfoWcdma -> cellInfo.cellIdentity.cid
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                        val nrCell = cellInfo.cellIdentity as? CellIdentityNr
                        return nrCell?.nci?.toInt() ?: -1
                    } else {
                        return -1
                    }
                }
            }
        } catch (e: Exception) {
            return -1 // fallback if permission denied or error occurs
        }
    }

    // Get operator name
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getOperatorName(context: Context): String {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val operatorName = telephonyManager.networkOperatorName
            return if (operatorName.isNotEmpty()) operatorName else "Unknown"
        } catch (e: Exception) {
            return "Unknown"
        }
    }

    // Get network type
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE])
    fun getNetworkType(context: Context): String {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            return getNetworkTypeString(telephonyManager.dataNetworkType)
        } catch (e: Exception) {
            return "Unknown"
        }
    }

    // Helper method to convert network type int to string
    private fun getNetworkTypeString(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA -> "2G"

            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G"

            TelephonyManager.NETWORK_TYPE_LTE -> "4G"

            TelephonyManager.NETWORK_TYPE_NR -> "5G"

            else -> "Unknown"
        }
    }

    // Get frequency band based on actual cell information
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getFrequencyBand(context: Context): String {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cellInfoList = telephonyManager.allCellInfo

            if (!cellInfoList.isNullOrEmpty()) {
                val cellInfo = cellInfoList[0]

                return when {
                    cellInfo is CellInfoLte -> {
                        val lteCell = cellInfo.cellIdentity as CellIdentityLte
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val earfcn = lteCell.earfcn
                            val band = getLteBandFromEarfcn(earfcn)
                            "$band MHz"
                        } else {
                            "LTE Band"
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr -> {
                        val nrCell = cellInfo.cellIdentity as CellIdentityNr
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val nrarfcn = nrCell.nrarfcn
                            val band = getNrBandFromNrarfcn(nrarfcn)
                            "$band MHz"
                        } else {
                            "5G Band"
                        }
                    }
                    cellInfo is CellInfoGsm -> "900/1800 MHz"
                    cellInfo is CellInfoWcdma -> "2100 MHz"
                    else -> "Unknown"
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceInfoUtil", "Error getting frequency band", e)
        }
        return "Unknown"
    }

    // Helper method to determine LTE band from EARFCN
    private fun getLteBandFromEarfcn(earfcn: Int): String {
        return when {
            earfcn in 0..599 -> "800"
            earfcn in 600..1199 -> "900"
            earfcn in 1200..1949 -> "1800"
            earfcn in 1950..2399 -> "1900"
            earfcn in 2400..2649 -> "2100"
            earfcn in 2650..2749 -> "850"
            earfcn in 2750..3449 -> "2600"
            earfcn in 3450..3799 -> "900"
            earfcn in 3800..4149 -> "1800"
            earfcn in 4150..4749 -> "700"
            earfcn in 4750..4999 -> "700"
            earfcn in 5000..5179 -> "850"
            earfcn in 5180..5279 -> "1900"
            earfcn in 5280..5379 -> "850"
            earfcn in 5730..5849 -> "2600"
            else -> "LTE"
        }
    }

    // Helper method to determine NR band from NRARFCN
    private fun getNrBandFromNrarfcn(nrarfcn: Int): String {
        return when {
            nrarfcn in 123400..130400 -> "850"
            nrarfcn in 143400..145600 -> "900"
            nrarfcn in 151600..160600 -> "1800"
            nrarfcn in 499200..537999 -> "3500"
            nrarfcn in 620000..680000 -> "4500"
            nrarfcn in 693334..733333 -> "26000"
            nrarfcn in 733334..748333 -> "28000"
            else -> "5G"
        }
    }

    // Get current timestamp
    fun getCurrentTimestamp(): String {
        val now = System.currentTimeMillis()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(now)) // Format the current time
    }

    // Get signal metrics (SINR and signal power)
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun getSignalMetrics(context: Context): Pair<Double, Double> {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cellInfoList = telephonyManager.allCellInfo

            if (!cellInfoList.isNullOrEmpty()) {
                val signalStrength: Double
                val sinr: Double

                when (val cellInfo = cellInfoList[0]) {
                    is CellInfoLte -> {
                        // LTE metrics
                        signalStrength = cellInfo.cellSignalStrength.dbm.toDouble()
                        sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            cellInfo.cellSignalStrength.rssnr.toDouble()
                        } else {
                            0.0
                        }
                    }
                    is CellInfoGsm -> {
                        // GSM metrics
                        signalStrength = cellInfo.cellSignalStrength.dbm.toDouble()
                        sinr = 0.0  // GSM doesn't have SINR
                    }
                    is CellInfoWcdma -> {
                        // WCDMA metrics
                        signalStrength = cellInfo.cellSignalStrength.dbm.toDouble()
                        sinr = 0.0  // Simplified
                    }
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                            // 5G metrics
                            signalStrength = cellInfo.cellSignalStrength.dbm.toDouble()
                            sinr = 0.0  // Simplified
                        } else {
                            signalStrength = 0.0
                            sinr = 0.0
                        }
                    }
                }
                return Pair(sinr, signalStrength)
            }
        } catch (e: Exception) {
            Log.e("DeviceInfo", "Error getting signal metrics", e)
        }
        return Pair(0.0, 0.0)
    }

    // Format timestamp for API submission
    fun getFormattedTimestampForApi(): String {
        val now = Date()
        val formatter = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US)
        return formatter.format(now)
    }
}

 */