package com.forge.app.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.forge.app.data.dao.XpBySource
import com.forge.app.databinding.ItemXpSourceBinding

class XpSourceAdapter : ListAdapter<XpBySource, XpSourceAdapter.ViewHolder>(XpSourceDiffCallback()) {

    private val sourceLabels = mapOf(
        "labor" to "Labors",
        "focus" to "Focus",
        "reflection" to "Reflection",
        "audit" to "Audit",
        "streak_bonus" to "Streak Bonus",
        "time_track" to "Time Tracking"
    )

    private val sourceColors = mapOf(
        "labor" to "#E53935",
        "focus" to "#FFB300",
        "reflection" to "#CE93D8",
        "audit" to "#00E676",
        "streak_bonus" to "#FF5252",
        "time_track" to "#80DEEA"
    )

    private var maxXp: Int = 1

    fun setMaxXp(max: Int) {
        maxXp = max.coerceAtLeast(1)
    }

    inner class ViewHolder(private val binding: ItemXpSourceBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: XpBySource) {
            binding.tvXpSourceName.text = sourceLabels[item.source] ?: item.source
            binding.tvXpSourceAmount.text = "${item.total} XP"

            val percent = (item.total * 100 / maxXp).coerceIn(0, 100)
            binding.pbXpSourceBar.progress = percent

            val color = Color.parseColor(sourceColors[item.source] ?: "#9E9E9E")
            binding.pbXpSourceBar.progressTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemXpSourceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class XpSourceDiffCallback : DiffUtil.ItemCallback<XpBySource>() {
        override fun areItemsTheSame(oldItem: XpBySource, newItem: XpBySource): Boolean {
            return oldItem.source == newItem.source
        }

        override fun areContentsTheSame(oldItem: XpBySource, newItem: XpBySource): Boolean {
            return oldItem == newItem
        }
    }
}
