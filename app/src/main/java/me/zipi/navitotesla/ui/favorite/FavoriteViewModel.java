package me.zipi.navitotesla.ui.favorite;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import lombok.Getter;
import me.zipi.navitotesla.db.PoiAddressEntity;

public class FavoriteViewModel extends ViewModel {

    @Getter
    private final MutableLiveData<List<PoiAddressEntity>> recentPoiAddress = new MutableLiveData<>(new ArrayList<>());
    @Getter
    private final MutableLiveData<List<PoiAddressEntity>> registeredPoiAddress = new MutableLiveData<>(new ArrayList<>());


}