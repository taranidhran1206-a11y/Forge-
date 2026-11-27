package com.forge.app.ui.fuel

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.forge.app.R
import com.forge.app.fuel.DayTotal
import com.forge.app.fuel.Estimate
import com.forge.app.fuel.FoodItem
import com.forge.app.fuel.Fuel
import com.forge.app.fuel.FuelDatabase
import com.forge.app.fuel.Meal
import com.forge.app.gym.GymDatabase
import com.forge.app.ui.kit.Kit
import com.forge.app.ui.kit.Ring
import com.forge.app.util.Photos
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Fuel: snap, describe or scan what you eat; calories and macros against your goal. */
class FuelFragment : Fragment() {

    private val dao by lazy { FuelDatabase.get(requireContext()).dao() }
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var day = Calendar.getInstance()

    private lateinit var dayTitle: TextView
    private lateinit var ring: Ring
    private lateinit var macros: LinearLayout
    private lateinit var mealsBox: LinearLayout
    private lateinit var weekBox: LinearLayout
    private lateinit var goalLine: TextView
    private var dayData: LiveData<List<Meal>>? = null
    private val dayObserver = Observer<List<Meal>> { render(it) }

    private val date get() = iso.format(day.time)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val c = requireContext()
        val (root, page) = Kit.page(c)
        page.addView(Kit.label(c, "Fuel", bottom = 0))
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = Kit.lp(bottom = Kit.dp(c, 16)) }
        head.addView(arrow(c, "‹") { moveDay(-1) })
        dayTitle = Kit.title(c, "Today", bottom = 0).apply { gravity = Gravity.CENTER }
        head.addView(dayTitle, Kit.weight())
        head.addView(arrow(c, "›") { moveDay(1) })
        page.addView(head)

        // Ring and macros
        val top = Kit.card(c, bottom = 12, vertical = false).apply { gravity = Gravity.CENTER_VERTICAL }
        ring = Ring(c).apply { id = R.id.tv_fuel_left }
        top.addView(ring, LinearLayout.LayoutParams(Kit.dp(c, 140), Kit.dp(c, 140)).apply { marginEnd = Kit.dp(c, 16) })
        macros = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        top.addView(macros, Kit.weight())
        page.addView(top)
        goalLine = Kit.sub(c, "").apply {
            setPadding(0, 0, 0, Kit.dp(c, 14))
            setOnClickListener { GoalSheet.open(c) { refreshDay() } }
        }
        page.addView(goalLine)

        // Ways to log
        val actions = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = Kit.lp(bottom = Kit.dp(c, 6)) }
        actions.addView(Kit.primary(c, "Snap") { snap(camera = true) }.apply { id = R.id.btn_fuel_snap }, LinearLayout.LayoutParams(0, Kit.dimen(c, R.dimen.tap_min), 1f).apply { marginEnd = Kit.dp(c, 8) })
        actions.addView(Kit.secondary(c, "Describe") { describe() }.apply { id = R.id.btn_fuel_describe }, LinearLayout.LayoutParams(0, Kit.dimen(c, R.dimen.tap_min), 1f).apply { marginEnd = Kit.dp(c, 8) })
        actions.addView(Kit.secondary(c, "Scan") { scan() }.apply { id = R.id.btn_fuel_scan }, LinearLayout.LayoutParams(0, Kit.dimen(c, R.dimen.tap_min), 1f))
        page.addView(actions)
        val more = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = Kit.lp(bottom = Kit.dp(c, 14)) }
        more.addView(Kit.quiet(c, "From gallery") { snap(camera = false) }, Kit.weight())
        more.addView(Kit.quiet(c, "Eat again") { eatAgain() }, Kit.weight())
        more.addView(Kit.quiet(c, "Quick add") { quickAdd() }, Kit.weight())
        page.addView(more)

        mealsBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(mealsBox)
        page.addView(Kit.label(c, "Last 7 days"))
        weekBox = Kit.card(c, bottom = 18)
        page.addView(weekBox)
        page.addView(Kit.sub(c, "Estimates from Gemini and Open Food Facts. Check portions with Fix if something looks off.", Kit.FG3).apply { gravity = Gravity.CENTER })
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val c = requireContext()
        refreshDay()
        val weekAgo = iso.format(Date(System.currentTimeMillis() - 6 * 86_400_000L))
        dao.totalsSince(weekAgo).asLiveData().observe(viewLifecycleOwner) { renderWeek(it) }
        if (!Fuel.hasProfile(c)) view.post { GoalSheet.open(c, first = true) { refreshDay() } }
    }

    private fun arrow(c: Context, t: String, onClick: () -> Unit) = TextView(c).apply {
        text = t
        textSize = 26f
        setTextColor(Kit.FG2)
        gravity = Gravity.CENTER
        setPadding(Kit.dp(c, 12), 0, Kit.dp(c, 12), 0)
        setOnClickListener { onClick() }
    }

    private fun moveDay(by: Int) {
        val next = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, by) }
        if (next.after(Calendar.getInstance())) return
        day = next
        refreshDay()
    }

    private fun refreshDay() {
        val today = iso.format(Date())
        val yesterday = iso.format(Date(System.currentTimeMillis() - 86_400_000L))
        dayTitle.text = when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(day.time)
        }
        dayData?.removeObserver(dayObserver)
        dayData = dao.day(date).asLiveData().also { it.observe(viewLifecycleOwner, dayObserver) }
    }

    private fun render(meals: List<Meal>) {
        val c = context ?: return
        val g = Fuel.goals(c)
        val kcal = meals.sumOf { it.kcal }
        val left = g.kcal - kcal
        ring.big = String.format(Locale.US, "%,d", kotlin.math.abs(left))
        ring.small = if (left >= 0) "kcal left" else "kcal over"
        ring.fraction = kcal.toFloat() / g.kcal.coerceAtLeast(1)
        ring.color = if (left >= 0) Kit.RED else Kit.AMBER
        macros.removeAllViews()
        macroRow(c, "Protein", meals.sumOf { it.protein }, g.protein, Kit.RED_TEXT)
        macroRow(c, "Carbs", meals.sumOf { it.carbs }, g.carbs, Kit.AMBER)
        macroRow(c, "Fat", meals.sumOf { it.fat }, g.fat, Kit.BLUE)
        goalLine.text = "Goal ${String.format(Locale.US, "%,d", g.kcal)} kcal · ${g.protein} g protein · ${kcal} eaten. Tap to change"

        mealsBox.removeAllViews()
        Fuel.MEALS.forEach { type ->
            val list = meals.filter { it.meal == type }
            if (list.isEmpty()) return@forEach
            val sum = list.sumOf { it.kcal }
            val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = Kit.lp(bottom = Kit.dp(c, 8)) }
            head.addView(Kit.label(c, Fuel.mealName(type), bottom = 0), Kit.weight())
            head.addView(Kit.mono(c, "$sum kcal", Kit.FG2))
            mealsBox.addView(head)
            list.forEach { m -> mealsBox.addView(mealCard(c, m)) }
            mealsBox.addView(Kit.spacer(c, 8))
        }
        if (meals.isEmpty()) {
            mealsBox.addView(Kit.card(c).apply {
                addView(Kit.row(c, "Nothing logged ${if (dayTitle.text == "Today") "yet today" else "this day"}"))
                addView(Kit.sub(c, "Snap your plate, describe it in words, or scan a barcode."))
            })
            mealsBox.addView(Kit.spacer(c, 6))
        }
    }

    private fun macroRow(c: Context, name: String, have: Double, goal: Int, color: Int) {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Kit.label(c, name, bottom = 0), Kit.weight())
        row.addView(Kit.mono(c, "${have.roundToInt()} / $goal g"))
        macros.addView(row)
        macros.addView(Kit.bar(c, (have / goal.coerceAtLeast(1)).toFloat(), color).apply {
            (layoutParams as LinearLayout.LayoutParams).apply { topMargin = Kit.dp(c, 4); bottomMargin = Kit.dp(c, 12) }
        })
    }

    private fun mealCard(c: Context, m: Meal): View {
        val card = Kit.card(c, bottom = 8, vertical = false).apply {
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { ResultSheet.open(c, viewLifecycleOwner.lifecycleScope, Estimate.fromMeal(m), m) }
        }
        if (m.photo.isNotEmpty()) {
            val img = ImageView(c).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { setColor(0xFF1E1E1E.toInt()); cornerRadius = Kit.dpf(c, 10f) }
                clipToOutline = true
                contentDescription = null
            }
            card.addView(img, LinearLayout.LayoutParams(Kit.dp(c, 48), Kit.dp(c, 48)).apply { marginEnd = Kit.dp(c, 12) })
            Thread {
                val bmp = Photos.load(File(Photos.dir(c, "fuel"), m.photo), 160)
                img.post { if (bmp != null) img.setImageBitmap(bmp) }
            }.start()
        }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Kit.row(c, m.name))
        col.addView(Kit.sub(c, "P ${m.protein.roundToInt()} · C ${m.carbs.roundToInt()} · F ${m.fat.roundToInt()}" +
            (if (m.healthScore > 0) " · ${m.healthScore}/10" else "")))
        card.addView(col, Kit.weight())
        card.addView(Kit.mono(c, "${m.kcal}", Kit.FG).apply { setTypeface(Typeface.MONOSPACE, Typeface.BOLD) })
        return card
    }

    private fun renderWeek(days: List<DayTotal>) {
        val c = context ?: return
        weekBox.removeAllViews()
        if (days.isEmpty()) { weekBox.addView(Kit.sub(c, "Your averages show up after a day or two of logging.")); return }
        val g = Fuel.goals(c)
        val avgK = days.map { it.kcal }.average().roundToInt()
        val avgP = days.map { it.protein }.average().roundToInt()
        val onTarget = days.count { kotlin.math.abs(it.kcal - g.kcal) <= g.kcal * 0.1 }
        val proteinDays = days.count { it.protein >= g.protein * 0.9 }
        weekBox.addView(Kit.twoColumn(c, "Average calories", String.format(Locale.US, "%,d kcal", avgK)))
        weekBox.addView(Kit.twoColumn(c, "Average protein", "$avgP g"))
        weekBox.addView(Kit.twoColumn(c, "Days within 10% of goal", "$onTarget of ${days.size}"))
        weekBox.addView(Kit.twoColumn(c, "Days protein hit", "$proteinDays of ${days.size}"))
    }

    // ═════════════════════════ logging ═════════════════════════

    private fun snap(camera: Boolean) {
        val c = requireContext()
        val activity = requireActivity()
        val name = "meal_${System.currentTimeMillis()}.jpg"
        val raw = File(Photos.dir(c, "fuel"), "raw_$name")
        val done = { ok: Boolean ->
            if (ok) analysePhoto(raw, name) else raw.delete()
            Unit
        }
        if (camera) Photos.take(activity, raw, done) else Photos.pick(activity, raw, done)
    }

    private fun analysePhoto(raw: File, name: String) {
        val c = requireContext()
        val wait = ResultSheet.waiting(c, "Looking at your meal…")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    val small = Photos.jpeg(raw, 1024)
                    if (small != null) File(Photos.dir(c, "fuel"), name).writeBytes(small)
                    raw.delete()
                    small
                } ?: throw IllegalStateException("couldn't read the photo")
                val est = Fuel.fromPhoto(c, jpeg, name)
                wait.dismiss()
                if (est.items.isEmpty()) {
                    Toast.makeText(c, est.note.ifBlank { "No food found in that photo" }, Toast.LENGTH_LONG).show()
                    return@launch
                }
                est.meal = Fuel.mealFor(System.currentTimeMillis())
                ResultSheet.open(c, viewLifecycleOwner.lifecycleScope, est, null, date)
            } catch (e: Exception) {
                wait.dismiss()
                Toast.makeText(c, "Couldn't analyse it: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun describe() {
        val c = requireContext()
        val field = Kit.input(c, "e.g. nasi lemak with fried chicken and teh tarik", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, single = false)
            .apply { id = R.id.et_fuel_describe; minLines = 2 }
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(Kit.dp(c, 20), Kit.dp(c, 8), Kit.dp(c, 20), 0); addView(field) }
        AlertDialog.Builder(c, R.style.ForgeDialogTheme)
            .setTitle("What did you eat?")
            .setView(box)
            .setPositiveButton("Estimate") { _, _ ->
                val text = field.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                val wait = ResultSheet.waiting(c, "Working out the numbers…")
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val est = Fuel.fromText(c, text)
                        wait.dismiss()
                        if (est.items.isEmpty()) { Toast.makeText(c, "Couldn't tell what that was. Try more detail.", Toast.LENGTH_LONG).show(); return@launch }
                        ResultSheet.open(c, viewLifecycleOwner.lifecycleScope, est, null, date)
                    } catch (e: Exception) {
                        wait.dismiss()
                        Toast.makeText(c, "Couldn't estimate it: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scan() {
        val c = requireContext()
        GmsBarcodeScanning.getClient(c).startScan()
            .addOnSuccessListener { code ->
                val value = code.rawValue ?: return@addOnSuccessListener
                val wait = ResultSheet.waiting(c, "Looking up $value…")
                viewLifecycleOwner.lifecycleScope.launch {
                    val product = try { Fuel.lookup(value) } catch (_: Exception) { null }
                    wait.dismiss()
                    if (product == null) {
                        Toast.makeText(c, "Not in Open Food Facts. Try Describe or Snap.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    askPortion(product)
                }
            }
            .addOnFailureListener { e -> Toast.makeText(c, "Scanner unavailable: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun askPortion(p: Fuel.Product) {
        val c = requireContext()
        val grams = Kit.input(c, "Grams", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            .apply { setText(com.forge.app.gym.Strength.plain(p.servingGrams ?: 100.0)) }
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Kit.dp(c, 20), Kit.dp(c, 8), Kit.dp(c, 20), 0)
            addView(Kit.sub(c, "Per 100 g: ${p.per100.kcal.roundToInt()} kcal · P ${p.per100.protein.roundToInt()} · C ${p.per100.carbs.roundToInt()} · F ${p.per100.fat.roundToInt()}"
                + (p.servingGrams?.let { "\nOne serving is ${com.forge.app.gym.Strength.plain(it)} g" } ?: "")))
            addView(Kit.spacer(c, 8))
            addView(grams)
        }
        AlertDialog.Builder(c, R.style.ForgeDialogTheme)
            .setTitle(p.name)
            .setView(box)
            .setPositiveButton("Next") { _, _ ->
                val g = grams.text.toString().replace(',', '.').toDoubleOrNull() ?: return@setPositiveButton
                val item = p.per100.scaled(g / 100.0).apply { name = p.name }
                val est = Estimate(p.name, mutableListOf(item), source = "barcode")
                ResultSheet.open(c, viewLifecycleOwner.lifecycleScope, est, null, date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun eatAgain() {
        val c = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val recent = withContext(Dispatchers.IO) { dao.recent(25) }
            if (recent.isEmpty()) { Toast.makeText(c, "Nothing logged yet", Toast.LENGTH_SHORT).show(); return@launch }
            AlertDialog.Builder(c, R.style.ForgeDialogTheme)
                .setTitle("Eat again")
                .setItems(recent.map { "${it.name} · ${it.kcal} kcal" }.toTypedArray()) { _, which ->
                    val est = Estimate.fromMeal(recent[which]).apply { meal = Fuel.mealFor(System.currentTimeMillis()); photo = "" }
                    ResultSheet.open(c, viewLifecycleOwner.lifecycleScope, est, null, date)
                }
                .show()
        }
    }

    private fun quickAdd() {
        val c = requireContext()
        val name = Kit.input(c, "Name (optional)")
        val kcal = Kit.input(c, "Calories", InputType.TYPE_CLASS_NUMBER)
        val protein = Kit.input(c, "Protein g (optional)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Kit.dp(c, 20), Kit.dp(c, 8), Kit.dp(c, 20), 0)
            addView(name); addView(kcal); addView(protein)
        }
        AlertDialog.Builder(c, R.style.ForgeDialogTheme)
            .setTitle("Quick add")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val k = kcal.text.toString().toIntOrNull() ?: return@setPositiveButton
                val p = protein.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                val n = name.text.toString().trim().ifEmpty { "Quick add" }
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    dao.insert(Meal(date = date, meal = Fuel.mealFor(System.currentTimeMillis()), name = n, kcal = k,
                        protein = p, carbs = 0.0, fat = 0.0, source = "manual",
                        items = FoodItem.json(listOf(FoodItem(n, 0.0, k.toDouble(), p, 0.0, 0.0)))))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        /** For the Today card: (kcal left, protein still to eat), or null before any goal is set. */
        suspend fun todaySummary(c: Context): Pair<Int, Int>? = withContext(Dispatchers.IO) {
            if (!Fuel.hasProfile(c)) return@withContext null
            val g = Fuel.goals(c)
            val meals = FuelDatabase.get(c).dao().dayNow(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
            (g.kcal - meals.sumOf { it.kcal }) to (g.protein - meals.sumOf { it.protein }.roundToInt())
        }

        suspend fun latestBodyWeight(c: Context): Double? = withContext(Dispatchers.IO) { GymDatabase.get(c).gymDao().latestWeight()?.kg }
    }
}
