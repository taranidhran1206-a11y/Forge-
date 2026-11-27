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

    // Same model as the web app first. At busy times it stalls or answers 503, so it
    // gets a short wait before falling through to Flash models that were answering
    // when this was tested. Whichever model answers is tried first next time.
    private val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-flash-lite-latest")
    private const val FIRST_TRY_SECONDS = 12L
    private const val LATER_TRY_SECONDS = 25L

    @Volatile
    private var lastWorkingModel: String? = null

    class Failure(message: String) : Exception(message)

    data class Turn(val fromUser: Boolean, val text: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
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
        maxTokens: Int = 2000
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

        post(key, body, FIRST_TRY_SECONDS, LATER_TRY_SECONDS)
    }

    /** Text and pictures for [ask]. */
    sealed class Part {
        data class Text(val text: String) : Part()
        class Image(val bytes: ByteArray, val mime: String = "image/jpeg") : Part()
    }

    /**
     * One request with text and optional images. With [json] the model must answer with
     * a JSON value (no prose), which is what the food and critique features parse.
     */
    suspend fun ask(
        context: Context,
        system: String,
        parts: List<Part>,
        json: Boolean = false,
        maxTokens: Int = 2000,
        temperature: Double = 0.4
    ): String = withContext(Dispatchers.IO) {
        val key = activeKey(context)
        if (key.isEmpty()) throw Failure("no API key is set")
        val arr = JSONArray()
        parts.forEach { p ->
            when (p) {
                is Part.Text -> arr.put(JSONObject().put("text", p.text))
                is Part.Image -> arr.put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", p.mime)
                    .put("data", Base64.encodeToString(p.bytes, Base64.NO_WRAP))))
            }
        }
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", arr)))
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature)
                put("maxOutputTokens", maxTokens)
                if (json) put("responseMimeType", "application/json")
            })
        }.toString()
        val hasImage = parts.any { it is Part.Image }
        post(key, body, if (hasImage) 30L else FIRST_TRY_SECONDS, if (hasImage) 45L else LATER_TRY_SECONDS)
    }

    /** Pulls the first JSON object or array out of a reply, even if it came wrapped in ``` fences. */
    fun jsonOf(reply: String): String {
        val t = reply.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = t.indexOfFirst { it == '{' || it == '[' }
        val end = t.indexOfLast { it == '}' || it == ']' }
        return if (start >= 0 && end > start) t.substring(start, end + 1) else t
    }

    /** Tries each model in turn; returns the first non-empty reply. */
    private fun post(key: String, body: String, firstTry: Long, laterTry: Long): String {
        var lastError = "no response"
        val errors = mutableListOf<String>()
        val order = listOfNotNull(lastWorkingModel) + MODELS.filter { it != lastWorkingModel }
        order.forEachIndexed { attempt, model ->
            val call = client.newBuilder()
                .callTimeout(if (attempt == 0) firstTry else laterTry, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", key)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                call.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val reply = parseReply(text)
                        if (reply.isNotBlank()) {
                            lastWorkingModel = model
                            return reply
                        }
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
                lastError = if (e is java.io.InterruptedIOException) "$model timed out" else "no connection"
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
