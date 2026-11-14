package com.forge.app.ui.today

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.forge.app.R
import com.forge.app.data.model.Labor
import com.forge.app.data.model.RankSystem
import com.forge.app.databinding.FragmentTodayBinding
import com.forge.app.ui.ForgeViewModelFactory
import com.forge.app.ui.adapters.LaborAdapter
import com.forge.app.util.XpToastHelper
import java.text.SimpleDateFormat
import java.util.*

class TodayFragment : Fragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TodayViewModel
    private lateinit var laborAdapter: LaborAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var timeTrackRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTodayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = ForgeViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[TodayViewModel::class.java]

        setupDate()
        setupDayDots()
        setupLaborsRecycler()
        setupAddLaborButton()
        setupTimeTracker()
        setupFocusTimer()
        setupShieldWall()
        observeViewModel()
    }

    private fun setupDate() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        binding.tvDate.text = dateFormat.format(Date())
    }

    private fun setupDayDots() {
        val dayDots = listOf(
            binding.dotSun, binding.dotMon, binding.dotTue,
            binding.dotWed, binding.dotThu, binding.dotFri, binding.dotSat
        )
        val calendar = Calendar.getInstance()
        val todayDow = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sunday

        dayDots.forEachIndexed { index, dot ->
            val dayIndex = index + 1 // 1=Sun, 2=Mon, etc.
            if (dayIndex == todayDow) {
                dot.setBackgroundResource(R.drawable.bg_day_dot_today)
                dot.setTextColor(Color.WHITE)
            } else {
                dot.setBackgroundResource(R.drawable.bg_day_dot)
                dot.setTextColor(Color.parseColor("#9E9E9E"))
            }
        }
    }

    private fun setupLaborsRecycler() {
        laborAdapter = LaborAdapter(
            onLaborClick = { labor -> viewModel.toggleLabor(labor) },
            onLaborLongClick = { labor -> showLaborOptionsDialog(labor) }
        )
        binding.rvLabors.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = laborAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupAddLaborButton() {
        binding.btnAddLabor.setOnClickListener {
            showAddLaborDialog()
        }
    }

    private fun showAddLaborDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val nameInput = EditText(context).apply {
            hint = "Labor name"
            setTextColor(Color.parseColor("#F5F5F5"))
            setHintTextColor(Color.parseColor("#555555"))
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundResource(R.drawable.bg_input)
            setPadding(24, 16, 24, 16)
        }
        layout.addView(nameInput)

        val descInput = EditText(context).apply {
            hint = "Description (optional)"
            setTextColor(Color.parseColor("#F5F5F5"))
            setHintTextColor(Color.parseColor("#555555"))
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundResource(R.drawable.bg_input)
            setPadding(24, 16, 24, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 16
            layoutParams = params
        }
        layout.addView(descInput)

        AlertDialog.Builder(context, R.style.TerminalDialogTheme)
            .setTitle("Add Labor")
            .setView(layout)
            .setPositiveButton("ADD") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.addLabor(name, descInput.text.toString().trim())
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showLaborOptionsDialog(labor: Labor) {
        val options = arrayOf("✏️ Edit", "🗑️ Delete")

        AlertDialog.Builder(requireContext(), R.style.TerminalDialogTheme)
            .setTitle(labor.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditLaborDialog(labor)
                    1 -> {
                        AlertDialog.Builder(requireContext(), R.style.TerminalDialogTheme)
                            .setTitle("Delete Labor")
                            .setMessage("Remove \"${labor.name}\"?")
                            .setPositiveButton("DELETE") { _, _ ->
                                viewModel.deleteLabor(labor)
                            }
                            .setNegativeButton("CANCEL", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showEditLaborDialog(labor: Labor) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val nameInput = EditText(context).apply {
            setText(labor.name)
            setTextColor(Color.parseColor("#F5F5F5"))
            setHintTextColor(Color.parseColor("#555555"))
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundResource(R.drawable.bg_input)
            setPadding(24, 16, 24, 16)
        }
        layout.addView(nameInput)

        val descInput = EditText(context).apply {
            setText(labor.description)
            setTextColor(Color.parseColor("#F5F5F5"))
            setHintTextColor(Color.parseColor("#555555"))
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundResource(R.drawable.bg_input)
            setPadding(24, 16, 24, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 16
            layoutParams = params
        }
        layout.addView(descInput)

        AlertDialog.Builder(context, R.style.TerminalDialogTheme)
            .setTitle("Edit Labor")
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.editLabor(labor, name, descInput.text.toString().trim())
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun setupTimeTracker() {
        // Single tap to start/stop — no category picker, Rize-style
        binding.btnTrackToggle.setOnClickListener {
            val activeTrack = viewModel.activeTimeTrack.value
            if (activeTrack != null) {
                viewModel.stopTimeTrack()
                // Observer handles UI reset via activeTimeTrack -> null
            } else {
                // Show immediate feedback while DB insert runs
                binding.tvTrackCategory.text = "Starting..."
                binding.btnTrackToggle.text = "■ STOP"
                binding.btnTrackToggle.setBackgroundResource(R.drawable.bg_button_primary)
                viewModel.startTimeTrack("general")
                // Observer will call startTimeTrackUpdater when _activeTimeTrack is set
            }
        }
    }

    private var trackStartTime: Long = 0L

    private fun startTimeTrackUpdater(startTime: Long) {
        stopTimeTrackUpdater(resetText = false)
        trackStartTime = startTime
        timeTrackRunnable = object : Runnable {
            override fun run() {
                if (_binding == null) return
                val elapsed = System.currentTimeMillis() - trackStartTime
                val totalSeconds = (elapsed / 1000).toInt()
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                binding.tvTrackDuration.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timeTrackRunnable!!)
    }

    private fun stopTimeTrackUpdater(resetText: Boolean = true) {
        timeTrackRunnable?.let { handler.removeCallbacks(it) }
        timeTrackRunnable = null
        if (resetText && _binding != null) {
            binding.tvTrackDuration.text = "00:00:00"
        }
    }

    private fun setupFocusTimer() {
        binding.btnFocusStart.setOnClickListener {
            val isRunning = viewModel.focusRunning.value ?: false
            if (isRunning) {
                viewModel.pauseFocus()
            } else {
                viewModel.startFocus()
            }
        }

        binding.btnFocusStop.setOnClickListener {
            viewModel.resetFocus()
        }
    }

    private fun setupShieldWall() {
        val quotes = listOf(
            "The battlefield rewards those who show up.",
            "Discipline is choosing between what you want now and what you want most.",
            "Your enemies are comfort, distraction, and self-doubt.",
            "Every rep, every page, every line of code — it compounds.",
            "The shield wall holds when each warrior does their part.",
            "You're not tired. You're uninspired. Find the mission.",
            "Pain is temporary. Quitting lasts forever.",
            "The forge burns hottest before the blade is ready.",
            "Victory belongs to the most tenacious.",
            "Today's discipline is tomorrow's freedom."
        )
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        binding.tvShieldText.text = quotes[dayOfYear % quotes.size]
    }

    private fun observeViewModel() {
        viewModel.todayLabors.observe(viewLifecycleOwner) { labors ->
            laborAdapter.submitList(labors)
            val done = labors.count { it.done }
            val total = labors.size
            binding.tvLaborCount.text = "$done/$total"
        }

        viewModel.totalXp.observe(viewLifecycleOwner) { xp ->
            binding.tvXpAmount.text = "${xp} XP"
        }

        viewModel.currentRank.observe(viewLifecycleOwner) { rank ->
            binding.tvRankName.text = rank.title
            try {
                binding.tvRankName.setTextColor(Color.parseColor(rank.colorHex))
            } catch (_: Exception) {
                binding.tvRankName.setTextColor(Color.WHITE)
            }
        }

        viewModel.rankProgress.observe(viewLifecycleOwner) { progress ->
            binding.pbXp.progress = (progress * 100).toInt()
        }

        viewModel.globalRankPercent.observe(viewLifecycleOwner) { percent ->
            binding.tvGlobalRank.text = "TOP ${percent}%"
        }

        viewModel.streakCount.observe(viewLifecycleOwner) { streak ->
            binding.tvStreakCount.text = "$streak"
        }

        viewModel.activeTimeTrack.observe(viewLifecycleOwner) { track ->
            if (track != null) {
                binding.tvTrackCategory.text = "Tracking"
                binding.tvTrackStatus.text = "Active"
                binding.tvTrackStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.trackIndicator.setBackgroundResource(R.drawable.bg_day_dot_today)
                binding.btnTrackToggle.text = "■ STOP"
                binding.btnTrackToggle.setBackgroundResource(R.drawable.bg_button_primary)
                startTimeTrackUpdater(track.startTime)
            } else {
                binding.tvTrackCategory.text = "Not tracking"
                binding.tvTrackStatus.text = "Tap to start"
                binding.tvTrackStatus.setTextColor(Color.parseColor("#9E9E9E"))
                binding.trackIndicator.setBackgroundResource(R.drawable.bg_day_dot)
                binding.btnTrackToggle.text = "▶ TRACK"
                binding.btnTrackToggle.setBackgroundResource(R.drawable.bg_button_outlined)
                stopTimeTrackUpdater()
            }
        }

        viewModel.focusTimeRemaining.observe(viewLifecycleOwner) { seconds ->
            val mins = seconds / 60
            val secs = seconds % 60
            binding.tvFocusTime.text = String.format("%02d:%02d", mins, secs)
        }

        viewModel.focusRunning.observe(viewLifecycleOwner) { running ->
            binding.btnFocusStart.setImageResource(if (running) R.drawable.ic_pause else R.drawable.ic_play)
        }

        viewModel.focusProgress.observe(viewLifecycleOwner) { progress ->
            binding.pbFocusRing.progress = (progress * 100).toInt()
        }

        viewModel.xpToastEvent.observe(viewLifecycleOwner) { event ->
            if (event != null) {
                activity?.let { XpToastHelper.showXpToast(it, event.amount, event.source) }
                viewModel.clearXpToast()
            }
        }

        viewModel.rankUpEvent.observe(viewLifecycleOwner) { rank ->
            if (rank != null) {
                Toast.makeText(
                    requireContext(),
                    "⚔️ RANK UP: ${rank.title}!",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearRankUp()
            }
        }

        viewModel.weekDayActivity.observe(viewLifecycleOwner) { days ->
            val dayDots = listOf(
                binding.dotSun, binding.dotMon, binding.dotTue,
                binding.dotWed, binding.dotThu, binding.dotFri, binding.dotSat
            )
            val calendar = Calendar.getInstance()
            val todayDow = calendar.get(Calendar.DAY_OF_WEEK)

            days.forEachIndexed { index, active ->
                val dayIndex = index + 1
                if (dayIndex == todayDow) {
                    dayDots[index].setBackgroundResource(R.drawable.bg_day_dot_today)
                } else if (active) {
                    dayDots[index].setBackgroundResource(R.drawable.bg_day_dot_active)
                    dayDots[index].setTextColor(Color.WHITE)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTimeTrackUpdater()
        _binding = null
    }
}
