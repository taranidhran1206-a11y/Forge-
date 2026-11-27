package com.forge.app.ui.fuel

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import com.forge.app.R
import com.forge.app.fuel.Estimate
import com.forge.app.fuel.FoodItem
import com.forge.app.fuel.Fuel
import com.forge.app.fuel.FuelDatabase
import com.forge.app.fuel.Meal
import com.forge.app.gym.Strength
import com.forge.app.ui.kit.Kit
import com.forge.app.util.Photos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** The result screen: what she thinks you ate, editable, with "Fix results" and Save. */
object ResultSheet {

    fun waiting(c: Context, text: String): Dialog {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Kit.dp(c, 24), Kit.dp(c, 20), Kit.dp(c, 24), Kit.dp(c, 20))
            addView(ProgressBar(c).apply { isIndeterminate = true }, LinearLayout.LayoutParams(Kit.dp(c, 28), Kit.dp(c, 28)).apply { marginEnd = Kit.dp(c, 16) })
            addView(Kit.body(c, text))
        }
        return AlertDialog.Builder(c, R.style.ForgeDialogTheme).setView(box).setCancelable(false).show()
    }

    fun open(c: Context, scope: CoroutineScope, est: Estimate, existing: Meal?, forDate: String? = null) {
        var dialog: Dialog? = null
        val date = existing?.date ?: forDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val (root, page) = Kit.sheet(c, if (existing == null) "Fuel · new" else "Fuel", est.name.ifBlank { "Your meal" }) { dialog?.dismiss() }
        val body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(body)
        var factor = 1.0

        fun render() {
            body.removeAllViews()
            if (est.photo.isNotEmpty()) {
                val img = ImageView(c).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = GradientDrawable().apply { setColor(0xFF1E1E1E.toInt()); cornerRadius = Kit.dpf(c, 16f) }
                    clipToOutline = true
                    layoutParams = Kit.lp(h = Kit.dp(c, 200), bottom = Kit.dp(c, 12))
                }
                body.addView(img)
                Thread {
                    val bmp = Photos.load(File(Photos.dir(c, "fuel"), est.photo), 900)
                    img.post { if (bmp != null) img.setImageBitmap(bmp) }
                }.start()
            }
            val scaled = est.items.map { it.scaled(factor) }
            val kcal = scaled.sumOf { it.kcal }
            // Totals, Calz-style: calories big, macros in three tiles
            val totals = Kit.card(c)
            totals.addView(Kit.label(c, "Calories", bottom = 0))
            totals.addView(Kit.num(c, String.format(Locale.US, "%,d", kcal.roundToInt())).apply { textSize = 34f })
            val tiles = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, Kit.dp(c, 10), 0, 0) }
            fun tile(label: String, v: Double, color: Int) = tiles.addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_chip)
                setPadding(Kit.dp(c, 12), Kit.dp(c, 10), Kit.dp(c, 12), Kit.dp(c, 10))
                addView(Kit.label(c, label, bottom = 2))
                addView(Kit.num(c, "${v.roundToInt()} g", color).apply { textSize = 18f })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = Kit.dp(c, 6) })
            tile("Protein", scaled.sumOf { it.protein }, Kit.RED_TEXT)
            tile("Carbs", scaled.sumOf { it.carbs }, Kit.AMBER)
            tile("Fat", scaled.sumOf { it.fat }, Kit.BLUE)
            totals.addView(tiles)
            if (est.healthScore > 0 || est.note.isNotBlank()) {
                val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, Kit.dp(c, 12), 0, 0) }
                if (est.healthScore > 0) {
                    val color = when { est.healthScore >= 7 -> Kit.GREEN; est.healthScore >= 4 -> Kit.AMBER; else -> Kit.RED_TEXT }
                    row.addView(Kit.mono(c, "${est.healthScore}/10", color).apply { setPadding(0, 0, Kit.dp(c, 10), 0) })
                }
                row.addView(Kit.sub(c, est.note), Kit.weight())
                totals.addView(row)
            }
            if (est.confidence == "low") totals.addView(Kit.sub(c, "Low confidence: check the portions below or use Fix results.", Kit.AMBER).apply { setPadding(0, Kit.dp(c, 6), 0, 0) })
            body.addView(totals)

            // Meal and portion
            body.addView(Kit.label(c, "Meal"))
            val (mealScroll, mealChips) = Kit.chipRow(c)
            Fuel.MEALS.forEach { m -> mealChips.addView(Kit.chip(c, Fuel.mealName(m), est.meal == m) { est.meal = m; render() }) }
            body.addView(mealScroll)
            body.addView(Kit.label(c, "How much of it"))
            val (portionScroll, portionChips) = Kit.chipRow(c)
            listOf(0.5 to "½", 0.75 to "¾", 1.0 to "All", 1.5 to "1½", 2.0 to "2×").forEach { (f, t) ->
                portionChips.addView(Kit.chip(c, t, factor == f) { factor = f; render() })
            }
            body.addView(portionScroll)

            // Items
            body.addView(Kit.label(c, "What's in it"))
            val list = Kit.card(c, bottom = 12).apply { setPadding(paddingLeft, Kit.dp(c, 8), paddingRight, Kit.dp(c, 8)) }
            scaled.forEachIndexed { i, it ->
                val row = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, Kit.dp(c, 6), 0, Kit.dp(c, 6))
                    setOnClickListener { editItem(c, est.items, est.items[i]) { render() } }
                }
                row.addView(Kit.twoColumn(c, it.name, "${it.kcal.roundToInt()} kcal", bold = true).apply { setPadding(0, 0, 0, 0) })
                row.addView(Kit.sub(c, (if (it.grams > 0) "${Strength.plain(it.grams)} g · " else "") +
                    "P ${it.protein.roundToInt()} · C ${it.carbs.roundToInt()} · F ${it.fat.roundToInt()}"))
                list.addView(row)
            }
            list.addView(Kit.quiet(c, "Add an item") {
                est.items.add(FoodItem("Item", 0.0, 0.0, 0.0, 0.0, 0.0))
                editItem(c, est.items, est.items.last()) { render() }
            })
            body.addView(list)

            // Fix results
            body.addView(Kit.label(c, "Fix results"))
            val fix = Kit.input(c, "e.g. it was 2 eggs, no rice, small plate", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, single = false)
            body.addView(fix)
            body.addView(Kit.accent(c, "Fix it") {
                val text = fix.text.toString().trim()
                if (text.isEmpty()) return@accent
                val wait = waiting(c, "Fixing…")
                scope.launch {
                    try {
                        val jpeg = if (est.photo.isNotEmpty()) withContext(Dispatchers.IO) { Photos.jpeg(File(Photos.dir(c, "fuel"), est.photo), 1024) } else null
                        val fixed = Fuel.fix(c, Estimate(est.name, est.items.map { it.scaled(factor) }.toMutableList(), est.healthScore, est.note, est.confidence, est.source, est.photo, est.meal), text, jpeg)
                        wait.dismiss()
                        if (fixed.items.isEmpty()) return@launch
                        est.name = fixed.name.ifBlank { est.name }
                        est.items.clear(); est.items.addAll(fixed.items)
                        est.healthScore = fixed.healthScore; est.note = fixed.note; est.confidence = fixed.confidence
                        factor = 1.0
                        render()
                    } catch (e: Exception) {
                        wait.dismiss()
                        Toast.makeText(c, "Couldn't fix it: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })

            body.addView(Kit.spacer(c, 6))
            body.addView(Kit.primary(c, if (existing == null) "Log it" else "Save changes") {
                val final = Estimate(est.name, est.items.map { it.scaled(factor) }.toMutableList(), est.healthScore, est.note, est.confidence, est.source, est.photo, est.meal)
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val dao = FuelDatabase.get(c).dao()
                        if (existing == null) dao.insert(final.toMeal(date))
                        else dao.update(final.toMeal(existing.date, existing.id, existing.time))
                    }
                    dialog?.dismiss()
                }
            }.apply { id = R.id.btn_fuel_save })
            if (existing != null) {
                body.addView(Kit.quiet(c, "Delete") {
                    scope.launch {
                        withContext(Dispatchers.IO) { FuelDatabase.get(c).dao().delete(existing.id) }
                        if (existing.photo.isNotEmpty()) File(Photos.dir(c, "fuel"), existing.photo).delete()
                        dialog?.dismiss()
                    }
                })
            }
        }
        render()
        dialog = Kit.showFull(c, root)
    }

    private fun editItem(c: Context, items: MutableList<FoodItem>, item: FoodItem, done: () -> Unit) {
        val dec = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val name = Kit.input(c, "Name").apply { setText(item.name) }
        val grams = Kit.input(c, "Grams", dec).apply { setText(Strength.plain(item.grams)) }
        val kcal = Kit.input(c, "Calories", dec).apply { setText(Strength.plain(item.kcal)) }
        val p = Kit.input(c, "Protein g", dec).apply { setText(Strength.plain(item.protein)) }
        val carb = Kit.input(c, "Carbs g", dec).apply { setText(Strength.plain(item.carbs)) }
        val f = Kit.input(c, "Fat g", dec).apply { setText(Strength.plain(item.fat)) }
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Kit.dp(c, 20), Kit.dp(c, 8), Kit.dp(c, 20), 0)
            addView(name)
            addView(Kit.sub(c, "Change grams and the rest scales with it"))
            addView(grams); addView(kcal); addView(p); addView(carb); addView(f)
        }
        val oldGrams = item.grams
        AlertDialog.Builder(c, R.style.ForgeDialogTheme)
            .setTitle("Edit item")
            .setView(box)
            .setPositiveButton("Done") { _, _ ->
                fun num(e: android.widget.EditText, d: Double) = e.text.toString().replace(',', '.').toDoubleOrNull() ?: d
                val g = num(grams, item.grams)
                item.name = name.text.toString().trim().ifEmpty { item.name }
                val k = num(kcal, item.kcal); val pr = num(p, item.protein); val cb = num(carb, item.carbs); val ft = num(f, item.fat)
                val onlyGramsChanged = g != oldGrams && k == item.kcal && pr == item.protein && cb == item.carbs && ft == item.fat
                if (onlyGramsChanged && oldGrams > 0) {
                    val s = g / oldGrams
                    item.kcal *= s; item.protein *= s; item.carbs *= s; item.fat *= s; item.fiber *= s
                } else {
                    item.kcal = k; item.protein = pr; item.carbs = cb; item.fat = ft
                }
                item.grams = g
                done()
            }
            .setNeutralButton("Remove") { _, _ -> items.remove(item); done() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
