package me.zipi.navitotesla.ui.favorite;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import lombok.Getter;
import me.zipi.navitotesla.model.Poi;

public class FavoriteDialogViewModel extends ViewModel {

    @Getter
    private final MutableLiveData<List<Poi>> poiList = new MutableLiveData<>(new ArrayList<>());

    @Getter
    private final MutableLiveData<Poi> selectedPoi = new MutableLiveData<>();
}