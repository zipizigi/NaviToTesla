package me.zipi.navitotesla.ui.home;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import lombok.Getter;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.model.Vehicle;

public class HomeViewModel extends ViewModel {

    @Getter
    final MutableLiveData<List<Vehicle>> vehicleListLiveData = new MutableLiveData<>(new ArrayList<>());
    @Getter
    final MutableLiveData<Token> tokenLiveData = new MutableLiveData<>();
    @Getter
    final MutableLiveData<String> appVersion = new MutableLiveData<>("1.0");
    @Getter
    final MutableLiveData<Boolean> isUpdateAvailable = new MutableLiveData<>(false);
    @Getter
    final MutableLiveData<String> refreshToken = new MutableLiveData<>("");

    @Getter
    final MutableLiveData<String> shareMode = new MutableLiveData<>("api"); // api or app
    @Getter
    final MutableLiveData<Boolean> isInstalledTeslaApp = new MutableLiveData<>(false);

    public void clearObserve(LifecycleOwner owner) {
        vehicleListLiveData.removeObservers(owner);
        tokenLiveData.removeObservers(owner);
        appVersion.removeObservers(owner);
        isUpdateAvailable.removeObservers(owner);
        refreshToken.removeObservers(owner);
        shareMode.removeObservers(owner);
        isInstalledTeslaApp.removeObservers(owner);
    }
}