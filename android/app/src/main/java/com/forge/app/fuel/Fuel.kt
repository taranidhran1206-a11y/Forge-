package com.forge.app.fuel

import android.content.Context
import com.forge.app.util.Gemini
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Goals, AI estimates and barcode lookups for Fuel. */
object Fuel {

    private const val PREFS = "forge_prefs"

    // ═════════════════════════ goals ═════════════════════════

    data class Goals(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int)

    data class Profile(
        val male: Boolean = true,
        val age: Int = 20,
        val heightCm: Int = 170,
        val weightKg: Double = 65.0,
        /** 0 = little, 1 = light (1-3 days), 2 = moderate (3-5 days), 3 = very active */
        val activity: Int = 2,
        /** -1 lose fat, 0 maintain, 1 build muscle */
        val aim: Int = 1
    )

    fun profile(c: Context): Profile {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Profile(
            p.getBoolean("fuel_male", true), p.getInt("fuel_age", 20), p.getInt("fuel_height", 170),
            p.getFloat("fuel_weight", 65f).toDouble(), p.getInt("fuel_activity", 2), p.getInt("fuel_aim", 1)
        )
    }

    fun hasProfile(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("fuel_kcal")

    fun saveProfile(c: Context, pr: Profile, goals: Goals) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("fuel_male", pr.male).putInt("fuel_age", pr.age).putInt("fuel_height", pr.heightCm)
            .putFloat("fuel_weight", pr.weightKg.toFloat()).putInt("fuel_activity", pr.activity).putInt("fuel_aim", pr.aim)
            .putInt("fuel_kcal", goals.kcal).putInt("fuel_protein", goals.protein)
            .putInt("fuel_carbs", goals.carbs).putInt("fuel_fat", goals.fat)
            .apply()
    }

    fun goals(c: Context): Goals {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val auto = suggest(profile(c))
        return Goals(p.getInt("fuel_kcal", auto.kcal), p.getInt("fuel_protein", auto.protein), p.getInt("fuel_carbs", auto.carbs), p.getInt("fuel_fat", auto.fat))
    }

    /**
     * Mifflin-St Jeor maintenance × activity, then a modest surplus or deficit.
     * Protein 1.8 g/kg for someone lifting, fat 25% of calories, carbs the rest.
     */
    fun suggest(p: Profile): Goals {
        val bmr = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age + if (p.male) 5 else -161
        val factor = listOf(1.2, 1.375, 1.55, 1.725)[p.activity.coerceIn(0, 3)]
        val maintenance = bmr * factor
        val floor = if (p.male) 1500.0 else 1200.0
        val kcal = when (p.aim) {
            -1 -> maxOf(floor, maintenance - 400)
            1 -> maintenance + 300
            else -> maintenance
        }
        val protein = (1.8 * p.weightKg).coerceIn(50.0, 220.0)
        val fat = kcal * 0.25 / 9
        val carbs = maxOf(0.0, (kcal - protein * 4 - fat * 9) / 4)
        return Goals(round(kcal, 10), protein.roundToInt(), carbs.roundToInt(), fat.roundToInt())
    }

    private fun round(v: Double, to: Int) = ((v / to).roundToInt() * to)

    fun mealFor(time: Long): String {
        val h = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 4..10 -> "breakfast"
            in 11..15 -> "lunch"
            in 18..22 -> "dinner"
            else -> "snack"
        }
    }

    val MEALS = listOf("breakfast", "lunch", "dinner", "snack")
    fun mealName(m: String) = when (m) { "breakfast" -> "Breakfast"; "lunch" -> "Lunch"; "dinner" -> "Dinner"; else -> "Snacks" }

    // ═════════════════════════ AI estimates ═════════════════════════

    private val SYSTEM = """
        You estimate nutrition for a calorie tracker used by a young man in Malaysia who lifts weights.
        Identify each food and drink, estimate the portion in grams from what you can see or what is described
        (plate size, cutlery, packaging), then give calories, protein, carbs, fat and fiber for each item using
        standard nutrition data. Know Malaysian and Asian dishes well (nasi lemak, roti canai, mee goreng, chicken rice,
        teh tarik, kopi, mamak food, kuih) as well as western food. Count cooking oil, sauces, sambal and sugar in drinks.
        Answer ONLY with JSON:
        {"name": short meal name, "items": [{"name": "...", "grams": number, "kcal": number, "protein": number,
          "carbs": number, "fat": number, "fiber": number}], "health_score": 1-10 (10 = great for a lifter's goals),
         "note": one short sentence on what's good or what to watch, "confidence": "high"|"medium"|"low"}
        If there is no food at all, answer {"name": "", "items": [], "note": "No food found", "confidence": "low"}.
    """.trimIndent()

    private fun parse(reply: String, source: String, photo: String): Estimate {
        val o = JSONObject(Gemini.jsonOf(reply))
        val arr = o.optJSONArray("items")
        val items = if (arr == null) mutableListOf() else (0 until arr.length()).map { FoodItem.fromJson(arr.getJSONObject(it)) }.toMutableList()
        return Estimate(
            name = o.optString("name", "").trim(),
            items = items,
            healthScore = o.optInt("health_score", 0).coerceIn(0, 10),
            note = o.optString("note", "").trim(),
            confidence = o.optString("confidence", ""),
            source = source,
            photo = photo
        )
    }

    suspend fun fromPhoto(c: Context, jpeg: ByteArray, photo: String, hint: String = ""): Estimate {
        val parts = mutableListOf<Gemini.Part>(Gemini.Part.Image(jpeg))
        parts.add(Gemini.Part.Text(if (hint.isBlank()) "What's in this meal?" else "What's in this meal? Extra detail from me: $hint"))
        return parse(Gemini.ask(c, SYSTEM, parts, json = true, temperature = 0.2, maxTokens = 1500), "photo", photo)
    }

    suspend fun fromText(c: Context, text: String): Estimate =
        parse(Gemini.ask(c, SYSTEM, listOf(Gemini.Part.Text("I ate: $text")), json = true, temperature = 0.2, maxTokens = 1500), "text", "")

    /** "Fix results": the earlier estimate plus what the person says was wrong. */
    suspend fun fix(c: Context, before: Estimate, correction: String, jpeg: ByteArray?): Estimate {
        val parts = mutableListOf<Gemini.Part>()
        jpeg?.let { parts.add(Gemini.Part.Image(it)) }
        val prev = JSONObject().put("name", before.name).put("items", org.json.JSONArray(FoodItem.json(before.items)))
        parts.add(Gemini.Part.Text("Your earlier estimate was: $prev\nCorrection from me: $correction\nGive the corrected estimate for the whole meal."))
        val out = parse(Gemini.ask(c, SYSTEM, parts, json = true, temperature = 0.2, maxTokens = 1500), before.source, before.photo)
        out.meal = before.meal
        return out
    }

    // ═════════════════════════ barcodes (Open Food Facts) ═════════════════════════

    data class Product(val name: String, val per100: FoodItem, val servingGrams: Double?)

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    suspend fun lookup(barcode: String): Product? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/${barcode.filter { it.isDigit() }}.json" +
                "?fields=product_name,brands,nutriments,serving_quantity,serving_size")
            .header("User-Agent", "Forge/2.2 (Android; personal tracker)")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            val o = JSONObject(r.body?.string() ?: return@withContext null)
            if (o.optInt("status", 0) != 1) return@withContext null
            val p = o.getJSONObject("product")
            val n = p.optJSONObject("nutriments") ?: JSONObject()
            fun g(k: String) = n.optDouble(k, 0.0).let { if (it.isNaN()) 0.0 else it }
            var kcal = g("energy-kcal_100g")
            if (kcal == 0.0 && g("energy_100g") > 0) kcal = g("energy_100g") / 4.184
            val brand = p.optString("brands", "").split(',').firstOrNull()?.trim().orEmpty()
            val name = listOf(brand, p.optString("product_name", "")).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Product $barcode" }
            val serving = p.optDouble("serving_quantity", 0.0).let { if (it.isNaN() || it <= 0) null else it }
            Product(name, FoodItem(name, 100.0, kcal, g("proteins_100g"), g("carbohydrates_100g"), g("fat_100g"), g("fiber_100g")), serving)
        }
    }
}
