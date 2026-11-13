package com.forge.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.forge.app.data.model.CoachMessage
import com.forge.app.databinding.ItemCoachMessageBinding

class CoachMessageAdapter : ListAdapter<CoachMessage, CoachMessageAdapter.ViewHolder>(CoachDiffCallback()) {

    inner class ViewHolder(private val binding: ItemCoachMessageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: CoachMessage) {
            if (message.isUser) {
                binding.tvUserText.text = message.text
                binding.tvUserText.visibility = View.VISIBLE
                binding.llAiMessage.visibility = View.GONE
            } else {
                binding.tvAiText.text = message.text
                binding.llAiMessage.visibility = View.VISIBLE
                binding.tvUserText.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCoachMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CoachDiffCallback : DiffUtil.ItemCallback<CoachMessage>() {
        override fun areItemsTheSame(oldItem: CoachMessage, newItem: CoachMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CoachMessage, newItem: CoachMessage): Boolean {
            return oldItem == newItem
        }
    }
}
