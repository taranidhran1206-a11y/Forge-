package com.forge.app.ui.today

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.forge.app.R
import com.forge.app.data.model.Labor
import com.forge.app.databinding.FragmentTodayBinding
import com.forge.app.focus.FocusService
import com.forge.app.focus.FocusState
import com.forge.app.focus.NudgeStore
import com.forge.app.ui.ForgeViewModelFactory
import com.forge.app.ui.adapters.LaborAdapter
import com.forge.app.util.WidgetHelper
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
    private var trackStartTime: Long = 0L

    private var selectedMinutes = 25
    private var pendingLabel = ""

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && context != null) {
                Toast.makeText(
                    requireContext(),
                    "Notifications are off, so nudges will only show inside Forge.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

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

        applyPanes()
        setupDate()
        setupDayDots()
        setupLaborsRecycler()
        setupAddLaborButton()
        setupTimeTracker()
        setupFocus()
        setupShieldWall()
        observeViewModel()
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

    private fun setupDate() {
        val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        binding.tvDate.text = dateFormat.format(Date())
    }

    private fun dayDots() = listOf(
        binding.dotSun, binding.dotMon, binding.dotTue,
        binding.dotWed, binding.dotThu, binding.dotFri, binding.dotSat
    )

    private fun setupDayDots() {
        val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1 = Sunday
        dayDots().forEachIndexed { index, dot ->
            if (index + 1 == todayDow) {
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
            onLaborLongClick = { labor -> showLaborOptionsDialog(labor) },
            onFocusClick = { labor -> startFocusFor(labor.name) }
        )
        binding.rvLabors.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = laborAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupAddLaborButton() {
        binding.btnAddLabor.setOnClickListener { showLaborDialog(null) }
    }

    private fun dialogInput(hintText: String, value: String, topMargin: Int): EditText {
        val context = requireContext()
        return EditText(context).apply {
            hint = hintText
            setText(value)
            setTextColor(Color.parseColor("#F5F5F5"))
            setHintTextColor(Color.parseColor("#9E9E9E"))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(36, 28, 36, 28)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { this.topMargin = topMargin }
        }
    }

    /** Add when [labor] is null, edit otherwise. */
    private fun showLaborDialog(labor: Labor?) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val nameInput = dialogInput("Labor name", labor?.name ?: "", 0)
        val descInput = dialogInput("Description (optional)", labor?.description ?: "", 20)
        layout.addView(nameInput)
        layout.addView(descInput)

        AlertDialog.Builder(context, R.style.TerminalDialogTheme)
            .setTitle(if (labor == null) "Add labor" else "Edit labor")
            .setView(layout)
            .setPositiveButton(if (labor == null) "Add" else "Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (labor == null) viewModel.addLabor(name, desc)
                    else viewModel.editLabor(labor, name, desc)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLaborOptionsDialog(labor: Labor) {
        val options = arrayOf("Start focus session", "Edit", "Delete")

        AlertDialog.Builder(requireContext(), R.style.TerminalDialogTheme)
            .setTitle(labor.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startFocusFor(labor.name)
                    1 -> showLaborDialog(labor)
                    2 -> AlertDialog.Builder(requireContext(), R.style.TerminalDialogTheme)
                        .setTitle("Delete labor")
                        .setMessage("Remove \"${labor.name}\"?")
                        .setPositiveButton("Delete") { _, _ -> viewModel.deleteLabor(labor) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .show()
    }

    // --- Time tracker ---

    private fun setupTimeTracker() {
        binding.btnTrackToggle.setOnClickListener {
            if (viewModel.activeTimeTrack.value != null) {
                viewModel.stopTimeTrack()
            } else {
                binding.tvTrackCategory.text = "Starting…"
                viewModel.startTimeTrack("general")
            }
        }
    }

    private fun startTimeTrackUpdater(startTime: Long) {
        stopTimeTrackUpdater(resetText = false)
        trackStartTime = startTime
        timeTrackRunnable = object : Runnable {
            override fun run() {
                if (_binding == null) return
                val totalSeconds = ((System.currentTimeMillis() - trackStartTime) / 1000).toInt()
                binding.tvTrackDuration.text = String.format(
                    "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60
                )
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

    // --- Focus session and nudge tracker ---

    private fun focusPrefs() =
        requireContext().getSharedPreferences("forge_prefs", Context.MODE_PRIVATE)

    private fun setupFocus() {
        selectedMinutes = focusPrefs().getInt("focus_minutes", 25)
        FocusState.setIdleDuration(selectedMinutes)

        val chips = listOf(binding.chip25 to 25, binding.chip45 to 45, binding.chip90 to 90)
        chips.forEach { (chip, minutes) ->
            chip.setOnClickListener {
                if (FocusState.live.value?.active == true) return@setOnClickListener
                selectedMinutes = minutes
                focusPrefs().edit().putInt("focus_minutes", minutes).apply()
                FocusState.setIdleDuration(minutes)
            }
        }

        binding.btnFocusStart.setOnClickListener {
            val state = FocusState.live.value ?: FocusState.Snapshot()
            when {
                !state.active -> startFocusFor(pendingLabel)
                state.running -> FocusService.send(requireContext(), FocusService.ACTION_PAUSE)
                else -> FocusService.send(requireContext(), FocusService.ACTION_RESUME)
            }
        }

        binding.btnFocusStop.setOnClickListener {
            FocusService.send(requireContext(), FocusService.ACTION_STOP)
        }

        binding.btnFocusPlus.setOnClickListener {
            val state = FocusState.live.value ?: FocusState.Snapshot()
            if (state.active) {
                FocusService.send(requireContext(), FocusService.ACTION_ADD_FIVE)
            } else {
                selectedMinutes = (selectedMinutes + 5).coerceAtMost(240)
                focusPrefs().edit().putInt("focus_minutes", selectedMinutes).apply()
                FocusState.setIdleDuration(selectedMinutes)
            }
        }
    }

    private fun startFocusFor(label: String) {
        val context = requireContext()
        val state = FocusState.live.value
        if (state != null && state.active) {
            Toast.makeText(context, "A focus session is already running.", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        pendingLabel = label
        FocusService.start(context, selectedMinutes, label)
    }

    private fun renderFocus(state: FocusState.Snapshot) {
        val remaining = state.remainingSeconds.coerceAtLeast(0)
        binding.tvFocusTime.text = String.format("%02d:%02d", remaining / 60, remaining % 60)
        binding.tvFocusOf.text = "of ${state.totalSeconds / 60} minutes"
        val total = state.totalSeconds.coerceAtLeast(1)
        binding.pbFocusRing.progress =
            if (state.active) ((total - remaining) * 1000L / total).toInt() else 0

        binding.tvFocusLabel.text = when {
            !state.active -> "Ready"
            state.running -> "Remaining"
            else -> "Paused"
        }
        binding.tvFocusTitle.text = when {
            state.active && state.label.isNotBlank() -> state.label
            state.active -> "Focus session"
            else -> "Deep work block"
        }
        binding.btnFocusStart.setImageResource(
            if (state.active && state.running) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.btnFocusStop.alpha = if (state.active) 1f else 0.4f
        binding.llDurationChips.visibility = if (state.active) View.GONE else View.VISIBLE

        if (!state.active) {
            val minutes = state.totalSeconds / 60
            listOf(binding.chip25 to 25, binding.chip45 to 45, binding.chip90 to 90).forEach { (chip, m) ->
                chip.setBackgroundResource(
                    if (m == minutes) R.drawable.bg_chip_on else R.drawable.bg_chip_neutral
                )
            }
        }
        renderNudges(state)
    }

    private fun renderNudges(state: FocusState.Snapshot) {
        val day = NudgeStore.today(requireContext())
        val away = day.awaySeconds + state.currentAwaySeconds
        val focus = day.focusSeconds + if (state.active) state.elapsedSeconds else 0

        binding.tvNudgeCount.text = day.nudges.toString()
        binding.tvDriftCount.text = day.drifts.toString()
        binding.tvAwayTime.text = NudgeStore.formatDuration(away)
        binding.tvFocusScore.text =
            if (focus > 0) "${(100 - away * 100 / focus).coerceIn(0, 100)}%" else "–"

        if (state.active && state.running) {
            binding.tvNudgeStatus.text = "Shield wall is up"
            binding.tvNudgeSub.text = "Leave Forge for 15 seconds and you get a nudge"
            binding.ivNudgeShield.setColorFilter(Color.parseColor("#4CAF50"))
        } else {
            binding.tvNudgeStatus.text = "Nudge tracker"
            binding.tvNudgeSub.text =
                if (day.sessions > 0) "${day.sessions} session${if (day.sessions == 1) "" else "s"} today"
                else "Starts with your focus session"
            binding.ivNudgeShield.setColorFilter(Color.parseColor("#9E9E9E"))
        }

        // Rebuild the log only when it changed
        val signature = day.log.joinToString("|")
        if (binding.llNudgeLog.tag != signature) {
            binding.llNudgeLog.tag = signature
            binding.llNudgeLog.removeAllViews()
            day.log.take(5).forEach { line ->
                val row = TextView(requireContext()).apply {
                    text = line
                    setTextColor(Color.parseColor("#C4C4C4"))
                    textSize = 12f
                    setPadding(0, 6, 0, 6)
                }
                binding.llNudgeLog.addView(row)
            }
        }
    }

    private fun setupShieldWall() {
        val quotes = listOf(
            "The battlefield rewards those who show up.",
            "Discipline is choosing between what you want now and what you want most.",
            "Your enemies are comfort, distraction, and self-doubt.",
            "Every rep, every page, every line of code compounds.",
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

    private fun renderLaborSummary() {
        val labors = viewModel.todayLabors.value ?: emptyList()
        val done = labors.count { it.done }
        val total = labors.size
        binding.tvLaborCount.text = "$done/$total"
        binding.pbLabors.progress = if (total > 0) done * 100 / total else 0
        val earned = viewModel.todayXp.value ?: 0
        val open = labors.filter { !it.done }.sumOf { it.xpReward }
        binding.tvLaborSub.text = when {
            total == 0 -> "No labors yet. Add your first one below."
            done == total -> "All done. Nice work. $earned XP earned today."
            else -> "$earned XP earned today · $open XP still on the table"
        }
    }

    private fun observeViewModel() {
        viewModel.todayLabors.observe(viewLifecycleOwner) { labors ->
            laborAdapter.submitList(labors)
            renderLaborSummary()

            // Keep home screen widgets in step with the list
            val ctx = requireContext().applicationContext
            WidgetHelper.writeLaborsJson(ctx, labors)
            WidgetHelper.writeStreakData(
                ctx, viewModel.streakCount.value ?: 0, labors.count { it.done }, labors.size
            )
            com.forge.app.widget.TodayPlanWidget.triggerUpdate(ctx)
            com.forge.app.ForgeStreakWidgetProvider.triggerUpdate(ctx)
        }

        viewModel.todayXp.observe(viewLifecycleOwner) { renderLaborSummary() }

        viewModel.totalXp.observe(viewLifecycleOwner) { xp ->
            binding.tvXpAmount.text = "$xp XP"
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

        viewModel.streakCount.observe(viewLifecycleOwner) { streak ->
            binding.tvStreakCount.text = "$streak"
            val ctx = requireContext().applicationContext
            val labors = viewModel.todayLabors.value ?: emptyList()
            WidgetHelper.writeStreakData(ctx, streak, labors.count { it.done }, labors.size)
            com.forge.app.ForgeStreakWidgetProvider.triggerUpdate(ctx)
        }

        viewModel.activeTimeTrack.observe(viewLifecycleOwner) { track ->
            if (track != null) {
                binding.tvTrackCategory.text = "Tracking"
                binding.tvTrackStatus.text = "Active"
                binding.tvTrackStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.trackIndicator.setBackgroundResource(R.drawable.bg_status_dot_green)
                binding.btnTrackToggle.text = "Stop tracking"
                binding.btnTrackToggle.setBackgroundResource(R.drawable.bg_chip_on)
                startTimeTrackUpdater(track.startTime)
            } else {
                binding.tvTrackCategory.text = "Not tracking"
                binding.tvTrackStatus.text = "Tap to start"
                binding.tvTrackStatus.setTextColor(Color.parseColor("#9E9E9E"))
                binding.trackIndicator.setBackgroundResource(R.drawable.bg_status_dot_idle)
                binding.btnTrackToggle.text = "Start tracking"
                binding.btnTrackToggle.setBackgroundResource(R.drawable.bg_chip_neutral)
                stopTimeTrackUpdater()
            }
        }

        FocusState.live.observe(viewLifecycleOwner) { state ->
            renderFocus(state ?: FocusState.Snapshot())
        }

        FocusState.completedXp.observe(viewLifecycleOwner) { xp ->
            if (xp != null) {
                activity?.let { XpToastHelper.showXpToast(it, xp, "focus") }
                FocusState.completedXp.value = null
                pendingLabel = ""
            }
        }

        viewModel.xpToastEvent.observe(viewLifecycleOwner) { event ->
            if (event != null) {
                activity?.let { XpToastHelper.showXpToast(it, event.amount, event.source) }
                viewModel.clearXpToast()
            }
        }

        viewModel.rankUpEvent.observe(viewLifecycleOwner) { rank ->
            if (rank != null) {
                Toast.makeText(requireContext(), "Rank up: ${rank.title}", Toast.LENGTH_LONG).show()
                viewModel.clearRankUp()
            }
        }

        viewModel.weekDayActivity.observe(viewLifecycleOwner) { days ->
            val dots = dayDots()
            val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            days.forEachIndexed { index, active ->
                if (index >= dots.size) return@forEachIndexed
                if (index + 1 == todayDow) {
                    dots[index].setBackgroundResource(R.drawable.bg_day_dot_today)
                } else if (active) {
                    dots[index].setBackgroundResource(R.drawable.bg_day_dot_active)
                    dots[index].setTextColor(Color.parseColor("#0A0A0A"))
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
