package me.zipi.navitotesla.receiver

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.EnablerUtil

class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        if (ActivityCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.extras!!.getParcelable(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.extras!!.getParcelable(BluetoothDevice.EXTRA_DEVICE)
        }

        AnalysisUtil.log("receive bluetooth broadcast: " + intent.action + " - " + device!!.name)
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> EnablerUtil.addConnectedBluetooth(
                device.name
            )

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> EnablerUtil.removeConnectedBluetooth(device.name)
        }
    }
}