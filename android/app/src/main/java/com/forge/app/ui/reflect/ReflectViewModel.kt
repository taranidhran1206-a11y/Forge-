package com.forge.app.ui.reflect

import androidx.lifecycle.*
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.DailyStats
import com.forge.app.data.model.Reflection
import com.forge.app.data.model.XpEvent
import kotlinx.coroutines.launch
import java.util.*

class ReflectViewModel(private val repository: ForgeRepository) : ViewModel() {

    private val today = repository.getTodayDate()

    val reflections: LiveData<List<Reflection>> = repository.getReflections().asLiveData()

    private val _todayReflection = MutableLiveData<Reflection?>(null)
    val todayReflection: LiveData<Reflection?> = _todayReflection

    private val _xpToastEvent = MutableLiveData<XpEvent?>(null)
    val xpToastEvent: LiveData<XpEvent?> = _xpToastEvent

    private val prompts = listOf(
        "What battle did you win today?",
        "Where did you waste ammo?",
        "What's your mission tomorrow?",
        "What fear did you face?",
        "What would your future self thank you for?",
        "What pattern are you stuck in?",
        "What distraction did you eliminate?",
        "Who did you help level up today?",
        "What skill did you sharpen?",
        "Where did you show discipline?",
        "What system broke down today?",
        "What did you learn the hard way?",
        "What's the one thing you must do tomorrow?",
        "Rate your focus today. Why?",
        "What's holding you back right now?"
    )

    val todayPrompt: LiveData<String> = MutableLiveData<String>().apply {
        // Use day of year to rotate through prompts
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        value = prompts[dayOfYear % prompts.size]
    }

    init {
        loadTodayReflection()
    }

    private fun loadTodayReflection() {
        viewModelScope.launch {
            _todayReflection.value = repository.getReflectionForDate(today)
        }
    }

    fun submitReflection(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val prompt = todayPrompt.value ?: prompts[0]

            val reflection = Reflection(
                text = text.trim(),
                aiResponse = null,
                promptUsed = prompt,
                date = today,
                timestamp = System.currentTimeMillis()
            )
            repository.insertReflection(reflection)
            _todayReflection.value = reflection

            // Award XP for reflection
            val xpAmount = 15
            repository.addXp(xpAmount, "reflection", "Daily reflection")

            _xpToastEvent.value = XpEvent(
                amount = xpAmount,
                source = "reflection",
                description = "Daily reflection",
                timestamp = System.currentTimeMillis(),
                date = today
            )

            // Update daily stats
            val stats = repository.getStats(today) ?: DailyStats(date = today)
            repository.upsertStats(stats.copy(reflectionDone = true))
        }
    }

    fun clearXpToast() {
        _xpToastEvent.value = null
    }
}
