package me.zipi.navitotesla.ui.setting

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.zipi.navitotesla.util.EnablerUtil

class SettingViewModel : ViewModel() {
    val isAppEnabled = MutableLiveData(true)

    val isConditionEnabled = MutableLiveData(false)

    val bluetoothConditions = MutableLiveData<List<String>>(emptyList())

    /**
     * 저장소의 현재 상태를 읽어 UI 상태(LiveData)에 반영한다.
     * 로드 경로는 절대 저장소에 다시 쓰지 않는다 — 그래야 화면 재진입이 사용자 설정을 덮어쓰지 않는다.
     */
    fun loadStates() {
        viewModelScope.launch {
            isAppEnabled.value = EnablerUtil.getAppEnabled()
            isConditionEnabled.value = EnablerUtil.getConditionEnabled()
            bluetoothConditions.value = EnablerUtil.listBluetoothCondition()
        }
    }

    fun reloadBluetoothConditions() {
        viewModelScope.launch {
            bluetoothConditions.value = EnablerUtil.listBluetoothCondition()
        }
    }

    /** 사용자가 실제로 토글을 바꿨을 때만 호출한다. 값이 바뀔 때만 저장한다. */
    fun setAppEnabledByUser(enabled: Boolean) {
        if (isAppEnabled.value == enabled) return
        isAppEnabled.value = enabled
        viewModelScope.launch { EnablerUtil.setAppEnabled(enabled) }
    }

    /** 사용자가 실제로 토글을 바꿨을 때만 호출한다. 값이 바뀔 때만 저장한다. */
    fun setConditionEnabledByUser(enabled: Boolean) {
        if (isConditionEnabled.value == enabled) return
        isConditionEnabled.value = enabled
        viewModelScope.launch { EnablerUtil.setConditionEnabled(enabled) }
    }

    fun clearObserve(owner: LifecycleOwner?) {
        isAppEnabled.removeObservers(owner!!)
        isConditionEnabled.removeObservers(owner)
        bluetoothConditions.removeObservers(owner)
    }
}
