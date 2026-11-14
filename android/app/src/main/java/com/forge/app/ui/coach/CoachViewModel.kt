package com.forge.app.ui.coach

import androidx.lifecycle.*
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.CoachMessage
import com.forge.app.data.model.RankSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

class CoachViewModel(private val repository: ForgeRepository) : ViewModel() {

    companion object {
        // Replace with your Gemini API key
        private const val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
        private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY"

        private const val SYSTEM_PROMPT = """You are Hercules, a tactical performance coach with a military/hacker mindset. You speak in short, direct sentences. You use metaphors of war, code, and systems. You push the user to be their best. Keep responses under 3 sentences unless asked for more. Use terminal-style language. Address the user as "soldier" or "warrior" occasionally. Never be soft — be direct but encouraging."""
    }

    val messages: LiveData<List<CoachMessage>> = repository.getMessages().asLiveData()

    val isTyping = MutableLiveData(false)

    private val _suggestionChips = MutableLiveData<List<String>>()
    val suggestionChips: LiveData<List<String>> = _suggestionChips

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

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
        if (text.isBlank()) return

        viewModelScope.launch {
            // Save user message
            val userMsg = CoachMessage(
                text = text.trim(),
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            repository.insertCoachMessage(userMsg)

            isTyping.value = true

            // Build context
            val context = buildContext()

            // Get AI response
            val response = try {
                callGeminiApi(text.trim(), context)
            } catch (e: Exception) {
                getFallbackResponse(text.trim())
            }

            isTyping.value = false

            // Save AI message
            val aiMsg = CoachMessage(
                text = response,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            repository.insertCoachMessage(aiMsg)

            updateSuggestionChips()
        }
    }

    private suspend fun buildContext(): String {
        val today = repository.getTodayDate()
        val stats = repository.getStats(today)
        val totalXp = repository.getTotalXpSync()
        val rank = RankSystem.getRank(totalXp)
        val streak = repository.calculateStreak()

        return """
            User context:
            - Rank: ${rank.title} (${totalXp} XP)
            - Streak: $streak days
            - Today's labors: ${stats?.laborsCompleted ?: 0}/${stats?.laborsTotal ?: 0} complete
            - Focus time today: ${stats?.focusMinutes ?: 0} minutes
            - Reflection done: ${stats?.reflectionDone ?: false}
            - Current time: ${Calendar.getInstance().get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", Calendar.getInstance().get(Calendar.MINUTE))}
        """.trimIndent()
    }

    private suspend fun callGeminiApi(prompt: String, context: String): String = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$SYSTEM_PROMPT\n\n$context\n\nUser: $prompt")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.8)
                put("maxOutputTokens", 256)
            })
        }

        val request = Request.Builder()
            .url(GEMINI_URL)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            throw Exception("API error: ${response.code}")
        }

        val json = JSONObject(body)
        val candidates = json.getJSONArray("candidates")
        if (candidates.length() == 0) throw Exception("No candidates")

        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        parts.getJSONObject(0).getString("text")
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
