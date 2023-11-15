package me.zipi.navitotesla.ui.favorite

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.zipi.navitotesla.db.PoiAddressEntity

class FavoriteViewModel : ViewModel() {
    val recentPoiAddress = MutableLiveData<List<PoiAddressEntity>>(ArrayList())

    val registeredPoiAddress = MutableLiveData<List<PoiAddressEntity>>(ArrayList())
}