package com.forge.app.ui.today

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.forge.app.R
import com.forge.app.data.model.Labor
import com.forge.app.databinding.FragmentTodayBinding
import com.forge.app.focus.FocusState
import com.forge.app.ui.ForgeViewModelFactory
import com.forge.app.ui.adapters.LaborAdapter
import com.forge.app.util.WidgetHelper
import com.forge.app.util.XpToastHelper
import com.google.android.material.navigation.NavigationBarView
import java.text.SimpleDateFormat
import java.util.*

class TodayFragment : Fragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TodayViewModel
    private lateinit var laborAdapter: LaborAdapter

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
        binding.tvDate.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
        renderWeek(emptyList())
        setupLaborsRecycler()
        setupShieldWall()

        binding.btnAddLabor.setOnClickListener { showLaborDialog(null) }
        binding.cardFocusMini.setOnClickListener { openFocusTab() }
        binding.cardGymLink.setOnClickListener {
            try {
                findNavController().navigate(R.id.nav_gym)
            } catch (_: Exception) {
            }
        }
        com.forge.app.gym.GymDatabase.get(requireContext()).gymDao().activeWorkout().asLiveData()
            .observe(viewLifecycleOwner) { workout ->
                binding.tvGymLink.text = if (workout != null) "Workout in progress" else "Log today's workout"
            }
        binding.cardPlanLink.setOnClickListener {
            try {
                findNavController().navigate(R.id.nav_plan)
            } catch (_: Exception) {
            }
        }
        binding.cardFuelLink.setOnClickListener {
            try {
                findNavController().navigate(R.id.nav_fuel)
            } catch (_: Exception) {
            }
        }
        binding.cardVerdictLink.setOnClickListener {
            com.forge.app.ui.verdict.VerdictUi.open(requireContext(), viewLifecycleOwner.lifecycleScope) { refreshLinkCards() }
        }
        binding.cardAlarmLink.setOnClickListener {
            com.forge.app.alarm.AlarmUi.open(requireContext(), viewLifecycleOwner.lifecycleScope) { refreshLinkCards() }
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        refreshLinkCards()
    }

    /** The one-line status on the Fuel, Verdict and Alarm cards. */
    private fun refreshLinkCards() {
        val b = _binding ?: return
        val c = requireContext().applicationContext
        b.tvVerdictLink.text = com.forge.app.ui.verdict.VerdictUi.cardLine(c)
        b.tvAlarmLink.text = com.forge.app.alarm.AlarmUi.cardLine(c)
        viewLifecycleOwner.lifecycleScope.launch {
            val left = com.forge.app.ui.fuel.FuelFragment.todaySummary(c)
            val bb = _binding ?: return@launch
            bb.tvFuelLink.text = when {
                left == null -> "Snap a meal to log calories"
                left.first >= 0 -> "%,d kcal left · %dg protein to go".format(left.first, maxOf(0, left.second))
                else -> "%,d kcal over · %dg protein to go".format(-left.first, maxOf(0, left.second))
            }
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

    private fun openFocusTab() {
        activity?.findViewById<NavigationBarView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_focus
    }

    /** Hand the labor over to the Focus screen, which starts the session. */
    private fun startFocusFor(label: String) {
        if (FocusState.live.value?.active == true) {
            Toast.makeText(requireContext(), "A focus session is already running.", Toast.LENGTH_SHORT).show()
        } else {
            FocusState.pendingLabel = label
        }
        openFocusTab()
    }

    /** Week strip, Monday first. [days] marks which days had a labor completed. */
    private fun renderWeek(days: List<Boolean>) {
        val cells = listOf(
            binding.dayCell0, binding.dayCell1, binding.dayCell2, binding.dayCell3,
            binding.dayCell4, binding.dayCell5, binding.dayCell6
        )
        val letters = listOf(
            binding.dayLetter0, binding.dayLetter1, binding.dayLetter2, binding.dayLetter3,
            binding.dayLetter4, binding.dayLetter5, binding.dayLetter6
        )
        val rings = listOf(
            binding.dayRing0, binding.dayRing1, binding.dayRing2, binding.dayRing3,
            binding.dayRing4, binding.dayRing5, binding.dayRing6
        )
        // Calendar: Sunday = 1 ... Saturday = 7; strip index: Monday = 0 ... Sunday = 6
        val todayIndex = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
        for (i in 0..6) {
            val isToday = i == todayIndex
            val active = days.getOrNull(i) == true
            if (isToday) cells[i].setBackgroundResource(R.drawable.bg_day_today_cell)
            else cells[i].background = null
            letters[i].setTextColor(Color.parseColor(if (isToday) "#F5F5F5" else "#9E9E9E"))
            rings[i].setImageResource(
                when {
                    active && !isToday -> R.drawable.ic_check_done
                    isToday -> R.drawable.bg_day_dot_today
                    else -> R.drawable.bg_day_dot
                }
            )
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

    private fun dialogInput(hintText: String, value: String, marginTop: Int): EditText {
        return EditText(requireContext()).apply {
            hint = hintText
            setText(value)
            setTextColor(Color.parseColor("#F5F5F5"))
            setHintTextColor(Color.parseColor("#9E9E9E"))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(36, 28, 36, 28)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = marginTop
            layoutParams = params
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
            // Keep the rank widget current as XP changes
            val ctx = requireContext().applicationContext
            val rank = com.forge.app.data.model.RankSystem.getRank(xp)
            WidgetHelper.writeRankData(
                ctx, rank.title, xp, com.forge.app.data.model.RankSystem.getProgress(xp), rank.colorHex
            )
            com.forge.app.widget.RankWidget.triggerUpdate(ctx)
        }

        viewModel.streakCount.observe(viewLifecycleOwner) { streak ->
            binding.tvStreakCount.text = "$streak"
            val ctx = requireContext().applicationContext
            val labors = viewModel.todayLabors.value ?: emptyList()
            WidgetHelper.writeStreakData(ctx, streak, labors.count { it.done }, labors.size)
            com.forge.app.ForgeStreakWidgetProvider.triggerUpdate(ctx)
        }

        FocusState.live.observe(viewLifecycleOwner) { snapshot ->
            val state = snapshot ?: FocusState.Snapshot()
            val remaining = state.remainingSeconds.coerceAtLeast(0)
            binding.tvMiniTime.text = String.format("%02d:%02d", remaining / 60, remaining % 60)
            binding.tvMiniState.text = when {
                !state.active -> "Ready when you are"
                state.running && state.label.isNotBlank() -> state.label
                state.running -> "Session running"
                else -> "Paused"
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
            renderWeek(days ?: emptyList())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
