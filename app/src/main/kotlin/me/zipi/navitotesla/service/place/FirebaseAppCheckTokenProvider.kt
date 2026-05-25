package me.zipi.navitotesla.service.place

import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.android.libraries.places.api.auth.PlacesAppCheckTokenProvider
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.appcheck.FirebaseAppCheck

class FirebaseAppCheckTokenProvider : PlacesAppCheckTokenProvider {
    override fun fetchAppCheckToken(): ListenableFuture<String> =
        CallbackToFutureAdapter.getFuture { completer ->
            FirebaseAppCheck
                .getInstance()
                .getAppCheckToken(false)
                .addOnSuccessListener { completer.set(it.token) }
                .addOnFailureListener { completer.setException(it) }
            "FirebaseAppCheck"
        }
}
