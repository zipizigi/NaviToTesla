package me.zipi.navitotesla.ui.favorite

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import me.zipi.navitotesla.R
import me.zipi.navitotesla.databinding.FragmentFavoriteBinding
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.service.NaviToTeslaService
import me.zipi.navitotesla.ui.favorite.PoiAddressRecyclerAdapter.OnFavoriteButtonClicked
import me.zipi.navitotesla.util.AnalysisUtil

class FavoriteFragment : Fragment(), View.OnClickListener {
    private lateinit var favoriteViewModel: FavoriteViewModel
    private lateinit var binding: FragmentFavoriteBinding
    private lateinit var poiHistoryRecyclerAdapter: PoiAddressRecyclerAdapter
    private lateinit var poiRegisteredRecyclerAdapter: PoiAddressRecyclerAdapter
    private lateinit var naviToTeslaService: NaviToTeslaService
    private lateinit var appDatabase: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        favoriteViewModel = ViewModelProvider(this)[FavoriteViewModel::class.java]
        binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        val root = binding.root
        poiHistoryRecyclerAdapter =
            PoiAddressRecyclerAdapter(
                object : OnFavoriteButtonClicked {
                    override fun onClick(position: Int) {
                        addFavoriteLocation(position)
                    }

                    override fun onShareClick(position: Int) {}
                },
            )
        binding.recylerHistory.adapter = poiHistoryRecyclerAdapter
        binding.recylerHistory.layoutManager = LinearLayoutManager(context)
        poiRegisteredRecyclerAdapter =
            PoiAddressRecyclerAdapter(
                object : OnFavoriteButtonClicked {
                    override fun onClick(position: Int) {
                        removeFavoriteLocation(position)
                    }

                    override fun onShareClick(position: Int) {
                        shareLocation(position)
                    }
                },
            )
        binding.recylerRegistered.adapter = poiRegisteredRecyclerAdapter
        binding.recylerRegistered.layoutManager = LinearLayoutManager(context)
        binding.btnFavoriteAdd.setOnClickListener(this)
        binding.btnFavoriteHelp.setOnClickListener(this)
        favoriteViewModel.recentPoiAddress.observe(
            viewLifecycleOwner,
        ) { items -> poiHistoryRecyclerAdapter.setItems(items) }
        favoriteViewModel.registeredPoiAddress.observe(
            viewLifecycleOwner,
        ) { items -> poiRegisteredRecyclerAdapter.setItems(items) }
        appDatabase = AppDatabase.getInstance()
        naviToTeslaService = NaviToTeslaService(requireActivity())
        return root
    }

    override fun onResume() {
        super.onResume()
        updatePoiAddress()
    }

    private fun updatePoiAddress() {
        viewLifecycleOwner.lifecycleScope.launch {
            favoriteViewModel.registeredPoiAddress.postValue(
                appDatabase.poiAddressDao().findRegisteredPoi().toMutableList(),
            )
            favoriteViewModel.recentPoiAddress.postValue(
                appDatabase.poiAddressDao().findRecentPoi(25).toMutableList(),
            )
        }
    }

    private fun addFavoriteLocation(position: Int) {
        if (favoriteViewModel.recentPoiAddress.value == null) {
            return
        }
        val poi: String = favoriteViewModel.recentPoiAddress.value!![position].poi
        addFavorite(poi)
    }

    private fun removeFavoriteLocation(position: Int) {
        if (activity == null || context == null) {
            return
        }
        AlertDialog.Builder(requireActivity()).setCancelable(true).setTitle(getString(R.string.removeFavorite))
            .setMessage(getString(R.string.confirmRemoveFavorite))
            .setPositiveButton(getString(R.string.delete)) { _: DialogInterface?, _: Int ->
                removeFavorite(
                    position,
                )
            }.setNegativeButton(getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }.show()
    }

    private fun shareLocation(position: Int) {
        if (activity == null || favoriteViewModel.registeredPoiAddress.value == null) {
            return
        }
        val poi: PoiAddressEntity = favoriteViewModel.registeredPoiAddress.value!![position]
        AlertDialog.Builder(requireActivity()).setCancelable(true).setTitle(getString(R.string.sendDestination))
            .setMessage(getString(R.string.confirmSendDestination) + " \n - " + poi.address)
            .setPositiveButton(getString(R.string.send)) { _: DialogInterface?, _: Int ->
                viewLifecycleOwner.lifecycleScope.launch {
                    if (activity == null) {
                        return@launch
                    }
                    try {
                        naviToTeslaService.share(poi.address)
                    } catch (e: Exception) {
                        AnalysisUtil.recordException(e)
                    }
                }
            }.setNegativeButton(getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }.show()
    }

    private fun removeFavorite(position: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (favoriteViewModel.registeredPoiAddress.value == null) {
                return@launch
            }
            val poi: PoiAddressEntity = favoriteViewModel.registeredPoiAddress.value!![position]
            appDatabase.poiAddressDao().delete(poi)
            updatePoiAddress()
        }
    }

    private fun addFavorite(dest: String? = null) {
        if (activity == null) {
            return
        }
        val dialog = FavoriteDialogFragment(dest)
        dialog.onDismissListener = Runnable { updatePoiAddress() }
        dialog.show(childFragmentManager, FavoriteDialogFragment::class.java.name)
    }

    override fun onClick(v: View) {
        if (activity == null || context == null) {
            return
        }
        if (v.id == binding.btnFavoriteHelp.id) {
            AlertDialog.Builder(requireActivity()).setTitle(getString(R.string.guide)).setMessage(getString(R.string.guideFavorite))
                .setCancelable(true).setPositiveButton(getString(R.string.confirm)) { _: DialogInterface?, _: Int -> }.create().show()
        } else if (v.id == binding.btnFavoriteAdd.id) {
            addFavorite()
        }
    }
}
