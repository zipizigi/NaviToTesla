package me.zipi.navitotesla.ui.setting

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.zipi.navitotesla.R
import java.lang.ref.WeakReference

class ConditionRecyclerAdapter(
    private val listener: OnDeleteButtonClicked,
) : RecyclerView.Adapter<ConditionRecyclerAdapter.ViewHolder>() {
    private var items: List<String> = mutableListOf()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.condition_view, parent, false)
        return ViewHolder(view, listener)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.onBind(items[position])
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<String>) {
        this.items = items
        notifyDataSetChanged()
    }

    interface OnDeleteButtonClicked {
        fun onClick(position: Int)
    }

    class ViewHolder(
        itemView: View,
        listener: OnDeleteButtonClicked,
    ) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        private val nameView: TextView
        private val deleteButton: Button
        private val listenerRef: WeakReference<OnDeleteButtonClicked>

        init {
            nameView = itemView.findViewById(R.id.recylcer_bluetooth)
            deleteButton = itemView.findViewById(R.id.btnConditionDelete)
            listenerRef = WeakReference(listener)
            deleteButton.setOnClickListener(this)
        }

        fun onBind(item: String?) {
            if (item != null) {
                nameView.text = item
            }
        }

        override fun onClick(view: View) {
            listenerRef.get()?.onClick(adapterPosition)
        }
    }
}
