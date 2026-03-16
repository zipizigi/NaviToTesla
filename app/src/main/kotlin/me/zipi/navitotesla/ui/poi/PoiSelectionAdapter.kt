package me.zipi.navitotesla.ui.poi

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.zipi.navitotesla.R
import me.zipi.navitotesla.model.Poi

class PoiSelectionAdapter(
    private val onItemClick: (Poi) -> Unit,
) : RecyclerView.Adapter<PoiSelectionAdapter.ViewHolder>() {
    private var items: List<Poi> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<Poi>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_poi_overlay, parent, false)
        return ViewHolder(view as TextView, onItemClick)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.onBind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        private val textView: TextView,
        private val onItemClick: (Poi) -> Unit,
    ) : RecyclerView.ViewHolder(textView) {
        fun onBind(poi: Poi) {
            textView.text = poi.getRoadAddress()
            textView.setOnClickListener { onItemClick(poi) }
        }
    }
}
