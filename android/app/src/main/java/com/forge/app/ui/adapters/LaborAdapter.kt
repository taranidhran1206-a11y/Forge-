package com.forge.app.ui.adapters

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.forge.app.R
import com.forge.app.data.model.Labor
import com.forge.app.databinding.ItemLaborBinding

/**
 * Labors grouped under Morning / Afternoon / Evening headers. The first open
 * labor is marked as up next.
 */
class LaborAdapter(
    private val onLaborClick: (Labor) -> Unit,
    private val onLaborLongClick: (Labor) -> Unit = {},
    private val onFocusClick: (Labor) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Item(val labor: Labor, val isNext: Boolean) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submitList(labors: List<Labor>) {
        val order = listOf("MORNING", "AFTERNOON", "EVENING")
        val built = mutableListOf<Row>()
        val known = labors.filter { it.timeOfDay.uppercase() in order }
        val other = labors.filter { it.timeOfDay.uppercase() !in order }
        val ordered = order.flatMap { section -> known.filter { it.timeOfDay.uppercase() == section } } + other
        val nextId = ordered.firstOrNull { !it.done }?.id
        order.forEach { section ->
            val inSection = known.filter { it.timeOfDay.uppercase() == section }
            if (inSection.isNotEmpty()) {
                built.add(Row.Header(section))
                inSection.forEach { built.add(Row.Item(it, it.id == nextId)) }
            }
        }
        if (other.isNotEmpty()) {
            built.add(Row.Header("ANYTIME"))
            other.forEach { built.add(Row.Item(it, it.id == nextId)) }
        }
        rows = built
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = if (rows[position] is Row.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderHolder(inflater.inflate(R.layout.item_labor_header, parent, false) as TextView)
        } else {
            ItemHolder(ItemLaborBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).view.text = row.title
            is Row.Item -> (holder as ItemHolder).bind(row)
        }
    }

    private class HeaderHolder(val view: TextView) : RecyclerView.ViewHolder(view)

    private inner class ItemHolder(private val binding: ItemLaborBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: Row.Item) {
            val labor = row.labor
            binding.tvLaborName.text = labor.name
            val desc = labor.description.ifEmpty { "Complete this task" }
            binding.tvLaborDesc.text = if (row.isNext) "Up next · $desc" else desc
            binding.tvXpReward.text = "+${labor.xpReward}"

            if (labor.done) {
                binding.ivLaborCheck.setImageResource(R.drawable.ic_check_done)
                binding.tvLaborName.paintFlags = binding.tvLaborName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvLaborName.setTextColor(Color.parseColor("#8A8A8A"))
                binding.tvXpReward.setTextColor(Color.parseColor("#8A8A8A"))
            } else {
                binding.ivLaborCheck.setImageResource(R.drawable.ic_check_pending)
                binding.tvLaborName.paintFlags = binding.tvLaborName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.tvLaborName.setTextColor(Color.parseColor("#F5F5F5"))
                binding.tvXpReward.setTextColor(Color.parseColor("#4CAF50"))
            }
            binding.root.setBackgroundResource(
                if (row.isNext) R.drawable.bg_card_next else R.drawable.bg_card
            )
            binding.ivChevron.visibility = if (labor.done) View.INVISIBLE else View.VISIBLE

            // Deep work style labors get a one-tap focus session
            val name = labor.name.lowercase()
            val focusable = !labor.done &&
                (name.contains("deep work") || name.contains("focus") || name.contains("study"))
            binding.tvLaborFocus.visibility = if (focusable) View.VISIBLE else View.GONE
            binding.tvLaborFocus.setOnClickListener { onFocusClick(labor) }

            binding.root.setOnClickListener { onLaborClick(labor) }
            binding.root.setOnLongClickListener {
                onLaborLongClick(labor)
                true
            }
        }
    }
}
