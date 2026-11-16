package com.forge.app.ui.coach

import android.content.Context
import androidx.lifecycle.*
import com.forge.app.util.Gemini
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.CoachMessage
import com.forge.app.data.model.RankSystem
import kotlinx.coroutines.launch
import java.util.*

class CoachViewModel(
    private val repository: ForgeRepository,
    private val appContext: Context
) : ViewModel() {

    companion object {
        private const val SYSTEM_PROMPT = """You are Hercules, the performance coach inside the Forge discipline app. Be direct, short and practical. Use the user's real numbers from the context when they help. Keep replies under four sentences unless asked for more. Push the user, but never insult them."""
    }

    val messages: LiveData<List<CoachMessage>> = repository.getMessages().asLiveData()

    val isTyping = MutableLiveData(false)

    private val _suggestionChips = MutableLiveData<List<String>>()
    val suggestionChips: LiveData<List<String>> = _suggestionChips

    init {
        updateSuggestionChips()
    }

    private fun updateSuggestionChips() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        _suggestionChips.value = when {
            hour in 5..11 -> listOf("Plan my day", "Show my tasks", "Motivate me", "Morning routine")
            hour in 12..16 -> listOf("How am I doing?", "Focus tips", "Energy boost", "Midday check")
            hour in 17..21 -> listOf("Day review", "Tomorrow prep", "Wind down", "Reflect")
            else -> listOf("Can't sleep", "Late night grind", "Tomorrow's plan", "Quick motivation")
        }
    }

    fun sendMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return

        viewModelScope.launch {
            try {
                val history = messages.value ?: emptyList()
                repository.insertCoachMessage(
                    CoachMessage(text = clean, isUser = true, timestamp = System.currentTimeMillis())
                )
                isTyping.value = true

                val reply = try {
                    val context = buildContext()
                    val turns = history.takeLast(10).map { Gemini.Turn(it.isUser, it.text) } +
                        Gemini.Turn(true, clean)
                    // The API needs the conversation to start with the user
                    Gemini.generate(appContext, SYSTEM_PROMPT + "\n\n" + context, turns.dropWhile { !it.fromUser })
                } catch (e: Exception) {
                    "Coach is offline (${e.message ?: "unknown error"}). Until it is back: " +
                        getFallbackResponse(clean)
                }

                repository.insertCoachMessage(
                    CoachMessage(text = reply, isUser = false, timestamp = System.currentTimeMillis())
                )
            } catch (_: Exception) {
                // Never let a failed message take the app down
            } finally {
                isTyping.value = false
                updateSuggestionChips()
            }
        }
    }

    private suspend fun buildContext(): String {
        val today = repository.getTodayDate()
        val stats = repository.getStats(today)
        val totalXp = repository.getTotalXpSync()
        val rank = RankSystem.getRank(totalXp)
        val streak = repository.calculateStreak()
        val labors = repository.getLaborsForDateSync(today)
        val open = labors.filter { !it.done }.joinToString(", ") { it.name }.ifEmpty { "none" }

        return """
            User context:
            - Rank: ${rank.title} (${totalXp} XP)
            - Streak: $streak days
            - Today's labors: ${stats?.laborsCompleted ?: 0}/${stats?.laborsTotal ?: 0} complete
            - Labors still open: $open
            - Focus time today: ${stats?.focusMinutes ?: 0} minutes
            - Reflection done: ${stats?.reflectionDone ?: false}
            - Current time: ${Calendar.getInstance().get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", Calendar.getInstance().get(Calendar.MINUTE))}
        """.trimIndent()
    }

    private fun getFallbackResponse(input: String): String {
        val lower = input.lowercase()
        val responses = mapOf(
            "motivat" to "You didn't come this far to only come this far. Execute the next task. Now.",
            "focus" to "Lock in. Phone down. Timer on. One task, one mission. 25 minutes of war.",
            "tired" to "Fatigue is the mind's first line of defense against greatness. Push through or recharge smart — no middle ground.",
            "plan" to "Open your battle plan. List 3 targets. Assign XP values. Execute in order of impact.",
            "help" to "I'm your tactical AI. Ask me to plan your day, review your progress, or push you harder.",
            "review" to "Check your progress tab. Numbers don't lie, soldier. Where are the gaps?",
            "sleep" to "Rest is a weapon. Use it strategically. Set your alarm, prepare tomorrow's plan, then shut down.",
            "streak" to "Every day you show up, the compound interest of discipline grows. Don't break the chain.",
            "task" to "Break it down. What's the smallest next action? Do that first. Then the next. Momentum is everything.",
            "morning" to "Rise. Hydrate. Review your labors. Start the timer. First task within 10 minutes of waking."
        )

        for ((key, response) in responses) {
            if (lower.contains(key)) return response
        }

        return listOf(
            "Copy that, soldier. Let's break this down and attack it systematically.",
            "Every rep counts. Every task matters. Stay locked in.",
            "The grind doesn't care about your feelings. But I do — now let's channel that into action.",
            "You're here, asking questions. That's already more than most. Now execute.",
            "Your future self is watching. Make them proud."
        ).random()
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearCoachMessages()
        }
    }
}
