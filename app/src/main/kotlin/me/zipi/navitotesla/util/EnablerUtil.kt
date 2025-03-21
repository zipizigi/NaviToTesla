package me.zipi.navitotesla.util

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    fun addBluetoothCondition(name: String) =
        CoroutineScope(Dispatchers.IO).launch {
            addCondition("bluetooth", name)
        }

    fun removeBluetoothCondition(name: String) =
        CoroutineScope(Dispatchers.IO).launch {
            removeCondition("bluetooth", name)
        }

    private suspend fun addCondition(
        type: String,
        name: String,
    ) {
        AppDatabase.getInstance().conditionDao().insertCondition(
            ConditionEntity(name = name, type = type, created = Date()),
        )
    }

    private suspend fun removeCondition(
        type: String,
        name: String,
    ) {
        val entity: ConditionEntity? = AppDatabase.getInstance().conditionDao().findConditionByName(type, name)
        if (entity != null) {
            AppDatabase.getInstance().conditionDao().delete(entity)
        }
    }

    suspend fun listWifiCondition(): List<String> {
        val result = mutableListOf<String>()
        for (entity in AppDatabase.getInstance().conditionDao().findCondition("wifi")) {
            result.add(entity.name)
        }
        return result
    }

    suspend fun listBluetoothCondition(): MutableList<String> {
        val result: MutableList<String> = mutableListOf()
        for (entity in AppDatabase.getInstance().conditionDao().findCondition("bluetooth")) {
            result.add(entity.name)
        }
        return result
    }

    suspend fun setAppEnabled(enabled: Boolean) {
        if (PreferencesUtil.getBoolean("appEnabled", true) != enabled) {
            PreferencesUtil.put("appEnabled", enabled)
        }
    }

    suspend fun setConditionEnabled(enabled: Boolean) {
        if (PreferencesUtil.getBoolean("appCondition", false) != enabled) {
            PreferencesUtil.put("appCondition", enabled)
        }
    }

    suspend fun getAppEnabled(): Boolean = PreferencesUtil.getBoolean("appEnabled", true)

    suspend fun getConditionEnabled(): Boolean = PreferencesUtil.getBoolean("appCondition", false)

    suspend fun isSendingCheck(): Boolean {
        if (!PreferencesUtil.getBoolean("appEnabled", true)) {
            return false
        }
        if (!PreferencesUtil.getBoolean("appCondition", false)) {
            return true
        }
        val wifiCondition = listWifiCondition()
        val bluetoothCondition = listBluetoothCondition()
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
                WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                   .getSystemService(Context.WIFI_SERVICE);
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
                permission,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return ArrayList()
        }
        val bluetoothManager =
            context.applicationContext.getSystemService(
                BluetoothManager::class.java,
            )
        val adapter = bluetoothManager.adapter ?: return ArrayList()
        val result: MutableList<String> = ArrayList()
        for (device in adapter.bondedDevices) {
            result.add(device.name)
        }
        return result
    }
}
