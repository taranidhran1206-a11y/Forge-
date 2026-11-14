package com.forge.app.ui.adapters

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.forge.app.R
import com.forge.app.data.model.Labor
import com.forge.app.databinding.ItemLaborBinding

class LaborAdapter(
    private val onLaborClick: (Labor) -> Unit,
    private val onLaborLongClick: (Labor) -> Unit = {}
) : ListAdapter<Labor, LaborAdapter.ViewHolder>(LaborDiffCallback()) {

    inner class ViewHolder(private val binding: ItemLaborBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(labor: Labor) {
            binding.tvLaborName.text = "${labor.emoji} ${labor.name}"
            binding.tvLaborDesc.text = labor.description.ifEmpty { "Complete this task" }
            binding.tvXpReward.text = "+${labor.xpReward} XP"

            // Done state - use green check circle vs pending circle
            if (labor.done) {
                binding.ivLaborCheck.setImageResource(R.drawable.widget_check_done)
                binding.tvLaborName.paintFlags = binding.tvLaborName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvLaborName.setTextColor(Color.parseColor("#666666"))
                binding.tvXpReward.setTextColor(Color.parseColor("#666666"))
                binding.root.alpha = 0.7f
            } else {
                binding.ivLaborCheck.setImageResource(R.drawable.widget_check_pending)
                binding.tvLaborName.paintFlags = binding.tvLaborName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.tvLaborName.setTextColor(Color.parseColor("#F5F5F5"))
                binding.tvXpReward.setTextColor(Color.parseColor("#4CAF50"))
                binding.root.alpha = 1.0f
            }

            binding.root.setOnClickListener {
                onLaborClick(labor)
            }

            binding.root.setOnLongClickListener {
                onLaborLongClick(labor)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLaborBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LaborDiffCallback : DiffUtil.ItemCallback<Labor>() {
        override fun areItemsTheSame(oldItem: Labor, newItem: Labor): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Labor, newItem: Labor): Boolean {
            return oldItem == newItem
        }
    }
}
