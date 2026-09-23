package com.rynvortex.spddump.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rynvortex.spddump.databinding.ItemFileRowBinding
import java.io.File

class FileAdapter(
    private var items: List<File>,
    private val onDelete: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    inner class VH(val binding: ItemFileRowBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(newItems: List<File>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFileRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        holder.binding.fileNameText.text = file.name
        holder.binding.fileSizeText.text = "%.1f MB".format(file.length() / 1024.0 / 1024.0)
        holder.binding.deleteButton.setOnClickListener { onDelete(file) }
    }

    override fun getItemCount() = items.size
}
