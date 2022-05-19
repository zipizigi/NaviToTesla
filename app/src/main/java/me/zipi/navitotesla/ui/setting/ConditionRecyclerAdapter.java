package me.zipi.navitotesla.ui.setting;

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

public class ConditionRecyclerAdapter extends RecyclerView.Adapter<ConditionRecyclerAdapter.ViewHolder> {

    List<String> items;
    final OnDeleteButtonClicked listener;

    public ConditionRecyclerAdapter(OnDeleteButtonClicked listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.condition_view, parent, false);
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
    public void setItems(List<String> items) {
        this.items = items;
        this.notifyDataSetChanged();
    }

    public interface OnDeleteButtonClicked {
        void onClick(int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView nameView;
        final Button deleteButton;
        private final WeakReference<OnDeleteButtonClicked> listenerRef;

        public ViewHolder(@NonNull View itemView, OnDeleteButtonClicked listener) {
            super(itemView);

            nameView = itemView.findViewById(R.id.recylcer_bluetooth);
            deleteButton = itemView.findViewById(R.id.btnConditionDelete);
            listenerRef = new WeakReference<>(listener);
            deleteButton.setOnClickListener(this);
        }

        void onBind(String item) {
            if (item != null) {
                nameView.setText(item);
            }
        }

        @Override
        public void onClick(View view) {
            if (listenerRef != null && listenerRef.get() != null) {
                listenerRef.get().onClick(getAdapterPosition());
            }
        }
    }

}
