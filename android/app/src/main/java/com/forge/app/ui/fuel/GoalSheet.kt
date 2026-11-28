package com.forge.app.ui.fuel

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import com.forge.app.fuel.Fuel
import com.forge.app.gym.GymDatabase
import com.forge.app.gym.Strength
import com.forge.app.ui.kit.Kit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Your daily calorie and macro goal, worked out from a few facts about you. */
object GoalSheet {

    fun open(c: Context, first: Boolean = false, scope: CoroutineScope = MainScope(), done: () -> Unit) {
        scope.launch {
            val saved = Fuel.profile(c)
            val latest = withContext(Dispatchers.IO) { GymDatabase.get(c).gymDao().latestWeight()?.kg }
            build(c, if (latest != null && !Fuel.hasProfile(c)) saved.copy(weightKg = latest) else saved, first, done)
        }
    }

    private fun build(c: Context, start: Fuel.Profile, first: Boolean, done: () -> Unit) {
        var dialog: Dialog? = null
        var p = start
        val (root, page) = Kit.sheet(c, "Fuel", if (first) "Set your goal" else "Your goal") { dialog?.dismiss() }
        if (first) page.addView(Kit.sub(c, "A few facts and Forge works out how much to eat. You can change the numbers after.").apply { setPadding(0, 0, 0, Kit.dp(c, 14)) })
        val dyn = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val dec = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val age = Kit.input(c, "Age", InputType.TYPE_CLASS_NUMBER).apply { setText(p.age.toString()) }
        val height = Kit.input(c, "Height (cm)", InputType.TYPE_CLASS_NUMBER).apply { setText(p.heightCm.toString()) }
        val weight = Kit.input(c, "Weight (kg)", dec).apply { setText(Strength.plain(p.weightKg)) }
        val kcal = Kit.input(c, "Calories", InputType.TYPE_CLASS_NUMBER)
        val protein = Kit.input(c, "Protein g", InputType.TYPE_CLASS_NUMBER)
        val carbs = Kit.input(c, "Carbs g", InputType.TYPE_CLASS_NUMBER)
        val fat = Kit.input(c, "Fat g", InputType.TYPE_CLASS_NUMBER)

        fun fillGoals() {
            val g = Fuel.suggest(p)
            kcal.setText(g.kcal.toString()); protein.setText(g.protein.toString())
            carbs.setText(g.carbs.toString()); fat.setText(g.fat.toString())
        }

        fun renderChips() {
            dyn.removeAllViews()
            dyn.addView(Kit.label(c, "Body"))
            val (s1, sex) = Kit.chipRow(c)
            sex.addView(Kit.chip(c, "Male", p.male) { p = p.copy(male = true); renderChips(); fillGoals() })
            sex.addView(Kit.chip(c, "Female", !p.male) { p = p.copy(male = false); renderChips(); fillGoals() })
            dyn.addView(s1)
            dyn.addView(Kit.label(c, "Training"))
            val (s2, act) = Kit.chipRow(c)
            listOf("Little", "1–3 days", "3–5 days", "Very active").forEachIndexed { i, t ->
                act.addView(Kit.chip(c, t, p.activity == i) { p = p.copy(activity = i); renderChips(); fillGoals() })
            }
            dyn.addView(s2)
            dyn.addView(Kit.label(c, "Aim"))
            val (s3, aim) = Kit.chipRow(c)
            listOf(-1 to "Lose fat", 0 to "Maintain", 1 to "Build muscle").forEach { (v, t) ->
                aim.addView(Kit.chip(c, t, p.aim == v) { p = p.copy(aim = v); renderChips(); fillGoals() })
            }
            dyn.addView(s3)
        }
        renderChips()
        page.addView(dyn)

        fun row(a: EditText, b: EditText) = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(a, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = Kit.dp(c, 8) })
            addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        page.addView(Kit.label(c, "About you"))
        page.addView(row(age, height))
        page.addView(weight)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, cnt: Int) {}
            override fun afterTextChanged(s: Editable?) {
                p = p.copy(
                    age = age.text.toString().toIntOrNull()?.coerceIn(13, 90) ?: p.age,
                    heightCm = height.text.toString().toIntOrNull()?.coerceIn(120, 230) ?: p.heightCm,
                    weightKg = weight.text.toString().replace(',', '.').toDoubleOrNull()?.coerceIn(30.0, 250.0) ?: p.weightKg
                )
                fillGoals()
            }
        }
        listOf(age, height, weight).forEach { it.addTextChangedListener(watcher) }

        page.addView(Kit.label(c, "Daily goal"))
        page.addView(row(kcal, protein))
        page.addView(row(carbs, fat))
        page.addView(Kit.sub(c, "Protein is 1.8 g per kg for lifting, fat a quarter of calories, carbs the rest. Edit any number.")
            .apply { setPadding(0, 0, 0, Kit.dp(c, 14)) })
        val existing = Fuel.goals(c)
        if (Fuel.hasProfile(c)) {
            kcal.setText(existing.kcal.toString()); protein.setText(existing.protein.toString())
            carbs.setText(existing.carbs.toString()); fat.setText(existing.fat.toString())
        } else fillGoals()

        page.addView(Kit.primary(c, "Save goal") {
            val g = Fuel.Goals(
                kcal.text.toString().toIntOrNull()?.coerceIn(1000, 6000) ?: existing.kcal,
                protein.text.toString().toIntOrNull()?.coerceIn(20, 400) ?: existing.protein,
                carbs.text.toString().toIntOrNull()?.coerceIn(0, 900) ?: existing.carbs,
                fat.text.toString().toIntOrNull()?.coerceIn(10, 300) ?: existing.fat
            )
            Fuel.saveProfile(c, p, g)
            dialog?.dismiss()
            done()
        })
        page.addView(Kit.sub(c, String.format(Locale.US, "Estimates, not medical advice. Adjust after two weeks by how your weight moves."), Kit.FG3))
        dialog = Kit.showFull(c, root)
    }
}
