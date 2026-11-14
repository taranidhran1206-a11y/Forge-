package com.forge.app.ui.progress

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.totalXp.observe(viewLifecycleOwner) { xp ->
            // Format XP nicely like video: 15,700 or 15.7k
            binding.tvXpCurrent.text = formatXp(xp)
        }

        viewModel.currentRank.observe(viewLifecycleOwner) { rank ->
            binding.tvRankTitle.text = rank.title
            binding.tvRankBadge.text = getRankEmoji(rank.title)
            binding.tvRankLevel.text = "LEVEL ${getRankLevel(rank.title)}"
            try {
                binding.tvRankTitle.setTextColor(Color.parseColor(rank.colorHex))
            } catch (_: Exception) {
                binding.tvRankTitle.setTextColor(Color.WHITE)
            }
        }

        viewModel.rankProgress.observe(viewLifecycleOwner) { progress ->
            binding.pbRankXp.progress = (progress * 100).toInt()
        }

        viewModel.xpToNextRank.observe(viewLifecycleOwner) { xpNeeded ->
            val nextRank = viewModel.nextRankName.value ?: "next rank"
            binding.tvXpNext.text = "${formatXp(xpNeeded)} to $nextRank"
        }

        viewModel.globalRankPercent.observe(viewLifecycleOwner) { percent ->
            binding.tvGlobalPercent.text = "Top $percent%"
        }

        viewModel.weeklyStats.observe(viewLifecycleOwner) { stats ->
            binding.llWeeklyStats.removeAllViews()
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

            stats.forEachIndexed { index, stat ->
                val dayView = layoutInflater.inflate(R.layout.item_xp_source, binding.llWeeklyStats, false)
                val tvName = dayView.findViewById<TextView>(R.id.tv_xp_source_name)
                val tvAmount = dayView.findViewById<TextView>(R.id.tv_xp_source_amount)
                val progressBar = dayView.findViewById<ProgressBar>(R.id.pb_xp_source_bar)

                val dayLabel = if (index < dayNames.size) dayNames[index] else "Day"
                tvName.text = dayLabel
                tvAmount.text = "${stat.totalXp} XP"
                val maxXp = stats.maxOfOrNull { it.totalXp } ?: 1
                progressBar.max = if (maxXp > 0) maxXp else 1
                progressBar.progress = stat.totalXp

                binding.llWeeklyStats.addView(dayView)
            }
        }

        viewModel.xpBySource.observe(viewLifecycleOwner) { sources ->
            binding.llXpBreakdown.removeAllViews()
            val totalXp = sources.sumOf { it.total }

            sources.forEach { source ->
                val itemView = layoutInflater.inflate(R.layout.item_xp_source, binding.llXpBreakdown, false)
                val tvName = itemView.findViewById<TextView>(R.id.tv_xp_source_name)
                val tvAmount = itemView.findViewById<TextView>(R.id.tv_xp_source_amount)
                val progressBar = itemView.findViewById<ProgressBar>(R.id.pb_xp_source_bar)

                tvName.text = source.source.replaceFirstChar { it.uppercase() }
                tvAmount.text = "${source.total} XP"
                progressBar.max = if (totalXp > 0) totalXp else 1
                progressBar.progress = source.total

                binding.llXpBreakdown.addView(itemView)
            }
        }

        viewModel.todayTimeByCategory.observe(viewLifecycleOwner) { categories ->
            binding.llTimeAllocation.removeAllViews()
            val totalMinutes = categories.sumOf { it.minutes }

            categories.forEach { cat ->
                val itemView = layoutInflater.inflate(R.layout.item_time_category, binding.llTimeAllocation, false)
                val tvCategory = itemView.findViewById<TextView>(R.id.tv_cat_name)
                val tvDuration = itemView.findViewById<TextView>(R.id.tv_cat_duration)
                val progressBar = itemView.findViewById<ProgressBar>(R.id.pb_cat_bar)

                tvCategory.text = formatCategoryName(cat.category)
                val hours = cat.minutes / 60
                val mins = cat.minutes % 60
                tvDuration.text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                progressBar.max = if (totalMinutes > 0) totalMinutes else 1
                progressBar.progress = cat.minutes

                binding.llTimeAllocation.addView(itemView)
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
        try {
            binding.tvLevelUpRank.setTextColor(Color.parseColor(rank.colorHex))
        } catch (_: Exception) {}

        val scaleX = ObjectAnimator.ofFloat(binding.tvLevelUpRank, View.SCALE_X, 0.3f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.tvLevelUpRank, View.SCALE_Y, 0.3f, 1.2f, 1f)
        val alpha = ObjectAnimator.ofFloat(binding.flLevelUpOverlay, View.ALPHA, 0f, 1f)

        val animSet = AnimatorSet()
        animSet.playTogether(scaleX, scaleY, alpha)
        animSet.duration = 800
        animSet.interpolator = AccelerateDecelerateInterpolator()
        animSet.start()

        binding.flLevelUpOverlay.postDelayed({
            val fadeOut = ObjectAnimator.ofFloat(binding.flLevelUpOverlay, View.ALPHA, 1f, 0f)
            fadeOut.duration = 500
            fadeOut.start()
            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    binding.flLevelUpOverlay.visibility = View.GONE
                }
            })
        }, 2500)
    }

    private fun formatXp(xp: Int): String {
        return when {
            xp >= 1000000 -> String.format("%.1fM", xp / 1000000.0)
            xp >= 10000 -> String.format("%.1fk", xp / 1000.0)
            xp >= 1000 -> String.format("%,d", xp)
            else -> "$xp"
        }
    }

    private fun getRankEmoji(rankTitle: String): String {
        return when (rankTitle.uppercase()) {
            "IRON" -> "⚔️"
            "BRONZE" -> "🥉"
            "SILVER" -> "🥈"
            "GOLD" -> "🥇"
            "PLATINUM" -> "💎"
            "DIAMOND" -> "💠"
            "TITAN" -> "✨"
            "MYTHIC" -> "🔮"
            "APEX" -> "🏆"
            "LEGEND" -> "👑"
            else -> "⚔️"
        }
    }

    private fun getRankLevel(rankTitle: String): Int {
        return when (rankTitle.uppercase()) {
            "IRON" -> 1
            "BRONZE" -> 2
            "SILVER" -> 3
            "GOLD" -> 4
            "PLATINUM" -> 5
            "DIAMOND" -> 6
            "TITAN" -> 7
            "MYTHIC" -> 8
            "APEX" -> 9
            "LEGEND" -> 10
            else -> 1
        }
    }

    private fun formatCategoryName(category: String): String {
        return when (category) {
            "deep_work" -> "🧠 Deep Work"
            "admin" -> "📧 Admin"
            "social_media" -> "📱 Social Media"
            "youtube" -> "▶️ YouTube"
            "exercise" -> "💪 Exercise"
            "rest" -> "😴 Rest"
            "learning" -> "📚 Learning"
            "other" -> "🔄 Other"
            else -> category
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
