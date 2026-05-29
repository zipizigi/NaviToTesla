package me.zipi.navitotesla.ui.favorite

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zipi.navitotesla.R
import me.zipi.navitotesla.databinding.FavoriteDialogFragmentBinding
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.service.poifinder.KakaoPoiFinder
import me.zipi.navitotesla.util.AnalysisUtil
import java.util.Date

class FavoriteDialogFragment :
    DialogFragment,
    AdapterView.OnItemSelectedListener,
    View.OnClickListener,
    RadioGroup.OnCheckedChangeListener {
    private lateinit var poiArrayAdapter: PoiArrayAdapter
    private var dest: String? = null
    private var prefillPoi: Poi? = null

    var onDismissListener: Runnable? = null
    private lateinit var favoriteDialogViewModel: FavoriteDialogViewModel
    private lateinit var binding: FavoriteDialogFragmentBinding

    constructor() : super()
    constructor(dest: String?) : super() {
        this.dest = dest
    }
    constructor(prefill: Poi) : super() {
        this.prefillPoi = prefill
        this.dest = prefill.poiName
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        favoriteDialogViewModel = ViewModelProvider(this)[FavoriteDialogViewModel::class.java]
        binding = FavoriteDialogFragmentBinding.inflate(inflater, container, false)
        poiArrayAdapter = PoiArrayAdapter(context, android.R.layout.simple_spinner_item)
        poiArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val spinner = binding.addressSelector
        spinner.adapter = poiArrayAdapter
        spinner.onItemSelectedListener = this
        binding.btnDestSearch.setOnClickListener(this)
        binding.btnFavoriteDismiss.setOnClickListener(this)
        binding.btnFavoriteSave.setOnClickListener(this)
        binding.radioGroup.setOnCheckedChangeListener(this)
        favoriteDialogViewModel.poiList.observe(viewLifecycleOwner) { updateSpinner() }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val metrics = resources.displayMetrics
            val isLandscape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthFraction = if (isLandscape) 0.7f else 0.92f
            val width = (metrics.widthPixels * widthFraction).toInt()
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.CENTER)
        }
    }

    override fun onResume() {
        super.onResume()
        val prefill = prefillPoi
        if (prefill != null) {
            // recent → + 진입 — DB 의 PoiAddressEntity 가 가진 도로명/지번/GPS 를 그대로 사용.
            // selectedPoi 를 미리 채워서 라디오 변경 시 onCheckedChanged 가 해당 값을 textfield 에 prefill.
            binding.txtDest.setText(prefill.poiName)
            binding.txtAddress.setText(prefill.getRoadAddress())
            favoriteDialogViewModel.selectedPoi.postValue(prefill)
        } else if (dest != null) {
            binding.txtDest.setText(dest)
            searchDest()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dest = null
        prefillPoi = null
    }

    override fun onClick(v: View) {
        when (v.id) {
            binding.btnDestSearch.id -> {
                searchDest()
            }

            binding.btnFavoriteDismiss.id -> {
                dismiss()
            }

            binding.btnFavoriteSave.id -> {
                saveFavorite()
            }
        }
    }

    override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View,
        position: Int,
        id: Long,
    ) {
        val poi = favoriteDialogViewModel.poiList.value?.get(position) ?: return
        favoriteDialogViewModel.selectedPoi.postValue(poi)
        val checkedId =
            when {
                binding.radioRoadAddress.isChecked -> binding.radioRoadAddress.id
                binding.radioAddress.isChecked -> binding.radioAddress.id
                else -> binding.radioGps.id
            }
        valueForRadio(poi, checkedId)?.let { binding.txtAddress.setText(it) }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    private fun saveFavorite() {
        val poiName = binding.txtDest.text.toString().trim()
        if (poiName.isEmpty()) {
            AnalysisUtil.makeToast(
                context = context,
                text = getString(R.string.emptyPoiName),
                level = AnalysisUtil.ToastLevel.WARN,
            )
            return
        }
        val address = binding.txtAddress.text.toString().trim()
        if (address.isEmpty() || address == "null,null") {
            AnalysisUtil.makeToast(
                context = context,
                text = getString(R.string.addressNotFound),
                level = AnalysisUtil.ToastLevel.WARN,
            )
            return
        }
        // 즐겨찾기는 사용자가 마지막으로 본 textfield 값 (도로명/지번/gps 어느 모드든) 을 그대로
        // roadAddress 컬럼에 저장하고, share 시 그 값을 그대로 사용한다 — 라디오/selected 분기 없음.
        val entity =
            PoiAddressEntity(
                poi = poiName,
                packageName = "",
                roadAddress = address,
                registered = true,
                created = Date(),
            )
        viewLifecycleOwner.lifecycleScope.launch {
            val dao = AppDatabase.getInstance().poiAddressDao()
            val existing = dao.findRegisteredByPoi(entity.poi)
            if (existing != null) {
                AnalysisUtil.makeToast(
                    context = context,
                    text = getString(R.string.duplicatedPoiName),
                    level = AnalysisUtil.ToastLevel.WARN,
                )
                return@launch
            }
            dao.insertPoi(entity)
            withContext(Dispatchers.Main) {
                dismiss()
            }
            onDismissListener?.run()
        }
    }

    private fun searchDest() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pois =
                    KakaoPoiFinder().listPoiAddress(
                        binding.txtDest.text.toString().trim(),
                    )
                favoriteDialogViewModel.poiList.postValue(pois)
            } catch (e: Exception) {
                AnalysisUtil.recordException(e)
            }
        }
    }

    private fun updateSpinner() {
        poiArrayAdapter.clear()
        poiArrayAdapter.addAll(favoriteDialogViewModel.poiList.value ?: listOf())
        poiArrayAdapter.notifyDataSetChanged()
    }

    override fun onCheckedChanged(
        group: RadioGroup,
        checkedId: Int,
    ) {
        val poi = favoriteDialogViewModel.selectedPoi.value ?: return
        valueForRadio(poi, group.checkedRadioButtonId)?.let { binding.txtAddress.setText(it) }
    }

    private fun valueForRadio(
        poi: Poi,
        radioId: Int,
    ): String? {
        val raw =
            when (radioId) {
                binding.radioRoadAddress.id -> poi.getRoadAddress()
                binding.radioAddress.id -> poi.getAddress()
                binding.radioGps.id -> poi.getGpsAddress()
                else -> return null
            }
        if (raw.isEmpty() || raw == "null,null") return null
        return raw
    }

    class PoiArrayAdapter(
        context: Context?,
        @LayoutRes resource: Int,
    ) : ArrayAdapter<Poi?>(
            requireNotNull(context),
            resource,
        ) {
        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val view = super.getView(position, convertView, parent) as TextView
            view.isSingleLine = false
            val poi = getItem(position)
            var shortAddress = poi!!.getRoadAddress()
            val addressSplit = shortAddress.split(" ")
            if (addressSplit.size > 3) {
                shortAddress = addressSplit[0] + " " + addressSplit[1] + " " + addressSplit[2]
            }
            val text: String = poi.poiName + "\n" + shortAddress
            view.text = text
            view.textSize = 12f
            return view
        }

        override fun getDropDownView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val view = super.getDropDownView(position, convertView, parent) as TextView
            view.isSingleLine = false
            val poi = getItem(position)
            val text: String = poi!!.poiName + "\n" + poi.getRoadAddress()
            view.text = text
            view.textSize = 12f
            return view
        }
    }
}
