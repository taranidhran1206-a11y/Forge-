package com.forge.app.ui.gym

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.forge.app.R
import com.forge.app.gym.ExercisePhotos
import com.forge.app.gym.GymCatalogue
import com.forge.app.gym.GymDatabase
import com.forge.app.gym.Muscles
import com.forge.app.gym.Strength
import com.forge.app.ui.kit.Kit
import com.forge.app.ui.kit.Spark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The exercise page (photos, muscles, how-to, your history, variations) and the exercise picker. */
object ExerciseUi {

    /** A rounded photo box that flips between the start and end photos. */
    fun photoBox(c: Context, ex: GymCatalogue.Exercise, heightDp: Int = 220): View {
        val frame = FrameLayout(c).apply {
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = Kit.dpf(c, 16f)
            }
            clipToOutline = true
            layoutParams = Kit.lp(h = Kit.dp(c, heightDp), bottom = Kit.dp(c, 12))
        }
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = ex.name }
        val wait = Kit.sub(c, "Loading photos…", 0xFF555555.toInt()).apply { gravity = Gravity.CENTER }
        frame.addView(wait, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(img, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        ExercisePhotos.animate(img, ex.imageId) {
            wait.text = "Photos need a connection the first time"
        }
        return frame
    }

    fun show(
        c: Context,
        scope: CoroutineScope,
        name: String,
        swapLabel: String? = null,
        onSwap: ((GymCatalogue.Exercise) -> Unit)? = null
    ) {
        val ex = GymCatalogue.find(c, name)
            ?: GymCatalogue.Exercise(name, "Other", emptyList(), emptyList(), "", "", "strength", "", emptyList())
        var dialog: Dialog? = null
        val (root, page) = Kit.sheet(c, ex.group, ex.name) { dialog?.dismiss() }

        if (ex.hasPhotos) page.addView(photoBox(c, ex))

        // What it works and with what
        val facts = Kit.card(c)
        fun fact(k: String, v: String) { if (v.isNotBlank()) facts.addView(Kit.twoColumn(c, k, v)) }
        fact("Main muscles", ex.primary.joinToString(", ") { cap(it) })
        fact("Also works", ex.secondary.joinToString(", ") { cap(it) })
        fact("Equipment", cap(ex.equipment))
        fact("Level", cap(ex.level))
        if (facts.childCount > 0) page.addView(facts)

        if (onSwap != null) {
            page.addView(Kit.accent(c, swapLabel ?: "Use this exercise") { dialog?.dismiss(); onSwap(ex) })
        }

        if (ex.instructions.isNotEmpty()) {
            page.addView(Kit.label(c, "How to do it"))
            val how = Kit.card(c, bottom = 18)
            ex.instructions.forEachIndexed { i, step ->
                how.addView(LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, Kit.dp(c, 4), 0, Kit.dp(c, 4))
                    addView(Kit.mono(c, "${i + 1}", Kit.RED_TEXT).apply { minWidth = Kit.dp(c, 22) })
                    addView(Kit.body(c, step), Kit.weight())
                })
            }
            page.addView(how)
        }

        // Your numbers
        val mine = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(mine)
        scope.launch {
            val history = withContext(Dispatchers.IO) { GymDatabase.get(c).gymDao().historyOf(ex.name) }
            if (history.isEmpty()) return@launch
            mine.addView(Kit.label(c, "Your history"))
            val card = Kit.card(c, bottom = 18)
            val best = history.filter { it.setType != "warmup" }.maxByOrNull { it.weightKg }
            val bestE = history.maxOf { Strength.e1rm(it.weightKg, it.reps) }
            if (best != null) card.addView(Kit.twoColumn(c, "Heaviest set", "${Strength.kg(best.weightKg)} × ${best.reps}", bold = true))
            card.addView(Kit.twoColumn(c, "Estimated 1-rep max", Strength.kg(bestE)))
            val sessions = history.groupBy { it.workoutId }.values.sortedBy { it.first().startedAt }
            if (sessions.size >= 2) {
                card.addView(Spark(c, sessions.map { s -> s.maxOf { Strength.e1rm(it.weightKg, it.reps) } })
                    .apply { layoutParams = Kit.lp(h = Kit.dp(c, 56), top = Kit.dp(c, 6), bottom = Kit.dp(c, 6)) })
            }
            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
            sessions.takeLast(5).reversed().forEach { s ->
                card.addView(Kit.twoColumn(c, fmt.format(Date(s.first().startedAt)),
                    s.joinToString("  ") { "${Strength.plain(it.weightKg)}×${it.reps}" }))
            }
            mine.addView(card)
        }

        // Other ways to hit the same muscle
        val vars = GymCatalogue.variations(c, ex)
        if (vars.isNotEmpty()) {
            page.addView(Kit.label(c, "Variations"))
            val list = Kit.card(c, bottom = 18).apply { setPadding(paddingLeft, Kit.dp(c, 6), paddingRight, Kit.dp(c, 6)) }
            vars.forEach { v ->
                list.addView(resultRow(c, v) {
                    dialog?.dismiss()
                    show(c, scope, v.name, swapLabel, onSwap)
                })
            }
            page.addView(list)
        }
        if (ex.hasPhotos) page.addView(Kit.sub(c, "Photos and how-to: Free Exercise DB (public domain)").apply { gravity = Gravity.CENTER })
        dialog = Kit.showFull(c, root)
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    /** One exercise in a list: small photo, name, muscle · equipment. */
    fun resultRow(c: Context, ex: GymCatalogue.Exercise, onClick: () -> Unit): View {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Kit.dp(c, 8), 0, Kit.dp(c, 8))
            layoutParams = Kit.lp()
            setOnClickListener { onClick() }
        }
        val thumb = ImageView(c).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(0xFF1E1E1E.toInt()); cornerRadius = Kit.dpf(c, 10f) }
            clipToOutline = true
            contentDescription = null
        }
        row.addView(thumb, LinearLayout.LayoutParams(Kit.dp(c, 44), Kit.dp(c, 44)).apply { marginEnd = Kit.dp(c, 12) })
        bindThumb(thumb, ex)
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Kit.body(c, ex.name).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) })
        col.addView(Kit.sub(c, ex.subtitle))
        row.addView(col, Kit.weight())
        return row
    }

    private fun bindThumb(thumb: ImageView, ex: GymCatalogue.Exercise) {
        thumb.setImageDrawable(null)
        thumb.tag = ex.imageId
        if (!ex.hasPhotos) return
        ExercisePhotos.get(thumb.context, ex.imageId, 0) { bmp ->
            if (thumb.tag == ex.imageId && bmp != null) thumb.setImageBitmap(bmp)
        }
    }

    /**
     * Full-screen picker: search, muscle chips, results with photos.
     * [first] shows at the top before searching (for swaps: the variations).
     */
    fun pick(
        c: Context,
        heading: String,
        first: List<GymCatalogue.Exercise> = emptyList(),
        firstLabel: String = "Suggested",
        onPick: (GymCatalogue.Exercise) -> Unit
    ) {
        var dialog: Dialog? = null
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            val pad = Kit.dimen(c, R.dimen.page_pad)
            setPadding(pad, Kit.dp(c, 20), pad, 0)
        }
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Kit.label(c, "Exercises", bottom = 0))
        col.addView(Kit.title(c, heading, bottom = 0))
        head.addView(col, Kit.weight())
        head.addView(Kit.quiet(c, "Close") { dialog?.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        root.addView(head, Kit.lp(bottom = Kit.dp(c, 12)))

        val search = Kit.input(c, "Search exercises, e.g. bench or chest").apply { id = R.id.et_exercise_search }
        root.addView(search)
        val (chipScroll, chips) = Kit.chipRow(c)
        root.addView(chipScroll)

        var group: String? = null
        var query = ""
        val items = mutableListOf<Any>() // String = section label, Exercise = row, Pair = custom
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemViewType(position: Int) = when (items[position]) { is String -> 0; is GymCatalogue.Exercise -> 1; else -> 2 }
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v: View = when (viewType) {
                    0 -> Kit.label(c, "", bottom = 0).apply { setPadding(0, Kit.dp(c, 14), 0, Kit.dp(c, 4)) }
                    else -> LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                }
                v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                return object : RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (val item = items[position]) {
                    is String -> (holder.itemView as TextView).text = item
                    is GymCatalogue.Exercise -> {
                        val box = holder.itemView as LinearLayout
                        box.removeAllViews()
                        box.addView(resultRow(c, item) { dialog?.dismiss(); onPick(item) })
                    }
                    is Pair<*, *> -> {
                        val box = holder.itemView as LinearLayout
                        box.removeAllViews()
                        val name = item.first as String
                        box.addView(LinearLayout(c).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(0, Kit.dp(c, 10), 0, Kit.dp(c, 10))
                            addView(Kit.body(c, "Add \"$name\"").apply { setTypeface(typeface, android.graphics.Typeface.BOLD) })
                            addView(Kit.sub(c, "Your own exercise"))
                            setOnClickListener {
                                dialog?.dismiss()
                                onPick(GymCatalogue.Exercise(name, group ?: "Other", emptyList(), emptyList(), "", "", "strength", "", emptyList()))
                            }
                        })
                    }
                }
            }
        }
        val list = RecyclerView(c).apply {
            layoutManager = LinearLayoutManager(c)
            this.adapter = adapter
            clipToPadding = false
            setPadding(0, 0, 0, Kit.dp(c, 24))
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun refresh() {
            items.clear()
            val q = query.trim()
            if (q.isEmpty() && group == null && first.isNotEmpty()) {
                items.add(firstLabel)
                items.addAll(first)
                items.add("All exercises")
            }
            val results = GymCatalogue.search(c, q, group, limit = if (q.isEmpty()) 120 else 80)
            if (q.length >= 2 && results.none { it.name.equals(q, true) }) items.add(GymCatalogue.titleCase(q) to true)
            items.addAll(results)
            if (results.isEmpty() && q.isNotEmpty()) items.add("Nothing matches. Add it as your own exercise above.")
            adapter.notifyDataSetChanged()
            list.scrollToPosition(0)
        }

        fun renderChips() {
            chips.removeAllViews()
            chips.addView(Kit.chip(c, "All", group == null) { group = null; renderChips(); refresh() })
            (Muscles.GROUPS + "Cardio").forEach { g ->
                chips.addView(Kit.chip(c, g, group == g) { group = if (group == g) null else g; renderChips(); refresh() })
            }
        }
        renderChips()
        refresh()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; refresh() }
        })
        dialog = Kit.showFull(c, root)
    }
}
