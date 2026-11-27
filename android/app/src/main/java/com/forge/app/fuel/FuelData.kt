package com.forge.app.fuel

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Fuel: what you ate, kept in its own database like the gym log. */
@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: Long = System.currentTimeMillis(),
    /** breakfast, lunch, dinner or snack */
    val meal: String,
    val name: String,
    val kcal: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    /** photo, text, barcode or manual */
    val source: String,
    /** the items as JSON: [{name, grams, kcal, protein, carbs, fat, fiber}] */
    val items: String = "[]",
    /** file name in files/fuel, or empty */
    val photo: String = "",
    val healthScore: Int = 0,
    val note: String = ""
)

data class DayTotal(val date: String, val kcal: Int, val protein: Double, val carbs: Double, val fat: Double)

@Dao
interface FuelDao {
    @Insert suspend fun insert(m: Meal): Long
    @Update suspend fun update(m: Meal)
    @Query("DELETE FROM meals WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM meals WHERE date = :date ORDER BY time ASC") fun day(date: String): Flow<List<Meal>>
    @Query("SELECT * FROM meals WHERE date = :date ORDER BY time ASC") suspend fun dayNow(date: String): List<Meal>
    @Query("SELECT * FROM meals WHERE id = :id") suspend fun get(id: Long): Meal?
    @Query(
        """SELECT date, SUM(kcal) AS kcal, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat
           FROM meals WHERE date >= :from GROUP BY date ORDER BY date DESC"""
    )
    fun totalsSince(from: String): Flow<List<DayTotal>>
    @Query(
        """SELECT date, SUM(kcal) AS kcal, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat
           FROM meals WHERE date >= :from GROUP BY date ORDER BY date DESC"""
    )
    suspend fun totalsSinceNow(from: String): List<DayTotal>
    /** Recent distinct foods for one-tap re-logging. */
    @Query("SELECT * FROM meals WHERE id IN (SELECT MAX(id) FROM meals GROUP BY name) ORDER BY time DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<Meal>
}

@Database(entities = [Meal::class], version = 1, exportSchema = false)
abstract class FuelDatabase : RoomDatabase() {
    abstract fun dao(): FuelDao

    companion object {
        @Volatile private var INSTANCE: FuelDatabase? = null
        fun get(context: Context): FuelDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, FuelDatabase::class.java, "forge_fuel")
                .build().also { INSTANCE = it }
        }
    }
}

/** One food in a meal. */
data class FoodItem(var name: String, var grams: Double, var kcal: Double, var protein: Double, var carbs: Double, var fat: Double, var fiber: Double = 0.0) {
    fun scaled(f: Double) = FoodItem(name, grams * f, kcal * f, protein * f, carbs * f, fat * f, fiber * f)

    fun toJson(): JSONObject = JSONObject().put("name", name).put("grams", r1(grams)).put("kcal", r1(kcal))
        .put("protein", r1(protein)).put("carbs", r1(carbs)).put("fat", r1(fat)).put("fiber", r1(fiber))

    companion object {
        fun fromJson(o: JSONObject) = FoodItem(
            o.optString("name", "Food"), o.optDouble("grams", 0.0).nz(), o.optDouble("kcal", 0.0).nz(),
            o.optDouble("protein", 0.0).nz(), o.optDouble("carbs", 0.0).nz(), o.optDouble("fat", 0.0).nz(), o.optDouble("fiber", 0.0).nz()
        )
        fun list(json: String): MutableList<FoodItem> = try {
            val a = JSONArray(json)
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
        fun json(items: List<FoodItem>): String = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()
        private fun Double.nz() = if (isNaN() || this < 0) 0.0 else this
        private fun r1(v: Double) = (v * 10).roundToInt() / 10.0
    }
}

/** A food estimate before it's saved: from a photo, a description or a barcode. */
data class Estimate(
    var name: String,
    val items: MutableList<FoodItem>,
    var healthScore: Int = 0,
    var note: String = "",
    var confidence: String = "",
    var source: String = "text",
    var photo: String = "",
    var meal: String = Fuel.mealFor(System.currentTimeMillis())
) {
    val kcal get() = items.sumOf { it.kcal }
    val protein get() = items.sumOf { it.protein }
    val carbs get() = items.sumOf { it.carbs }
    val fat get() = items.sumOf { it.fat }
    val fiber get() = items.sumOf { it.fiber }

    fun toMeal(date: String, id: Long = 0, time: Long = System.currentTimeMillis()) = Meal(
        id = id, date = date, time = time, meal = meal, name = name.ifBlank { items.firstOrNull()?.name ?: "Meal" },
        kcal = kcal.roundToInt(), protein = protein, carbs = carbs, fat = fat, fiber = fiber, source = source,
        items = FoodItem.json(items), photo = photo, healthScore = healthScore, note = note
    )

    companion object {
        fun fromMeal(m: Meal) = Estimate(m.name, FoodItem.list(m.items).ifEmpty {
            mutableListOf(FoodItem(m.name, 0.0, m.kcal.toDouble(), m.protein, m.carbs, m.fat, m.fiber))
        }, m.healthScore, m.note, "", m.source, m.photo, m.meal)
    }
}
