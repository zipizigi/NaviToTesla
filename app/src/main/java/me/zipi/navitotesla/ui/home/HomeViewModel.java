package me.zipi.navitotesla.ui.home;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import lombok.Getter;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.model.Vehicle;

public class HomeViewModel extends ViewModel {

    @Getter
    MutableLiveData<List<Vehicle>> vehicleListLiveData = new MutableLiveData<>(new ArrayList<>());
    @Getter
    MutableLiveData<Token> tokenLiveData = new MutableLiveData<>();
    @Getter
    MutableLiveData<String> appVersion = new MutableLiveData<>("1.0");
    @Getter
    MutableLiveData<Boolean> isUpdateAvailable = new MutableLiveData<>(false);

    @Getter
    MutableLiveData<String> refreshToken = new MutableLiveData<>("");


    public void clearObserve(LifecycleOwner owner) {
        vehicleListLiveData.removeObservers(owner);
        tokenLiveData.removeObservers(owner);
        appVersion.removeObservers(owner);
        isUpdateAvailable.removeObservers(owner);
        refreshToken.removeObservers(owner);
    }
}