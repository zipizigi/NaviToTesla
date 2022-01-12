package me.zipi.navitotesla.ui.favorite;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import me.zipi.navitotesla.R;
import me.zipi.navitotesla.db.PoiAddressEntity;

public class PoiAddressRecyclerAdapter extends RecyclerView.Adapter<PoiAddressRecyclerAdapter.ViewHolder> {

    List<PoiAddressEntity> items;
    OnFavoriteButtonClicked listener;

    public PoiAddressRecyclerAdapter(OnFavoriteButtonClicked listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.poi_address_view, parent, false);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.onBind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<PoiAddressEntity> items) {
        this.items = items;
        this.notifyDataSetChanged();
    }

    public interface OnFavoriteButtonClicked {
        void onClick(int position);

        void onShareClick(int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView poiView;
        TextView addressView;
        Button button;
        Button shareButton;
        boolean isRegistered;
        private WeakReference<OnFavoriteButtonClicked> listenerRef;

        public ViewHolder(@NonNull View itemView, OnFavoriteButtonClicked listener) {
            super(itemView);

            poiView = itemView.findViewById(R.id.recylcer_poi);
            addressView = itemView.findViewById(R.id.recylcer_address);
            button = itemView.findViewById(R.id.btnFavorite);
            shareButton = itemView.findViewById(R.id.btnShare);
            listenerRef = new WeakReference<>(listener);
            button.setOnClickListener(this);
            shareButton.setOnClickListener(this);
        }

        void onBind(PoiAddressEntity item) {
            poiView.setText(item.getPoi());
            addressView.setText(item.getAddress());
            isRegistered = item.isRegistered();
            if (!isRegistered) {
                button.setBackgroundResource(R.drawable.ic_baseline_add_24);
                shareButton.setVisibility(View.GONE);
            } else {
                button.setBackgroundResource(R.drawable.ic_baseline_remove_24);
                shareButton.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onClick(View view) {
            if (listenerRef != null && listenerRef.get() != null) {
                if (view.getId() == shareButton.getId()) {
                    listenerRef.get().onShareClick(getAdapterPosition());
                } else {
                    listenerRef.get().onClick(getAdapterPosition());
                }
            }
        }
    }

}
