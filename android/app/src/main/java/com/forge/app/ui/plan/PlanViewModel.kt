package com.forge.app.ui.plan

import androidx.lifecycle.*
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.PlanEntry
import kotlinx.coroutines.launch

class PlanViewModel(private val repository: ForgeRepository) : ViewModel() {

    private val today = repository.getTodayDate()

    val planEntries: LiveData<List<PlanEntry>> = repository.getEntriesForDate(today).asLiveData()

    private val battleResponses = listOf(
        "Roger that. Mission logged. Execute with precision.",
        "Target acquired. Stay locked in, soldier.",
        "Acknowledged. The battlefield favors the prepared.",
        "Command received. No retreat, no surrender.",
        "Intel registered. Time to move, warrior.",
        "Objective set. Failure is not in the protocol.",
        "Copy that. Your future self will thank you.",
        "Directive logged. Now go earn those XP.",
        "Battle plan updated. The grind never stops.",
        "Mission parameters set. Execute or be executed."
    )

    private val weeklyRules = """
        |[FORGE WEEKLY PROTOCOL]
        |================================
        |1. Complete all daily labors
        |2. Maintain streak - no zero days
        |3. Minimum 2hr deep work daily
        |4. Reflect every evening
        |5. Track all time categories
        |6. Push rank progression
        |7. Shield wall: no excuses
        |================================
        |STATUS: ACTIVE
    """.trimMargin()

    fun submitCommand(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val output = processCommand(trimmed)

            val entry = PlanEntry(
                input = trimmed,
                output = output,
                timestamp = System.currentTimeMillis(),
                date = today
            )
            repository.insertPlanEntry(entry)
        }
    }

    private suspend fun processCommand(input: String): String {
        val lower = input.lowercase()

        return when {
            lower == "status" -> {
                val stats = repository.getStats(today)
                val streak = repository.calculateStreak()
                """
                    |[FORGE STATUS REPORT]
                    |Date: $today
                    |Labors: ${stats?.laborsCompleted ?: 0}/${stats?.laborsTotal ?: 0}
                    |Focus: ${stats?.focusMinutes ?: 0} min
                    |Streak: $streak days
                    |Reflection: ${if (stats?.reflectionDone == true) "DONE" else "PENDING"}
                    |Status: ${if ((stats?.laborsCompleted ?: 0) > 0) "ENGAGED" else "STANDBY"}
                """.trimMargin()
            }

            lower == "rules" -> weeklyRules

            lower.startsWith("add ") -> {
                val task = input.substring(4).trim()
                if (task.isNotEmpty()) {
                    "Task added to battle plan: \"$task\"\nExecute with intent. No half measures."
                } else {
                    "Error: Empty task. Specify your mission objective."
                }
            }

            lower == "clear" -> {
                "Battle plan entries cleared for today.\nFresh slate. New ops. Let's move."
            }

            lower == "help" -> {
                """
                    |[FORGE TERMINAL COMMANDS]
                    |status  - View today's battle report
                    |rules   - Display weekly protocol
                    |add <t> - Add task to battle plan
                    |clear   - Clear today's entries
                    |help    - Show this command list
                    |
                    |Or type anything to log a directive.
                """.trimMargin()
            }

            else -> {
                battleResponses.random()
            }
        }
    }
}
