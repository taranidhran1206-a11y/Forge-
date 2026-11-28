package com.forge.app.ui.progress

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.forge.app.R
import com.forge.app.data.model.RankSystem
import com.forge.app.databinding.FragmentProgressBinding
import com.forge.app.ui.ForgeViewModelFactory

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProgressViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val factory = ForgeViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[ProgressViewModel::class.java]
        applyPanes()
        observeViewModel()
        com.forge.app.sync.AccountUi.bind(this, binding.tvAccountLabel, binding.cardAccount)
    }

    /** Side by side in landscape and on tablets, stacked on a phone in portrait. */
    private fun applyPanes() {
        val twoPane = resources.getBoolean(R.bool.two_pane)
        val gap = resources.getDimensionPixelSize(R.dimen.pane_gap)
        binding.panes.orientation = if (twoPane) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        listOf(binding.paneLeft, binding.paneRight).forEachIndexed { index, pane ->
            val params = pane.layoutParams as LinearLayout.LayoutParams
            if (twoPane) {
                params.width = 0
                params.weight = 1f
                params.marginStart = if (index == 1) gap else 0
            } else {
                params.width = LinearLayout.LayoutParams.MATCH_PARENT
                params.weight = 0f
                params.marginStart = 0
            }
            pane.layoutParams = params
        }
    }

    private fun pretty(rank: RankSystem.Rank): String =
        rank.title.lowercase().replaceFirstChar { it.uppercase() }

    private fun colorOf(rank: RankSystem.Rank): Int =
        try { Color.parseColor(rank.colorHex) } catch (_: Exception) { Color.WHITE }

    /** Current rank, the next three, then one line for the rest. Thresholds are the real ones. */
    private fun renderLadder(current: RankSystem.Rank) {
        val ranks = RankSystem.Rank.values()
        val shown = ranks.filter { it.ordinal >= current.ordinal }.take(4)
        val rest = ranks.filter { it.ordinal > (shown.lastOrNull()?.ordinal ?: current.ordinal) }
        binding.llRankLadder.removeAllViews()

        shown.forEach { rank ->
            val row = layoutInflater.inflate(R.layout.item_rank_row, binding.llRankLadder, false)
            row.findViewById<ImageView>(R.id.iv_rank_dot).setBackgroundColor(colorOf(rank))
            row.findViewById<TextView>(R.id.tv_rank_row_name).text = pretty(rank)
            val right = row.findViewById<TextView>(R.id.tv_rank_row_right)
            if (rank == current) {
                row.setBackgroundColor(Color.parseColor("#1A1A1A"))
                right.text = "YOU ARE HERE"
                right.typeface = android.graphics.Typeface.DEFAULT_BOLD
                right.letterSpacing = 0.08f
                right.setTextColor(colorOf(rank))
            } else {
                right.text = String.format("%,d XP", rank.minXp)
            }
            binding.llRankLadder.addView(row)
        }

        if (rest.isNotEmpty()) {
            val names = rest.map { pretty(it) }
            val list = if (names.size == 1) names[0]
            else names.dropLast(1).joinToString(", ") + " and " + names.last()
            val footer = TextView(requireContext()).apply {
                text = "Then $list at ${String.format("%,d", rest.last().minXp)} XP"
                setTextColor(Color.parseColor("#9E9E9E"))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_small))
                val pad = resources.getDimensionPixelSize(R.dimen.card_pad)
                setPadding(pad, pad * 3 / 4, pad, pad * 3 / 4)
            }
            binding.llRankLadder.addView(footer)
        }
    }

    private fun barRow(parent: LinearLayout, name: String, value: String, progress: Int, max: Int, bar: Int) {
        val row = layoutInflater.inflate(R.layout.item_bar_row, parent, false)
        row.findViewById<TextView>(R.id.tv_bar_name).text = name
        row.findViewById<TextView>(R.id.tv_bar_value).text = value
        row.findViewById<ProgressBar>(R.id.pb_bar).apply {
            progressDrawable = ContextCompat.getDrawable(requireContext(), bar)
            this.max = max.coerceAtLeast(1)
            this.progress = progress
        }
        parent.addView(row)
    }

    private fun emptyRow(parent: LinearLayout, message: String) {
        parent.addView(TextView(requireContext()).apply {
            text = message
            setTextColor(Color.parseColor("#9E9E9E"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_small))
            setPadding(0, 16, 0, 16)
        })
    }

    private fun observeViewModel() {
        viewModel.totalXp.observe(viewLifecycleOwner) { xp ->
            binding.tvXpCurrent.text = formatXp(xp)

            val rank = RankSystem.getRank(xp)
            val color = colorOf(rank)
            binding.tvRankTitle.text = rank.title
            binding.tvRankTitle.setTextColor(color)
            binding.tvRankLevel.text = "LEVEL ${rank.ordinal + 1}"
            binding.ivRankHex.setColorFilter(color)

            val pct = (RankSystem.getProgress(xp) * 100).toInt()
            binding.pbRankXp.progress = pct
            binding.tvRankPct.text = "$pct%"
            binding.tvRankPct.setTextColor(color)
            binding.pbRankXp.progressTintList = android.content.res.ColorStateList.valueOf(color)
            val next = RankSystem.getNextRank(rank)
            binding.tvXpNext.text =
                if (next != null) "${String.format("%,d", RankSystem.getXpToNext(xp))} XP to ${pretty(next)}"
                else "Top rank reached"
            renderLadder(rank)
        }

        viewModel.weeklyStats.observe(viewLifecycleOwner) { stats ->
            binding.llWeeklyStats.removeAllViews()
            binding.tvWeekXp.text = "+${formatXp(stats.sumOf { it.totalXp })}"
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val maxXp = stats.maxOfOrNull { it.totalXp } ?: 0
            stats.forEach { stat ->
                // Rows are the last seven dates, so label each from its own date
                val label = try {
                    val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(stat.date)
                    val cal = java.util.Calendar.getInstance()
                    if (parsed != null) cal.time = parsed
                    dayNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                } catch (_: Exception) {
                    stat.date
                }
                barRow(binding.llWeeklyStats, label, "${stat.totalXp} XP", stat.totalXp, maxXp, R.drawable.bg_bar_green)
            }
        }

        viewModel.xpBySource.observe(viewLifecycleOwner) { sources ->
            binding.llXpBreakdown.removeAllViews()
            val total = sources.sumOf { it.total }
            if (sources.isEmpty()) emptyRow(binding.llXpBreakdown, "No XP yet. Complete a labor to start.")
            sources.forEach { source ->
                barRow(
                    binding.llXpBreakdown,
                    formatSourceName(source.source), "${source.total} XP",
                    source.total, total, R.drawable.bg_xp_progress
                )
            }
        }

        viewModel.todayTimeByCategory.observe(viewLifecycleOwner) { categories ->
            binding.llTimeAllocation.removeAllViews()
            val total = categories.sumOf { it.minutes }
            if (categories.isEmpty()) emptyRow(binding.llTimeAllocation, "Nothing tracked yet today.")
            categories.forEach { cat ->
                val text = if (cat.minutes >= 60) "${cat.minutes / 60}h ${cat.minutes % 60}m" else "${cat.minutes}m"
                barRow(
                    binding.llTimeAllocation,
                    formatCategoryName(cat.category), text,
                    cat.minutes, total, R.drawable.bg_bar_rank
                )
            }
        }

        viewModel.levelUpEvent.observe(viewLifecycleOwner) { rank ->
            if (rank != null) {
                showLevelUpAnimation(rank)
                viewModel.clearLevelUp()
            }
        }
    }

    private fun showLevelUpAnimation(rank: RankSystem.Rank) {
        binding.flLevelUpOverlay.visibility = View.VISIBLE
        binding.tvLevelUpRank.text = rank.title
        binding.tvLevelUpRank.setTextColor(colorOf(rank))

        val scaleX = ObjectAnimator.ofFloat(binding.tvLevelUpRank, View.SCALE_X, 0.3f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.tvLevelUpRank, View.SCALE_Y, 0.3f, 1.2f, 1f)
        val alpha = ObjectAnimator.ofFloat(binding.flLevelUpOverlay, View.ALPHA, 0f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        binding.flLevelUpOverlay.postDelayed({
            val overlay = _binding?.flLevelUpOverlay ?: return@postDelayed
            val fadeOut = ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f)
            fadeOut.duration = 500
            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    _binding?.flLevelUpOverlay?.visibility = View.GONE
                }
            })
            fadeOut.start()
        }, 2500)
    }

    private fun formatXp(xp: Int): String = when {
        xp >= 1000000 -> String.format("%.1fM", xp / 1000000.0)
        xp >= 100000 -> String.format("%.1fk", xp / 1000.0)
        else -> String.format("%,d", xp)
    }

    private fun formatSourceName(source: String): String = when (source) {
        "labor" -> "Labors"
        "focus" -> "Focus sessions"
        "time_track" -> "Time tracking"
        "reflection" -> "Reflections"
        else -> source.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    private fun formatCategoryName(category: String): String = when (category) {
        "general" -> "Tracked time"
        "deep_work" -> "Deep work"
        "social_media" -> "Social media"
        "youtube" -> "YouTube"
        else -> category.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
