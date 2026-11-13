package com.forge.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.forge.app.data.model.Reflection
import com.forge.app.databinding.ItemReflectionBinding

class ReflectionAdapter : ListAdapter<Reflection, ReflectionAdapter.ViewHolder>(ReflectionDiffCallback()) {

    inner class ViewHolder(private val binding: ItemReflectionBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(reflection: Reflection) {
            binding.tvRefDate.text = reflection.date
            binding.tvRefPrompt.text = "// ${reflection.promptUsed}"
            binding.tvRefText.text = reflection.text

            if (reflection.aiResponse != null) {
                binding.tvRefAiResponse.text = reflection.aiResponse
                binding.tvRefAiResponse.visibility = View.VISIBLE
            } else {
                binding.tvRefAiResponse.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReflectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ReflectionDiffCallback : DiffUtil.ItemCallback<Reflection>() {
        override fun areItemsTheSame(oldItem: Reflection, newItem: Reflection): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Reflection, newItem: Reflection): Boolean {
            return oldItem == newItem
        }
    }
}
