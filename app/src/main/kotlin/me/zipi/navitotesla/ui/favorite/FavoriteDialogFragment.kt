package me.zipi.navitotesla.ui.favorite

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import me.zipi.navitotesla.AppExecutors
import me.zipi.navitotesla.databinding.FavoriteDialogFragmentBinding
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory
import me.zipi.navitotesla.util.AnalysisUtil
import java.util.Date

class FavoriteDialogFragment : DialogFragment, AdapterView.OnItemSelectedListener,
    View.OnClickListener, RadioGroup.OnCheckedChangeListener {
    var poiArrayAdapter: PoiArrayAdapter? = null
    private var dest: String? = null

    var onDismissListener: Runnable? = null
    private var favoriteDialogViewModel: FavoriteDialogViewModel? = null
    private var binding: FavoriteDialogFragmentBinding? = null

    constructor() : super()
    constructor(dest: String?) : super() {
        this.dest = dest
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        favoriteDialogViewModel = ViewModelProvider(this).get(
            FavoriteDialogViewModel::class.java
        )
        binding = FavoriteDialogFragmentBinding.inflate(inflater, container, false)
        poiArrayAdapter = PoiArrayAdapter(context, android.R.layout.simple_spinner_item)
        poiArrayAdapter!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val spinner = binding!!.addressSelector
        spinner.adapter = poiArrayAdapter
        spinner.onItemSelectedListener = this
        binding!!.btnDestSearch.setOnClickListener(this)
        binding!!.btnFavoriteDismiss.setOnClickListener(this)
        binding!!.btnFavoriteSave.setOnClickListener(this)
        binding!!.radioGroup.setOnCheckedChangeListener(this)
        favoriteDialogViewModel!!.poiList.observe(viewLifecycleOwner) { updateSpinner() }
        return binding!!.root
    }

    override fun onResume() {
        super.onResume()
        if (dest != null && binding != null) {
            binding!!.txtDest.setText(dest)
            searchDest()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
        poiArrayAdapter = null
        dest = null
    }

    override fun onClick(v: View) {
        if (binding == null) {
            return
        }
        if (v.id == binding!!.btnDestSearch.id) {
            searchDest()
        } else if (v.id == binding!!.btnFavoriteDismiss.id) {
            dismiss()
        } else if (v.id == binding!!.btnFavoriteSave.id) {
            saveFavorite()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        if (binding == null || favoriteDialogViewModel?.poiList?.value == null) {
            return
        }
        val poi: Poi = favoriteDialogViewModel!!.poiList.value!![position]
        favoriteDialogViewModel!!.selectedPoi.postValue(poi)
        if (binding!!.radioRoadAddress.isChecked) {
            binding!!.txtAddress.setText(poi.getRoadAddress())
        } else if (binding!!.radioAddress.isChecked) {
            binding!!.txtAddress.setText(poi.getAddress())
        } else {
            binding!!.txtAddress.setText(poi.getGpsAddress())
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}
    private fun saveFavorite() {
        if (binding == null) {
            return
        }
        val entity: PoiAddressEntity =

            PoiAddressEntity(
                poi = binding!!.txtDest.text.toString(),
                address = binding!!.txtAddress.text.toString(),
                registered = true,
                created = Date()
            )

        AppExecutors.execute {
            context?.let {
                AppDatabase.getInstance(it).poiAddressDao().insertPoi(entity)
                activity?.let { activity ->
                    activity.runOnUiThread { dismiss() }
                    onDismissListener?.run()
                }
            }

        }
    }

    private fun searchDest() {
        AppExecutors.execute {
            if (binding == null) {
                return@execute
            }
            try {
                val pois = PoiFinderFactory.kakaoPoiFinder.listPoiAddress(
                    binding!!.txtDest.text.toString().trim()
                )
                favoriteDialogViewModel?.poiList?.postValue(pois)
            } catch (e: Exception) {
                AnalysisUtil.recordException(e)
            }
        }
    }

    private fun updateSpinner() {
        if (poiArrayAdapter != null) {
            poiArrayAdapter!!.clear()
            poiArrayAdapter!!.addAll(favoriteDialogViewModel?.poiList?.value ?: listOf())
            poiArrayAdapter!!.notifyDataSetChanged()
        }
    }

    override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
        if (favoriteDialogViewModel?.selectedPoi?.value == null || binding == null) {
            return
        }

        if (binding!!.radioRoadAddress.id == group.checkedRadioButtonId) {
            binding!!.txtAddress.setText(
                favoriteDialogViewModel!!.selectedPoi.value?.getRoadAddress()
            )
        } else if (binding!!.radioAddress.id == group.checkedRadioButtonId) {
            binding!!.txtAddress.setText(
                favoriteDialogViewModel!!.selectedPoi.value?.getAddress()
            )
        } else {
            binding!!.txtAddress.setText(
                favoriteDialogViewModel!!.selectedPoi.value?.getGpsAddress()
            )
        }
    }

    class PoiArrayAdapter(context: Context?, @LayoutRes resource: Int) : ArrayAdapter<Poi?>(
        context!!, resource
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            view.isSingleLine = false
            val poi = getItem(position)
            var shortAddress = poi!!.getRoadAddress()
            val addressSplit = shortAddress!!.split(" ")
            if (addressSplit.size > 3) {
                shortAddress = addressSplit[0] + " " + addressSplit[1] + " " + addressSplit[2]
            }
            val text: String = poi.poiName + "\n" + shortAddress
            view.text = text
            view.textSize = 12f
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent) as TextView
            view.isSingleLine = false
            val poi = getItem(position)
            val text: String = poi!!.poiName + "\n" + poi!!.getRoadAddress()
            view.text = text
            view.textSize = 12f
            return view
        }
    }
}