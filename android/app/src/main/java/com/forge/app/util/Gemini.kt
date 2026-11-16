package com.forge.app.util

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * One place for every Gemini call (coach replies, reflection insights).
 * The default key is the one the Forge web app already ships with; a key
 * saved in the app's settings overrides it.
 */
object Gemini {

    private const val PREFS = "forge_prefs"
    private const val PREF_KEY = "gemini_key"
    private const val DEFAULT_KEY_ENCODED = "QVEuQWI4Uk42SUlxcW9KcWd4SHNQUzdPUWVCSWxJS2JrdWpteVdFVHFlLXVnUXFYTVRqNWc="

    // Same model as the web app first, then older ones in case it is unavailable
    private val MODELS = listOf("gemini-3.8-flash", "gemini-2.5-flash")

    class Failure(message: String) : Exception(message)

    data class Turn(val fromUser: Boolean, val text: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun savedKey(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_KEY, "")?.trim() ?: ""

    fun saveKey(context: Context, key: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, key.trim()).apply()
    }

    private fun activeKey(context: Context): String {
        val saved = savedKey(context)
        if (saved.isNotEmpty()) return saved
        return try {
            String(Base64.decode(DEFAULT_KEY_ENCODED, Base64.DEFAULT)).trim()
        } catch (_: Exception) {
            ""
        }
    }

    /** Returns the model's reply, or throws [Failure] with a reason a person can read. */
    suspend fun generate(
        context: Context,
        system: String,
        turns: List<Turn>,
        maxTokens: Int = 500
    ): String = withContext(Dispatchers.IO) {
        val key = activeKey(context)
        if (key.isEmpty()) throw Failure("no API key is set")

        val contents = JSONArray()
        turns.filter { it.text.isNotBlank() }.forEach { turn ->
            contents.put(JSONObject().apply {
                put("role", if (turn.fromUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
            })
        }
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.9)
                put("maxOutputTokens", maxTokens)
                put("topP", 0.95)
            })
        }.toString()

        var lastError = "no response"
        val errors = mutableListOf<String>()
        for (model in MODELS) {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", key)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val reply = parseReply(text)
                        if (reply.isNotBlank()) return@withContext reply
                        lastError = "the model returned an empty reply"
                    } else {
                        val message = try {
                            JSONObject(text).getJSONObject("error").getString("message")
                        } catch (_: Exception) {
                            ""
                        }
                        lastError = "error ${response.code}" + if (message.isNotBlank()) ": ${message.take(140)}" else ""
                        errors.add("$model $lastError")
                        // A bad key or blocked request fails the same way on every model
                        if (response.code == 400 || response.code == 401 || response.code == 403) {
                            throw Failure(lastError)
                        }
                    }
                }
            } catch (failure: Failure) {
                throw failure
            } catch (e: Exception) {
                lastError = "no connection (${e.javaClass.simpleName})"
                errors.add(lastError)
            }
        }
        // The first model is the one that matters; later ones are only fallbacks
        throw Failure(errors.firstOrNull() ?: lastError)
    }

    private fun parseReply(json: String): String {
        return try {
            val candidates = JSONObject(json).optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return ""
            val out = StringBuilder()
            for (i in 0 until parts.length()) out.append(parts.getJSONObject(i).optString("text", ""))
            out.toString().trim()
        } catch (_: Exception) {
            ""
        }
    }
}
