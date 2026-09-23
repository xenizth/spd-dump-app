package com.rynvortex.spddump.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rynvortex.spddump.databinding.ItemOperationCardBinding

class OperationAdapter(
    private val items: List<Operation>,
    private val onClick: (Operation) -> Unit
) : RecyclerView.Adapter<OperationAdapter.VH>() {

    inner class VH(val binding: ItemOperationCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOperationCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val op = items[position]
        holder.binding.titleView.text = op.title
        holder.binding.subtitleView.text = op.subtitle
        holder.binding.badgeView.text = op.badge
        if (op.badgeIsWarning) {
            holder.binding.badgeView.setTextColor(Color.parseColor("#E8B34A"))
        }
        holder.binding.root.setOnClickListener { onClick(op) }
    }

    override fun getItemCount() = items.size
}
