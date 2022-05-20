package me.zipi.navitotesla.receiver;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.EnablerUtil;

public class BluetoothReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final String permission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ? Manifest.permission.BLUETOOTH_CONNECT : Manifest.permission.BLUETOOTH;

        if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        BluetoothDevice device = intent.getExtras().getParcelable(BluetoothDevice.EXTRA_DEVICE);
        AnalysisUtil.log("receive bluetooth broadcast: " + intent.getAction() + " - " + device.getName());
        switch (intent.getAction()) {
            case BluetoothDevice.ACTION_ACL_CONNECTED:
                EnablerUtil.addConnectedBluetooth(device.getName());
                break;
            case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                EnablerUtil.removeConnectedBluetooth(device.getName());
                break;
        }
    }
}
