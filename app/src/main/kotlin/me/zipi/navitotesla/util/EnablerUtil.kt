package me.zipi.navitotesla.util

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.ConditionEntity
import java.util.Date

object EnablerUtil {
    private val connectedBluetoothDevice: MutableSet<String> = HashSet()
    fun addConnectedBluetooth(name: String?) {
        if (name == null) {
            AnalysisUtil.log("add bluetooth name error. device name is null")
            return
        }
        connectedBluetoothDevice.add(name.lowercase())
    }

    fun removeConnectedBluetooth(name: String?) {
        if (name == null) {
            AnalysisUtil.log("remove bluetooth name error. device name is null")
            return
        }
        connectedBluetoothDevice.remove(name.lowercase())
    }

    @Deprecated("not used")
    fun addWifiCondition(context: Context, ssid: String) {
        addCondition(context, "wifi", ssid)
    }

    fun addBluetoothCondition(context: Context, name: String) {
        addCondition(context, "bluetooth", name)
    }

    fun removeBluetoothCondition(context: Context, name: String) {
        removeCondition(context, "bluetooth", name)
    }

    private fun addCondition(context: Context, type: String, name: String) {
        AppDatabase.getInstance(context).conditionDao().insertCondition(
            ConditionEntity(name = name, type = type, created = Date())
        )
    }

    private fun removeCondition(context: Context, type: String, name: String) {
        val entity: ConditionEntity? = AppDatabase.getInstance(context).conditionDao()
            .findConditionByNameSync(type, name)
        if (entity != null) {
            AppDatabase.getInstance(context).conditionDao().delete(entity)
        }
    }

    fun listWifiCondition(context: Context): List<String> {
        val result = mutableListOf<String>()
        for (entity in AppDatabase.getInstance(context).conditionDao()
            .findConditionSync("wifi")) {
            result.add(entity.name)
        }
        return result
    }

    fun listBluetoothCondition(context: Context): MutableList<String> {
        val result: MutableList<String> = mutableListOf()
        for (entity in AppDatabase.getInstance(context).conditionDao()
            .findConditionSync("bluetooth")) {
            result.add(entity.name)
        }
        return result
    }

    fun setAppEnabled(context: Context, enabled: Boolean) {
        if (PreferencesUtil.getBoolean(context, "appEnabled", true) !== enabled) {
            PreferencesUtil.put(context, "appEnabled", enabled)
        }
    }

    fun setConditionEnabled(context: Context, enabled: Boolean) {
        if (PreferencesUtil.getBoolean(context, "appCondition", false) !== enabled) {
            PreferencesUtil.put(context, "appCondition", enabled)
        }
    }

    fun getAppEnabled(context: Context): Boolean {
        return PreferencesUtil.getBoolean(context, "appEnabled", true)
    }

    fun getConditionEnabled(context: Context): Boolean {
        return PreferencesUtil.getBoolean(context, "appCondition", false)
    }

    fun isSendingCheck(context: Context): Boolean {
        if (!PreferencesUtil.getBoolean(context, "appEnabled", true)) {
            return false
        }
        if (!PreferencesUtil.getBoolean(context, "appCondition", false)) {
            return true
        }
        val wifiCondition = listWifiCondition(context)
        val bluetoothCondition = listBluetoothCondition(context)
        if (wifiCondition.isEmpty() && bluetoothCondition.isEmpty()) {
            return true
        }
        if (bluetoothCondition.isNotEmpty()) {
            for (condition in bluetoothCondition) {
                if (connectedBluetoothDevice.contains(condition.lowercase())) {
                    return true
                }
            }
            AnalysisUtil.log("Bluetooth not connected")
        }
        /*
        // Android 11+ Get wifi ssid required background location
        if (wifiCondition.size() > 0) {
            try {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wifiManager.getConnectionInfo();
                AnalysisUtil.log(info.toString());
                String ssid = info.getSSID();
                for (String condition : wifiCondition) {
                    if (condition.equalsIgnoreCase(ssid)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                AnalysisUtil.log("error wifi condition check");
                AnalysisUtil.recordException(e);
                return true;
            }
            AnalysisUtil.log("Wifi not connected");
        }
*/
        AnalysisUtil.log("Condition not match, not sending")
        return false
    }

    fun getPairedBluetooth(context: Context?): List<String> {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        if (ActivityCompat.checkSelfPermission(
                context!!,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return ArrayList()
        }
        val bluetoothManager = context.applicationContext.getSystemService(
            BluetoothManager::class.java
        )
        val adapter = bluetoothManager.adapter ?: return ArrayList()
        val result: MutableList<String> = ArrayList()
        for (device in adapter.bondedDevices) {
            result.add(device.name)
        }
        return result
    }
}