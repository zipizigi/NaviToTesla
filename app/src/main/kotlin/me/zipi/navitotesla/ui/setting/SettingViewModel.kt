package me.zipi.navitotesla.ui.setting

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingViewModel : ViewModel() {
    val isAppEnabled = MutableLiveData(true)

    val isConditionEnabled = MutableLiveData(false)

    val bluetoothConditions = MutableLiveData<MutableList<String>>(mutableListOf())
    fun clearObserve(owner: LifecycleOwner?) {
        isAppEnabled.removeObservers(owner!!)
        isConditionEnabled.removeObservers(owner)
        bluetoothConditions.removeObservers(owner)
    }
}
