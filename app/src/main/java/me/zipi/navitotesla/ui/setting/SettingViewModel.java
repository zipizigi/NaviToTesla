package me.zipi.navitotesla.ui.setting;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import lombok.Getter;

public class SettingViewModel extends ViewModel {
    @Getter
    private final MutableLiveData<Boolean> isAppEnabled = new MutableLiveData<>(true);
    @Getter
    private final MutableLiveData<Boolean> isConditionEnabled = new MutableLiveData<>(false);
    @Getter
    private final MutableLiveData<List<String>> bluetoothConditions = new MutableLiveData<>(new ArrayList<>());

    public void clearObserve(LifecycleOwner owner) {
        isAppEnabled.removeObservers(owner);
        isConditionEnabled.removeObservers(owner);
        bluetoothConditions.removeObservers(owner);
    }
}
