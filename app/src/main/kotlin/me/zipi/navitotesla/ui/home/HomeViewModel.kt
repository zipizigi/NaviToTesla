package me.zipi.navitotesla.ui.home

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.zipi.navitotesla.model.Token
import me.zipi.navitotesla.model.Vehicle

class HomeViewModel : ViewModel() {
    val vehicleListLiveData = MutableLiveData<List<Vehicle>>(listOf())

    val tokenLiveData = MutableLiveData<Token>()

    val appVersion = MutableLiveData("1.0")

    val isUpdateAvailable = MutableLiveData(false)

    val refreshToken = MutableLiveData("")

    val shareMode = MutableLiveData("api") // api or app

    val isInstalledTeslaApp = MutableLiveData(false)
    fun clearObserve(owner: LifecycleOwner?) {
        vehicleListLiveData.removeObservers(owner!!)
        tokenLiveData.removeObservers(owner)
        appVersion.removeObservers(owner)
        isUpdateAvailable.removeObservers(owner)
        refreshToken.removeObservers(owner)
        shareMode.removeObservers(owner)
        isInstalledTeslaApp.removeObservers(owner)
    }
}