package me.zipi.navitotesla.ui.favorite

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.zipi.navitotesla.R
import me.zipi.navitotesla.db.PoiAddressEntity
import java.lang.ref.WeakReference

class PoiAddressRecyclerAdapter(val listener: OnFavoriteButtonClicked) :
    RecyclerView.Adapter<PoiAddressRecyclerAdapter.ViewHolder>() {
    private var items: List<PoiAddressEntity> = listOf()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.poi_address_view, parent, false)
        return ViewHolder(view, listener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.onBind(items!![position])
    }

    override fun getItemCount(): Int {
        return items!!.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<PoiAddressEntity>) {
        this.items = items
        notifyDataSetChanged()
    }

    interface OnFavoriteButtonClicked {
        fun onClick(position: Int)
        fun onShareClick(position: Int)
    }

    class ViewHolder(itemView: View, listener: OnFavoriteButtonClicked?) :
        RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val poiView: TextView
        val addressView: TextView
        val button: Button
        val shareButton: Button
        var isRegistered = false
        private val listenerRef: WeakReference<OnFavoriteButtonClicked?>?

        init {
            poiView = itemView.findViewById(R.id.recylcer_poi)
            addressView = itemView.findViewById(R.id.recylcer_address)
            button = itemView.findViewById(R.id.btnFavorite)
            shareButton = itemView.findViewById(R.id.btnShare)
            listenerRef = WeakReference(listener)
            button.setOnClickListener(this)
            shareButton.setOnClickListener(this)
        }

        fun onBind(item: PoiAddressEntity) {
            poiView.text = item.poi
            addressView.text = item.address
            isRegistered = item.isRegistered()
            if (!isRegistered) {
                button.setBackgroundResource(R.drawable.ic_baseline_add_24)
                shareButton.visibility = View.GONE
            } else {
                button.setBackgroundResource(R.drawable.ic_baseline_remove_24)
                shareButton.visibility = View.VISIBLE
            }
        }

        override fun onClick(view: View) {
            if (listenerRef != null && listenerRef.get() != null) {
                if (view.id == shareButton.id) {
                    listenerRef.get()!!.onShareClick(adapterPosition)
                } else {
                    listenerRef.get()!!.onClick(adapterPosition)
                }
            }
        }
    }
}