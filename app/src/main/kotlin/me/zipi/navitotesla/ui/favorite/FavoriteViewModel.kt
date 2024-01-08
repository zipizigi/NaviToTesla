package me.zipi.navitotesla.ui.favorite

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.zipi.navitotesla.db.PoiAddressEntity

class FavoriteViewModel : ViewModel() {
    val recentPoiAddress = MutableLiveData<MutableList<PoiAddressEntity>>()

    val registeredPoiAddress = MutableLiveData<MutableList<PoiAddressEntity>>()
}
