package me.zipi.navitotesla.ui.favorite;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import lombok.Setter;
import me.zipi.navitotesla.databinding.FavoriteDialogFragmentBinding;
import me.zipi.navitotesla.db.AppDatabase;
import me.zipi.navitotesla.db.PoiAddressEntity;
import me.zipi.navitotesla.model.Poi;
import me.zipi.navitotesla.service.KakaoPoiFinder;
import me.zipi.navitotesla.service.PoiFinderFactory;
import me.zipi.navitotesla.util.AnalysisUtil;

public class FavoriteDialogFragment extends DialogFragment implements
        AdapterView.OnItemSelectedListener, View.OnClickListener, RadioGroup.OnCheckedChangeListener {

    private String dest;
    @Setter
    private Runnable onDismissListener;

    public FavoriteDialogFragment() {
        super();
    }

    public FavoriteDialogFragment(String dest) {
        super();
        this.dest = dest;
    }

    private FavoriteDialogViewModel favoriteDialogViewModel;
    @Nullable
    private FavoriteDialogFragmentBinding binding;

    @Nullable
    PoiArrayAdapter poiArrayAdapter;

    @Nullable
    Executor executor;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setCancelable(false);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        favoriteDialogViewModel =
                new ViewModelProvider(this).get(FavoriteDialogViewModel.class);
        binding = FavoriteDialogFragmentBinding.inflate(inflater, container, false);

        poiArrayAdapter = new PoiArrayAdapter(getContext(), android.R.layout.simple_spinner_item);
        poiArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = binding.addressSelector;
        spinner.setAdapter(poiArrayAdapter);
        spinner.setOnItemSelectedListener(this);


        binding.btnDestSearch.setOnClickListener(this);
        binding.btnFavoriteDismiss.setOnClickListener(this);
        binding.btnFavoriteSave.setOnClickListener(this);
        binding.radioGroup.setOnCheckedChangeListener(this);
        favoriteDialogViewModel.getPoiList().observe(getViewLifecycleOwner(), (v) -> updateSpinner());

        executor = Executors.newSingleThreadExecutor();
        return binding.getRoot();
    }

    @Override
    public void onResume() {

        super.onResume();
        if (this.dest != null && binding != null) {
            binding.txtDest.setText(dest);
            searchDest();
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        binding = null;
        poiArrayAdapter = null;
        executor = null;
        dest = null;

    }

    @Override
    public void onClick(View v) {
        if (binding == null) {
            return;
        }
        if (v.getId() == binding.btnDestSearch.getId()) {
            searchDest();
        } else if (v.getId() == binding.btnFavoriteDismiss.getId()) {
            dismiss();
        } else if (v.getId() == binding.btnFavoriteSave.getId()) {
            saveFavorite();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (binding == null) {
            return;
        }
        Poi poi = favoriteDialogViewModel.getPoiList().getValue().get(position);
        favoriteDialogViewModel.getSelectedPoi().postValue(poi);

        if (binding.radioAddress.isChecked()) {
            binding.txtAddress.setText(poi.getFinalAddress());
        } else {
            binding.txtAddress.setText(poi.getGpsAddress());
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    private void saveFavorite() {
        if (binding == null || executor == null) {
            return;
        }
        PoiAddressEntity entity = PoiAddressEntity.builder()
                .registered(true)
                .poi(binding.txtDest.getText().toString())
                .address(binding.txtAddress.getText().toString())
                .build();

        executor.execute(() -> {
            AppDatabase.getInstance(getContext()).poiAddressDao().insertPoi(entity);
            if (getActivity() != null) {
                getActivity().runOnUiThread(this::dismiss);
                if (onDismissListener != null) {
                    onDismissListener.run();
                }
            }

        });

    }

    private void searchDest() {
        if (executor == null) {
            return;
        }
        executor.execute(() -> {
            if (binding == null) {
                return;
            }
            try {
                List<Poi> pois = PoiFinderFactory.getKakaoPoiFinder().listPoiAddress(binding.txtDest.getText().toString().trim());
                favoriteDialogViewModel.getPoiList().postValue(pois);
            } catch (Exception e) {
                AnalysisUtil.recordException(e);
            }
        });
    }


    private void updateSpinner() {
        if (poiArrayAdapter != null) {
            poiArrayAdapter.clear();
            poiArrayAdapter.addAll(favoriteDialogViewModel.getPoiList().getValue());
            poiArrayAdapter.notifyDataSetChanged();
        }
    }


    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (favoriteDialogViewModel.getSelectedPoi().getValue() == null || binding == null) {
            return;
        }
        if (binding.radioAddress.getId() == group.getCheckedRadioButtonId()) {
            binding.txtAddress.setText(favoriteDialogViewModel.getSelectedPoi().getValue().getFinalAddress());
        } else {
            binding.txtAddress.setText(favoriteDialogViewModel.getSelectedPoi().getValue().getGpsAddress());
        }
    }

    private static class PoiArrayAdapter extends ArrayAdapter<Poi> {
        public PoiArrayAdapter(Context context, @LayoutRes int resource) {
            super(context, resource);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

            TextView view = (TextView) super.getView(position, convertView, parent);
            view.setSingleLine(false);
            Poi poi = getItem(position);
            String shortAddress = poi.getFinalAddress();
            String[] addressSplit = shortAddress.split(" ");
            if (addressSplit.length > 2) {
                shortAddress = addressSplit[0] + " " + addressSplit[1];
            }
            String text = poi.getPoiName() + "\n" + shortAddress;
            view.setText(text);
            view.setTextSize(12);

            return view;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            TextView view = (TextView) super.getDropDownView(position, convertView, parent);
            view.setSingleLine(false);
            Poi poi = getItem(position);
            String text = poi.getPoiName() + "\n" + poi.getFinalAddress();
            view.setText(text);
            view.setTextSize(12);

            return view;
        }
    }
}