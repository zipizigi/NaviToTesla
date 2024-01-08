package me.zipi.navitotesla.ui.favorite

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.zipi.navitotesla.model.Poi

class FavoriteDialogViewModel : ViewModel() {

    val poiList = MutableLiveData<List<Poi>>(ArrayList())

    val selectedPoi = MutableLiveData<Poi>()
}
