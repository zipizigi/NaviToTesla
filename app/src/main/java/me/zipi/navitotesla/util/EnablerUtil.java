package me.zipi.navitotesla.util;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.core.app.ActivityCompat;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.zipi.navitotesla.db.AppDatabase;
import me.zipi.navitotesla.db.ConditionEntity;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EnablerUtil {

    private static final Set<String> connectedBluetoothDevice = new HashSet<>();

    public static void addConnectedBluetooth(String name) {
        if (name == null) {
            AnalysisUtil.log("add bluetooth name error. device name is null");
            return;
        }
        connectedBluetoothDevice.add(name.toLowerCase(Locale.ROOT));
    }

    public static void removeConnectedBluetooth(String name) {
        if (name == null) {
            AnalysisUtil.log("remove bluetooth name error. device name is null");
            return;
        }
        connectedBluetoothDevice.remove(name.toLowerCase(Locale.ROOT));
    }

    public static void addWifiCondition(Context context, String ssid) {
        addCondition(context, "wifi", ssid);
    }

    public static void addBluetoothCondition(Context context, String name) {
        addCondition(context, "bluetooth", name);
    }

    public static void removeBluetoothCondition(Context context, String name) {
        removeCondition(context, "bluetooth", name);
    }

    private static void addCondition(Context context, String type, String name) {
        AppDatabase.getInstance(context).conditionDao().
                insertCondition(ConditionEntity.builder()
                        .name(name)
                        .type(type)
                        .created(new Date())
                        .build());
    }

    private static void removeCondition(Context context, String type, String name) {
        ConditionEntity entity = AppDatabase.getInstance(context).conditionDao().findConditionByNameSync(type, name);
        if (entity != null) {
            AppDatabase.getInstance(context).conditionDao().delete(entity);
        }
    }

    public static List<String> listWifiCondition(Context context) {
        List<String> result = new ArrayList<>();
        for (ConditionEntity entity : AppDatabase.getInstance(context).conditionDao().findConditionSync("wifi")) {
            result.add(entity.getName());
        }
        return result;
    }

    public static List<String> listBluetoothCondition(Context context) {
        List<String> result = new ArrayList<>();
        for (ConditionEntity entity : AppDatabase.getInstance(context).conditionDao().findConditionSync("bluetooth")) {
            result.add(entity.getName());
        }
        return result;
    }

    public static void setAppEnabled(Context context, Boolean enabled) {
        if (PreferencesUtil.getBoolean(context, "appEnabled", true) != enabled) {
            PreferencesUtil.put(context, "appEnabled", enabled);
        }
    }

    public static void setConditionEnabled(Context context, Boolean enabled) {
        if (PreferencesUtil.getBoolean(context, "appCondition", false) != enabled) {
            PreferencesUtil.put(context, "appCondition", enabled);
        }
    }

    public static Boolean getAppEnabled(Context context) {
        return PreferencesUtil.getBoolean(context, "appEnabled", true);
    }

    public static Boolean getConditionEnabled(Context context) {
        return PreferencesUtil.getBoolean(context, "appCondition", false);
    }

    public static boolean isSendingCheck(Context context) {
        if (!PreferencesUtil.getBoolean(context, "appEnabled", true)) {
            return false;
        }
        if (!PreferencesUtil.getBoolean(context, "appCondition", false)) {
            return true;
        }
        List<String> wifiCondition = listWifiCondition(context);
        List<String> bluetoothCondition = listBluetoothCondition(context);
        if (wifiCondition.size() == 0 && bluetoothCondition.size() == 0) {
            return true;
        }

        if (bluetoothCondition.size() > 0) {
            for (String condition : bluetoothCondition) {
                if (connectedBluetoothDevice.contains(condition.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            AnalysisUtil.log("Bluetooth not connected");
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
        AnalysisUtil.log("Condition not match, not sending");
        return false;

    }

    public static List<String> getPairedBluetooth(Context context) {
        final String permission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ? Manifest.permission.BLUETOOTH_CONNECT : Manifest.permission.BLUETOOTH;

        if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return new ArrayList<>();
        }

        BluetoothManager bluetoothManager = context.getApplicationContext().getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        if (adapter == null) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            result.add(device.getName());
        }
        return result;
    }

}
