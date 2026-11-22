package com.forge.app.ui.focus

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.forge.app.R
import com.forge.app.data.dao.CategoryTime
import com.forge.app.databinding.FragmentFocusBinding
import com.forge.app.focus.FocusService
import com.forge.app.focus.Distraction
import com.forge.app.focus.DistractionService
import com.forge.app.focus.FocusState
import com.forge.app.focus.NudgeStore
import com.forge.app.ui.ForgeViewModelFactory
import com.forge.app.ui.today.TodayViewModel
import com.forge.app.util.XpToastHelper

/** Focus timer, nudge tracker and today's time by category. */
class FocusFragment : Fragment() {

    private var _binding: FragmentFocusBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TodayViewModel

    private val handler = Handler(Looper.getMainLooper())
    private var trackRunnable: Runnable? = null
    private var selectedMinutes = 25
    private var categories: List<CategoryTime> = emptyList()
    private var categorySignature = ""

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
        _binding = FragmentFocusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val factory = ForgeViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[TodayViewModel::class.java]

        applyPanes()
        setupControls()
        setupDistractionWatch()
        observe()

        // A labor's Focus button on the Today screen hands its name over here
        FocusState.pendingLabel?.let { label ->
            FocusState.pendingLabel = null
            startSession(label)
        }
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

    private fun prefs() = requireContext().getSharedPreferences("forge_prefs", Context.MODE_PRIVATE)

    private fun chips() = listOf(binding.chip25 to 25, binding.chip45 to 45, binding.chip90 to 90)

    private fun setupControls() {
        selectedMinutes = prefs().getInt("focus_minutes", 25)
        FocusState.setIdleDuration(selectedMinutes)

        chips().forEach { (chip, minutes) ->
            chip.setOnClickListener {
                if (FocusState.live.value?.active == true) return@setOnClickListener
                setIdleMinutes(minutes)
            }
        }

        binding.btnFocusStart.setOnClickListener {
            val state = FocusState.live.value ?: FocusState.Snapshot()
            when {
                !state.active -> startSession("")
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
                setIdleMinutes((selectedMinutes + 5).coerceAtMost(240))
            }
        }

        binding.btnTrackToggle.setOnClickListener {
            if (viewModel.activeTimeTrack.value != null) {
                viewModel.stopTimeTrack()
            } else {
                binding.tvTrackStatus.text = "Starting…"
                viewModel.startTimeTrack("general")
            }
        }
    }

    private fun setIdleMinutes(minutes: Int) {
        selectedMinutes = minutes
        prefs().edit().putInt("focus_minutes", minutes).apply()
        FocusState.setIdleDuration(minutes)
    }

    private fun startSession(label: String) {
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
        FocusService.start(context, selectedMinutes, label)
    }

    private fun observe() {
        FocusState.live.observe(viewLifecycleOwner) { state ->
            render(state ?: FocusState.Snapshot())
        }

        FocusState.completedXp.observe(viewLifecycleOwner) { minutes ->
            if (minutes != null) {
                Toast.makeText(requireContext(), "Focus session complete · $minutes min", Toast.LENGTH_LONG).show()
                FocusState.completedXp.value = null
            }
        }

        viewModel.todayTimeByCategory.observe(viewLifecycleOwner) { list ->
            categories = list ?: emptyList()
            renderCategories(FocusState.live.value ?: FocusState.Snapshot())
        }

        viewModel.xpToastEvent.observe(viewLifecycleOwner) { event ->
            if (event != null) {
                activity?.let { XpToastHelper.showXpToast(it, event.amount, event.source) }
                viewModel.clearXpToast()
            }
        }

        viewModel.activeTimeTrack.observe(viewLifecycleOwner) { track ->
            if (track != null) {
                binding.tvTrackStatus.text = "Running"
                binding.tvTrackStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.trackIndicator.setImageResource(R.drawable.bg_status_dot_green)
                binding.btnTrackToggle.text = "Stop tracking"
                binding.btnTrackToggle.setBackgroundResource(R.drawable.bg_chip_on)
                startTrackTicker(track.startTime)
            } else {
                binding.tvTrackStatus.text = "Not running"
                binding.tvTrackStatus.setTextColor(Color.parseColor("#9E9E9E"))
                binding.trackIndicator.setImageResource(R.drawable.bg_status_dot_idle)
                binding.btnTrackToggle.text = "Start tracking"
                binding.btnTrackToggle.setBackgroundResource(R.drawable.bg_chip_neutral)
                stopTrackTicker()
            }
        }
    }

    private fun render(state: FocusState.Snapshot) {
        val remaining = state.remainingSeconds.coerceAtLeast(0)
        val total = state.totalSeconds.coerceAtLeast(1)
        binding.tvFocusTime.text = String.format("%02d:%02d", remaining / 60, remaining % 60)
        binding.tvFocusOf.text = "of ${state.totalSeconds / 60} minutes"
        binding.pbFocusRing.progress =
            if (state.active) ((total - remaining) * 1000L / total).toInt() else 0

        val status = when {
            !state.active -> "Ready"
            state.running -> "Running"
            else -> "Paused"
        }
        binding.tvFocusLabel.text = if (state.active && state.running) "Remaining" else status
        binding.tvFocusChip.text = status
        binding.ivFocusDot.setImageResource(
            if (state.active && state.running) R.drawable.bg_status_dot_green else R.drawable.bg_status_dot_idle
        )
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
            chips().forEach { (chip, m) ->
                chip.setBackgroundResource(
                    if (m == minutes) R.drawable.bg_chip_on else R.drawable.bg_chip_neutral
                )
            }
        }
        renderNudges(state)
        renderCategories(state)
    }

    private fun renderNudges(state: FocusState.Snapshot) {
        val day = NudgeStore.today(requireContext())
        // Only worth showing once there's a focus session today.
        binding.cardNudges.visibility = if (state.active || day.sessions > 0) View.VISIBLE else View.GONE
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

        val signature = day.log.joinToString("|")
        if (binding.llNudgeLog.tag != signature) {
            binding.llNudgeLog.tag = signature
            binding.llNudgeLog.removeAllViews()
            val size = resources.getDimension(R.dimen.t_small)
            day.log.take(5).forEach { line ->
                binding.llNudgeLog.addView(TextView(requireContext()).apply {
                    text = line
                    setTextColor(Color.parseColor("#C4C4C4"))
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size)
                    setPadding(0, 6, 0, 6)
                })
            }
        }
    }

    private fun categoryName(key: String): String = when (key) {
        "general" -> "Tracked time"
        "deep_work" -> "Deep work"
        "social_media" -> "Social media"
        "youtube" -> "YouTube"
        else -> key.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    private fun minutesText(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60}h ${String.format("%02d", minutes % 60)}m" else "${minutes}m"

    private fun renderCategories(state: FocusState.Snapshot) {
        val day = NudgeStore.today(requireContext())
        val focusMinutes = (day.focusSeconds + if (state.active) state.elapsedSeconds else 0) / 60
        val rows = mutableListOf<Pair<String, Int>>()
        if (focusMinutes > 0) rows.add("Focus sessions" to focusMinutes)
        categories.filter { it.minutes > 0 }.forEach { rows.add(categoryName(it.category) to it.minutes) }

        val signature = rows.joinToString("|") { "${it.first}:${it.second}" }
        if (signature == categorySignature && binding.llCategories.childCount == rows.size) return
        categorySignature = signature

        val totalMinutes = rows.sumOf { it.second }
        val longest = rows.maxOfOrNull { it.second } ?: 1
        binding.tvCatTotal.text = minutesText(totalMinutes)
        binding.tvCatEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        binding.cardCategories.visibility = View.GONE // the time tracker card covers today's time now
        binding.llCategories.removeAllViews()
        val bars = listOf(R.drawable.bg_xp_progress, R.drawable.bg_bar_rank, R.drawable.bg_bar_green)
        rows.sortedByDescending { it.second }.forEachIndexed { index, (name, minutes) ->
            val row = layoutInflater.inflate(R.layout.item_bar_row, binding.llCategories, false)
            row.findViewById<TextView>(R.id.tv_bar_name).text = name
            row.findViewById<TextView>(R.id.tv_bar_value).text = minutesText(minutes)
            row.findViewById<ProgressBar>(R.id.pb_bar).apply {
                progressDrawable = ContextCompat.getDrawable(requireContext(), bars[index % bars.size])
                max = longest
                progress = minutes
            }
            binding.llCategories.addView(row)
        }
    }

    private fun startTrackTicker(startTime: Long) {
        stopTrackTicker(resetText = false)
        trackRunnable = object : Runnable {
            override fun run() {
                if (_binding == null) return
                val seconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                binding.tvTrackDuration.text =
                    String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(trackRunnable!!)
    }

    private fun stopTrackTicker(resetText: Boolean = true) {
        trackRunnable?.let { handler.removeCallbacks(it) }
        trackRunnable = null
        if (resetText && _binding != null) binding.tvTrackDuration.text = "00:00:00"
    }

    // --- Distraction watch ---

    private fun setupDistractionWatch() {
        val context = requireContext()
        binding.btnDistractionToggle.setOnClickListener {
            if (Distraction.isEnabled(context)) {
                Distraction.setEnabled(context, false)
                DistractionService.stop(context)
            } else {
                Distraction.setEnabled(context, true)
                if (!Distraction.hasUsageAccess(context)) openUsageAccess()
                else DistractionService.start(context)
                askNotificationsIfNeeded()
            }
            renderDistraction()
        }
        binding.rowSetupUsage.setOnClickListener { openUsageAccess() }
        binding.rowSetupNotifications.setOnClickListener { openNotificationSettings() }
        binding.rowSetupBattery.setOnClickListener { askBatteryUnrestricted() }
        binding.btnChooseApps.setOnClickListener { showAppPicker() }
        binding.tvDistractionSettings.setOnClickListener {
            val open = binding.llDistractionSettings.visibility != View.VISIBLE
            binding.llDistractionSettings.visibility = if (open) View.VISIBLE else View.GONE
            binding.tvDistractionSettings.text = if (open) "Nudge settings  ˅" else "Nudge settings  ›"
        }
        listOf(
            binding.chipNudge1 to 1, binding.chipNudge3 to 3,
            binding.chipNudge5 to 5, binding.chipNudge10 to 10
        ).forEach { (chip, minutes) ->
            chip.setOnClickListener {
                Distraction.setThresholdMinutes(context, minutes)
                renderDistraction()
            }
        }
        Distraction.live.observe(viewLifecycleOwner) { renderDistraction() }
    }

    override fun onResume() {
        super.onResume()
        // Permissions may have just been granted in Settings
        val context = requireContext()
        if (Distraction.isEnabled(context)) DistractionService.start(context)
        renderDistraction()
    }

    private fun notificationsAllowed(): Boolean =
        androidx.core.app.NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()

    private fun askNotificationsIfNeeded() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openUsageAccess() {
        Toast.makeText(requireContext(), "Find Forge in the list and allow usage access", Toast.LENGTH_LONG).show()
        val packageUri = android.net.Uri.parse("package:${requireContext().packageName}")
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun openNotificationSettings() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        } catch (_: Exception) {
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun askBatteryUnrestricted() {
        val context = requireContext()
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${context.packageName}")))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun showAppPicker() {
        val context = requireContext()
        val apps = Distraction.launcherApps(context)
        if (apps.isEmpty()) return
        val checked = BooleanArray(apps.size) { i -> Distraction.isDistracting(context, apps[i].first) }
        AlertDialog.Builder(context, R.style.ForgeDialogTheme)
            .setTitle("Distracting apps")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                Distraction.setChosenApps(context, apps.indices.filter { checked[it] }.map { apps[it].first }.toSet())
                renderDistraction()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderDistraction() {
        val b = _binding ?: return
        val context = requireContext()
        val enabled = Distraction.isEnabled(context)
        val usage = Distraction.hasUsageAccess(context)
        val notify = notificationsAllowed()
        val battery = Distraction.isBatteryUnrestricted(context)
        val running = enabled && usage

        b.btnDistractionToggle.text = if (enabled) "On" else "Turn on"
        b.btnDistractionToggle.setBackgroundResource(if (enabled) R.drawable.bg_chip_on else R.drawable.bg_chip_neutral)
        b.ivDistractionEye.setColorFilter(Color.parseColor(if (running) "#4CAF50" else "#9E9E9E"))
        b.rowSetupUsage.visibility = if (enabled && !usage) View.VISIBLE else View.GONE
        b.rowSetupNotifications.visibility = if (enabled && !notify) View.VISIBLE else View.GONE
        b.rowSetupBattery.visibility = if (enabled && !battery) View.VISIBLE else View.GONE

        val threshold = Distraction.thresholdMinutes(context)
        b.tvDistractionStatus.text = when {
            !enabled -> "Nudges you when you stay in apps like Instagram or YouTube"
            !usage -> "Needs usage access to see which app is open"
            else -> "Tracking all day · nudges after $threshold min in one app"
        }
        listOf(b.chipNudge1 to 1, b.chipNudge3 to 3, b.chipNudge5 to 5, b.chipNudge10 to 10).forEach { (chip, m) ->
            chip.setBackgroundResource(if (m == threshold) R.drawable.bg_chip_on else R.drawable.bg_chip_neutral)
        }

        val today = Distraction.today(context)
        b.tvDistractedTotal.text = Distraction.minutesText(today.totalSeconds)
        b.tvDistractionNudges.text = today.nudges.toString()
        val top = today.secondsByApp.entries.sortedByDescending { it.value }.take(4)
        val signature = top.joinToString("|") { "${it.key}:${it.value / 60}" }
        if (b.llDistractionApps.tag != signature) {
            b.llDistractionApps.tag = signature
            b.llDistractionApps.removeAllViews()
            val longest = top.firstOrNull()?.value ?: 1
            top.forEach { (pkg, seconds) ->
                val row = layoutInflater.inflate(R.layout.item_bar_row, b.llDistractionApps, false)
                row.findViewById<TextView>(R.id.tv_bar_name).text = Distraction.label(context, pkg)
                row.findViewById<TextView>(R.id.tv_bar_value).text = Distraction.minutesText(seconds)
                row.findViewById<ProgressBar>(R.id.pb_bar).apply {
                    progressDrawable = ContextCompat.getDrawable(context, R.drawable.bg_xp_progress)
                    max = longest.coerceAtLeast(1)
                    progress = seconds
                }
                b.llDistractionApps.addView(row)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTrackTicker()
        _binding = null
    }
}
