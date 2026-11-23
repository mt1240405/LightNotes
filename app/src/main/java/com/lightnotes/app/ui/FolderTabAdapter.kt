package com.lightnotes.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.lightnotes.app.R
import com.lightnotes.app.databinding.ItemFolderTabBinding
import com.lightnotes.app.ui.viewmodel.NotesFilterType

data class FolderTabItem(
    val filterType: NotesFilterType,
    val folderGlobalId: String? = null,
    val name: String,
    val count: Int = 0
)

class FolderTabAdapter(
    private val onTabSelected: (FolderTabItem) -> Unit
) : RecyclerView.Adapter<FolderTabAdapter.TabViewHolder>() {

    private val tabs = mutableListOf<FolderTabItem>()
    private var selectedPosition = 0

    fun submitTabs(newTabs: List<FolderTabItem>) {
        tabs.clear()
        tabs.addAll(newTabs)
        notifyDataSetChanged()
    }

    fun setSelectedTab(position: Int) {
        val prev = selectedPosition
        selectedPosition = position
        notifyItemChanged(prev)
        notifyItemChanged(selectedPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemFolderTabBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val item = tabs[position]
        holder.bind(item, position == selectedPosition)
    }

    override fun getItemCount(): Int = tabs.size

    inner class TabViewHolder(private val binding: ItemFolderTabBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FolderTabItem, isSelected: Boolean) {
            val context = binding.root.context
            binding.tvTabName.text = if (item.count > 0) "${item.name} (${item.count})" else item.name

            if (isSelected) {
                binding.tvTabName.setBackgroundResource(R.drawable.bg_folder_tab_selected)
                binding.tvTabName.setTextColor(ContextCompat.getColor(context, R.color.brand_amber))
            } else {
                binding.tvTabName.setBackgroundResource(R.drawable.bg_folder_tab_unselected)
                binding.tvTabName.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos != selectedPosition) {
                    setSelectedTab(pos)
                    onTabSelected(item)
                }
            }
        }
    }
}
