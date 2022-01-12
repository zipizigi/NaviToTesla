package me.zipi.navitotesla.ui.favorite;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import me.zipi.navitotesla.AppExecutors;
import me.zipi.navitotesla.databinding.FragmentFavoriteBinding;
import me.zipi.navitotesla.db.AppDatabase;
import me.zipi.navitotesla.db.PoiAddressEntity;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.util.AnalysisUtil;

public class FavoriteFragment extends Fragment implements View.OnClickListener {

    private FavoriteViewModel favoriteViewModel;
    @Nullable
    private FragmentFavoriteBinding binding;
    @Nullable
    private PoiAddressRecyclerAdapter poiHistoryRecyclerAdapter;
    @Nullable
    private PoiAddressRecyclerAdapter poiRegisteredRecyclerAdapter;
    @Nullable
    private NaviToTeslaService naviToTeslaService;
    @Nullable
    private AppDatabase appDatabase;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        favoriteViewModel =
                new ViewModelProvider(this).get(FavoriteViewModel.class);

        binding = FragmentFavoriteBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        poiHistoryRecyclerAdapter = new PoiAddressRecyclerAdapter(new PoiAddressRecyclerAdapter.OnFavoriteButtonClicked() {
            @Override
            public void onClick(int position) {
                addFavoriteLocation(position);
            }

            @Override
            public void onShareClick(int position) {

            }
        });
        binding.recylerHistory.setAdapter(poiHistoryRecyclerAdapter);
        binding.recylerHistory.setLayoutManager(new LinearLayoutManager(getContext()));

        poiRegisteredRecyclerAdapter = new PoiAddressRecyclerAdapter(new PoiAddressRecyclerAdapter.OnFavoriteButtonClicked() {
            @Override
            public void onClick(int position) {
                removeFavoriteLocation(position);
            }

            @Override
            public void onShareClick(int position) {
                shareLocation(position);
            }
        });
        binding.recylerRegistered.setAdapter(poiRegisteredRecyclerAdapter);
        binding.recylerRegistered.setLayoutManager(new LinearLayoutManager(getContext()));

        binding.btnFavoriteAdd.setOnClickListener(this);
        binding.btnFavoriteHelp.setOnClickListener(this);
        favoriteViewModel.getRecentPoiAddress().observe(getViewLifecycleOwner(),
                items -> poiHistoryRecyclerAdapter.setItems(items));
        favoriteViewModel.getRegisteredPoiAddress().observe(getViewLifecycleOwner(),
                items -> poiRegisteredRecyclerAdapter.setItems(items));
        appDatabase = AppDatabase.getInstance(getContext());
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        poiHistoryRecyclerAdapter = null;
        poiRegisteredRecyclerAdapter = null;
        naviToTeslaService = null;
        appDatabase = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePoiAddress();

    }

    private void updatePoiAddress() {
        AppExecutors.execute(() -> {
            if (appDatabase != null) {
                favoriteViewModel.getRecentPoiAddress().postValue(appDatabase.poiAddressDao().findRecentPoiSync(25));
                favoriteViewModel.getRegisteredPoiAddress().postValue(appDatabase.poiAddressDao().findRegisteredPoiSync());
            }
        });

    }

    private void addFavoriteLocation(int position) {
        String poi = favoriteViewModel.getRecentPoiAddress().getValue().get(position).getPoi();
        addFavorite(poi);
    }

    private void removeFavoriteLocation(int position) {
        if (getActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getActivity())
                .setCancelable(true)
                .setTitle("즐겨찾기 삭제")
                .setMessage("해당 즐겨찾기를 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> removeFavorite(position))
                .setNegativeButton("취소", (dialog, which) -> {
                })
                .show();
    }

    private void shareLocation(int position) {
        if (getActivity() == null) {
            return;
        }
        PoiAddressEntity poi = favoriteViewModel.getRegisteredPoiAddress().getValue().get(position);
        new AlertDialog.Builder(getActivity())
                .setCancelable(true)
                .setTitle("목적지 전송")
                .setMessage("목적지를 전송하시겠습니까?\n - " + poi.getAddress())
                .setPositiveButton("전송", (dialog, which) -> {

                    AppExecutors.execute(() -> {
                        if (getActivity() == null) {
                            return;
                        }
                        if (naviToTeslaService == null) {
                            naviToTeslaService = new NaviToTeslaService(getActivity());
                        }
                        try {
                            naviToTeslaService.share(poi.getAddress());
                        } catch (Exception e) {
                            AnalysisUtil.recordException(e);
                        }
                    });
                    //
                })
                .setNegativeButton("취소", (dialog, which) -> {
                })
                .show();
    }


    private void removeFavorite(int position) {

        AppExecutors.execute(() -> {
            if (appDatabase == null) {
                return;
            }
            PoiAddressEntity poi = favoriteViewModel.getRegisteredPoiAddress().getValue().get(position);
            appDatabase.poiAddressDao().delete(poi);
            updatePoiAddress();
        });
    }

    private void addFavorite() {
        addFavorite(null);
    }

    private void addFavorite(String dest) {
        if (getActivity() == null) {
            return;
        }
        FavoriteDialogFragment dialog = new FavoriteDialogFragment(dest);
        dialog.setOnDismissListener(this::updatePoiAddress);
        dialog.show(getChildFragmentManager(), FavoriteDialogFragment.class.getName());
    }

    @Override
    public void onClick(View v) {
        if (binding == null || getActivity() == null) {
            return;
        }
        if (v.getId() == binding.btnFavoriteHelp.getId()) {
            new AlertDialog.Builder(getActivity())
                    .setTitle("안내")
                    .setMessage("집, 회사와 같은 즐겨찾는 목적지와 중복된 목적지의 실제 주소를 입력하여 사용할 수 있습니다.")
                    .setCancelable(true)
                    .setPositiveButton("확인", (dialog, which) -> {
                    })
                    .create().show();
        } else if (v.getId() == binding.btnFavoriteAdd.getId()) {
            addFavorite();
        }
    }
}