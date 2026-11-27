package com.forge.app.ui.verdict

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.forge.app.R
import com.forge.app.critique.Verdict
import com.forge.app.ui.kit.Kit
import com.forge.app.ui.kit.Ring
import com.forge.app.ui.kit.Spark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hercules' daily verdict: the day's score, what went right, and what to fix tomorrow. */
object VerdictUi {

    fun colorFor(score: Int) = when {
        score >= 75 -> Kit.GREEN
        score >= 50 -> Kit.AMBER
        else -> Kit.RED
    }

    /** The line shown on Today's link card. */
    fun cardLine(c: Context): String {
        val r = Verdict.saved(c, Verdict.today())
        return if (r != null) "Today: ${r.score}/100 · ${r.verdict}" else "Score today and get tomorrow's fixes"
    }

    fun open(c: Context, scope: CoroutineScope, onChange: () -> Unit = {}) {
        var dialog: Dialog? = null
        var shown: String = Verdict.today()
        val (root, page) = Kit.sheet(c, "Hercules' verdict", "Today") { dialog?.dismiss() }
        val titleView = (page.getChildAt(0) as? LinearLayout)?.let { head -> ((head.getChildAt(0) as? LinearLayout)?.getChildAt(1)) as? TextView }
        val body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(body)

        lateinit var render: () -> Unit

        fun run() {
            body.removeAllViews()
            body.addView(Kit.card(c, background = R.drawable.bg_card_next).apply {
                addView(Kit.row(c, "Hercules is going over your day…"))
                addView(Kit.sub(c, "Labors, focus, training, fuel and your reflection."))
            })
            scope.launch {
                val day = Verdict.gather(c, shown)
                Verdict.judge(c, day)
                render()
                onChange()
            }
        }

        render = render@{
            body.removeAllViews()
            titleView?.text = pretty(shown)
            val result = Verdict.saved(c, shown)
            val isToday = shown == Verdict.today()

            if (result == null) {
                body.addView(Kit.card(c).apply {
                    addView(Kit.row(c, if (isToday) "No verdict yet today" else "No verdict for this day"))
                    addView(Kit.sub(c, "Best near the end of the day, after your reflection. You can judge again any time; the newest one is kept."))
                })
                if (isToday) body.addView(Kit.primary(c, "Judge today") { run() }.apply { id = R.id.btn_verdict_run })
            } else {
                // Score and verdict
                val top = Kit.card(c, background = R.drawable.bg_card_next, vertical = false).apply { gravity = Gravity.CENTER_VERTICAL }
                top.addView(Ring(c).apply {
                    id = R.id.tv_verdict_score
                    fraction = result.score / 100f
                    big = result.score.toString()
                    small = "OF 100"
                    color = colorFor(result.score)
                    contentDescription = "Score ${result.score} out of 100"
                }, LinearLayout.LayoutParams(Kit.dp(c, 104), Kit.dp(c, 104)).apply { marginEnd = Kit.dp(c, 16) })
                top.addView(LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Kit.label(c, grade(result.score), bottom = 4).apply { setTextColor(colorFor(result.score)) })
                    addView(Kit.body(c, result.verdict))
                }, Kit.weight())
                body.addView(top)

                // Parts
                body.addView(Kit.label(c, "Breakdown"))
                body.addView(Kit.card(c).apply {
                    result.parts.forEachIndexed { i, p ->
                        addView(Kit.twoColumn(c, p.name, p.score?.let { "$it" } ?: "—", rightColor = p.score?.let { colorFor(it) } ?: Kit.FG3))
                        addView(Kit.bar(c, (p.score ?: 0) / 100f, p.score?.let { colorFor(it) } ?: Kit.TRACK))
                        addView(Kit.sub(c, p.detail).apply { setPadding(0, Kit.dp(c, 4), 0, if (i < result.parts.size - 1) Kit.dp(c, 8) else 0) })
                    }
                })

                if (result.wins.isNotEmpty()) {
                    body.addView(Kit.label(c, "What went right"))
                    body.addView(Kit.card(c).apply { result.wins.forEach { addView(point(c, it, Kit.GREEN)) } })
                }
                if (result.fixes.isNotEmpty()) {
                    body.addView(Kit.label(c, "Fix tomorrow"))
                    body.addView(Kit.card(c).apply { result.fixes.forEach { addView(point(c, it, Kit.RED)) } })
                }
                if (result.offline) {
                    body.addView(Kit.sub(c, "Hercules couldn't be reached, so these are Forge's own notes. Judge again when you're online.")
                        .apply { setPadding(0, 0, 0, Kit.dp(c, 12)) })
                }
                if (isToday) body.addView(Kit.secondary(c, "Judge again") { run() }.apply { id = R.id.btn_verdict_run })
            }

            // History
            val history = Verdict.history(c)
            if (history.size >= 2) {
                body.addView(Kit.spacer(c, 8))
                body.addView(Kit.label(c, "Last ${minOf(14, history.size)} verdicts"))
                body.addView(Kit.card(c).apply {
                    addView(Spark(c, history.take(14).reversed().map { it.score.toDouble() }),
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Kit.dp(c, 64)).apply { bottomMargin = Kit.dp(c, 8) })
                    val avg = history.take(7).map { it.score }.average().toInt()
                    addView(Kit.sub(c, "7-day average $avg"))
                })
            }
            if (history.isNotEmpty()) {
                body.addView(Kit.label(c, "Past days"))
                body.addView(Kit.card(c).apply {
                    history.take(30).forEach { r ->
                        addView(Kit.twoColumn(c, pretty(r.date), "${r.score}", bold = r.date == shown, rightColor = colorFor(r.score)).apply {
                            minimumHeight = Kit.dimen(c, R.dimen.tap_min)
                            setOnClickListener { shown = r.date; render() }
                        })
                    }
                })
                if (!isToday) body.addView(Kit.quiet(c, "Back to today") { shown = Verdict.today(); render() })
            }
        }

        render()
        dialog = Kit.showFull(c, root)
    }

    private fun point(c: Context, text: String, color: Int) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        setPadding(0, Kit.dp(c, 6), 0, Kit.dp(c, 6))
        addView(Kit.dot(c, color), LinearLayout.LayoutParams(Kit.dp(c, 8), Kit.dp(c, 8)).apply { topMargin = Kit.dp(c, 6); marginEnd = Kit.dp(c, 12) })
        addView(Kit.body(c, text), Kit.weight())
    }

    private fun grade(score: Int) = when {
        score >= 90 -> "Legendary"
        score >= 75 -> "Strong day"
        score >= 50 -> "Getting there"
        score >= 25 -> "Slipping"
        else -> "Reset"
    }

    private fun pretty(date: String): String {
        if (date == Verdict.today()) return "Today"
        return try {
            SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: Date())
        } catch (_: Exception) { date }
    }
}
