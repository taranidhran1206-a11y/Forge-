package com.forge.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.forge.app.data.model.PlanEntry
import com.forge.app.databinding.ItemPlanEntryBinding

class PlanEntryAdapter : ListAdapter<PlanEntry, PlanEntryAdapter.ViewHolder>(PlanDiffCallback()) {

    inner class ViewHolder(private val binding: ItemPlanEntryBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: PlanEntry) {
            binding.tvPlanInput.text = "> ${entry.input}"
            binding.tvPlanOutput.text = entry.output
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlanEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PlanDiffCallback : DiffUtil.ItemCallback<PlanEntry>() {
        override fun areItemsTheSame(oldItem: PlanEntry, newItem: PlanEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PlanEntry, newItem: PlanEntry): Boolean {
            return oldItem == newItem
        }
    }
}
