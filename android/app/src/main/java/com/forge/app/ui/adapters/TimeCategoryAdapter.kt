package com.forge.app.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.forge.app.data.dao.CategoryTime
import com.forge.app.databinding.ItemTimeCategoryBinding

class TimeCategoryAdapter : ListAdapter<CategoryTime, TimeCategoryAdapter.ViewHolder>(CategoryDiffCallback()) {

    private val categoryEmojis = mapOf(
        "deep_work" to "🎯",
        "admin" to "📋",
        "social_media" to "📱",
        "youtube" to "▶️",
        "exercise" to "💪",
        "rest" to "😴",
        "learning" to "📚",
        "other" to "📌"
    )

    private val categoryLabels = mapOf(
        "deep_work" to "Deep Work",
        "admin" to "Admin",
        "social_media" to "Social Media",
        "youtube" to "YouTube",
        "exercise" to "Exercise",
        "rest" to "Rest",
        "learning" to "Learning",
        "other" to "Other"
    )

    private val categoryColors = mapOf(
        "deep_work" to "#E53935",
        "admin" to "#FFB300",
        "social_media" to "#FF5252",
        "youtube" to "#FF5252",
        "exercise" to "#00E676",
        "rest" to "#80DEEA",
        "learning" to "#CE93D8",
        "other" to "#9E9E9E"
    )

    private var totalMinutes: Int = 0

    fun setTotalMinutes(total: Int) {
        totalMinutes = total
    }

    inner class ViewHolder(private val binding: ItemTimeCategoryBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CategoryTime) {
            binding.tvCatEmoji.text = categoryEmojis[item.category] ?: "📌"
            binding.tvCatName.text = categoryLabels[item.category] ?: item.category
            binding.tvCatDuration.text = formatMinutes(item.minutes)

            val percent = if (totalMinutes > 0) (item.minutes * 100 / totalMinutes) else 0
            binding.pbCatBar.progress = percent

            val color = Color.parseColor(categoryColors[item.category] ?: "#9E9E9E")
            binding.pbCatBar.progressTintList = android.content.res.ColorStateList.valueOf(color)
        }

        private fun formatMinutes(mins: Int): String {
            return if (mins >= 60) {
                "${mins / 60}h ${mins % 60}m"
            } else {
                "${mins}m"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTimeCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryTime>() {
        override fun areItemsTheSame(oldItem: CategoryTime, newItem: CategoryTime): Boolean {
            return oldItem.category == newItem.category
        }

        override fun areContentsTheSame(oldItem: CategoryTime, newItem: CategoryTime): Boolean {
            return oldItem == newItem
        }
    }
}
