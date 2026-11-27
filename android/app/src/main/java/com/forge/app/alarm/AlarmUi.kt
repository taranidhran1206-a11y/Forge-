package com.forge.app.alarm

import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.forge.app.R
import com.forge.app.ui.kit.Kit
import com.forge.app.util.Photos
import kotlinx.coroutines.CoroutineScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The alarm list and editor, opened from Today. */
object AlarmUi {

    private val DAY_ORDER = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
    private val DAY_SHORT = mapOf(
        Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu",
        Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat", Calendar.SUNDAY to "Sun"
    )
    private val CHALLENGES = listOf("pushups" to "Push-ups", "squats" to "Squats", "math" to "Sums")
    private val REPS = listOf(5, 10, 15, 20, 30)

    fun time(c: Context, hour: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
        return SimpleDateFormat(if (DateFormat.is24HourFormat(c)) "HH:mm" else "h:mm a", Locale.getDefault()).format(cal.time)
    }

    fun daysText(days: Set<Int>): String = when {
        days.isEmpty() -> "Once"
        days.size == 7 -> "Every day"
        days == setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY) -> "Weekdays"
        days == setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> "Weekends"
        else -> DAY_ORDER.filter { it in days }.joinToString(" ") { DAY_SHORT[it]!! }
    }

    /** The line on Today's card. */
    fun cardLine(c: Context): String {
        val (a, at) = AlarmStore.next(c) ?: return "Alarms you switch off with push-ups"
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val day = when (cal.get(Calendar.DAY_OF_YEAR)) {
            today.get(Calendar.DAY_OF_YEAR) -> "Today"
            tomorrow.get(Calendar.DAY_OF_YEAR) -> "Tomorrow"
            else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(at))
        }
        return "$day ${time(c, a.hour, a.minute)} · ${a.challengeText}"
    }

    fun open(c: Context, scope: CoroutineScope, onChange: () -> Unit = {}) {
        AlarmService.ensureChannel(c)
        var dialog: Dialog? = null
        val (root, page) = Kit.sheet(c, "Wake-up challenge", "Alarms") { dialog?.dismiss(); onChange() }
        val body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(body)

        lateinit var render: () -> Unit
        render = {
            body.removeAllViews()
            warnings(c)?.let { (text, fix) ->
                body.addView(Kit.card(c, background = R.drawable.bg_card_next).apply {
                    addView(Kit.body(c, text))
                    addView(Kit.spacer(c, 8))
                    addView(Kit.accent(c, "Fix it") { fix() })
                })
            }
            body.addView(Kit.primary(c, "New alarm") { edit(c, null) { render(); onChange() } })
            body.addView(Kit.sub(c, "The alarm keeps ringing until you've done the reps. Forge counts them with the front camera; nothing is recorded or sent anywhere.")
                .apply { setPadding(0, 0, 0, Kit.dp(c, 16)) })

            val alarms = AlarmStore.all(c)
            if (alarms.isEmpty()) {
                body.addView(Kit.card(c).apply { addView(Kit.body(c, "No alarms yet. Set one for tomorrow morning.")) })
            }
            alarms.forEach { a -> body.addView(alarmCard(c, a, { edit(c, a) { render(); onChange() } }) { render(); onChange() }) }

            body.addView(Kit.spacer(c, 8))
            body.addView(Kit.label(c, "Try it first"))
            val (row, chips) = Kit.chipRow(c)
            CHALLENGES.forEach { (key, name) ->
                chips.addView(Kit.chip(c, name, false) { practice(c, key) })
            }
            body.addView(row)
            body.addView(Kit.sub(c, "A practice run with no ringing, so you can find a good spot for the phone."))
        }
        render()
        dialog = Kit.showFull(c, root)
    }

    private fun alarmCard(c: Context, a: AlarmStore.Alarm, onEdit: () -> Unit, onChange: () -> Unit): LinearLayout {
        val card = Kit.card(c, vertical = false).apply { gravity = Gravity.CENTER_VERTICAL; setOnClickListener { onEdit() } }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Kit.num(c, time(c, a.hour, a.minute), if (a.enabled) Kit.FG else Kit.FG3).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f) })
        col.addView(Kit.sub(c, listOfNotNull(daysText(a.days), a.challengeText, a.label.ifBlank { null }).joinToString(" · ")))
        card.addView(col, Kit.weight())
        card.addView(Kit.chip(c, if (a.enabled) "On" else "Off", a.enabled) {
            AlarmStore.save(c, a.copy(enabled = !a.enabled))
            if (!a.enabled) toastNext(c, a.copy(enabled = true))
            onChange()
        })
        return card
    }

    private fun toastNext(c: Context, a: AlarmStore.Alarm) {
        val at = AlarmStore.nextTrigger(a) ?: return
        val mins = (at - System.currentTimeMillis()) / 60_000
        val text = if (mins < 60) "Rings in $mins min" else "Rings in ${mins / 60} h ${mins % 60} min"
        Toast.makeText(c, text, Toast.LENGTH_SHORT).show()
    }

    private fun practice(c: Context, challenge: String) {
        val go = {
            c.startActivity(Intent(c, AlarmActivity::class.java)
                .putExtra(AlarmStore.EXTRA_PREVIEW, true)
                .putExtra("challenge", challenge)
                .putExtra("reps", 5))
        }
        val activity = Photos.activityOf(c)
        if (challenge == "math" || activity == null) go()
        else Photos.askCamera(activity) { go() }
    }

    /** Things that would stop the alarm from ringing properly, with a way to fix each. */
    private fun warnings(c: Context): Pair<String, () -> Unit>? {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) {
            return "Notifications are off for Forge, so alarms can't ring." to {
                c.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName))
            }
        }
        if (!AlarmStore.canExact(c) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return "Exact alarms are off for Forge, so alarms may ring a few minutes late." to {
                c.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}")))
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            return "Forge can't open the challenge over the lock screen. Allow full-screen alerts." to {
                c.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${c.packageName}")))
            }
        }
        return null
    }

    // ── editor ──

    private fun edit(c: Context, existing: AlarmStore.Alarm?, done: () -> Unit) {
        var hour = existing?.hour ?: 6
        var minute = existing?.minute ?: 30
        val days = (existing?.days ?: setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)).toMutableSet()
        var challenge = existing?.challenge ?: "pushups"
        var reps = existing?.reps ?: 15

        var dialog: Dialog? = null
        val (root, page) = Kit.sheet(c, "Wake-up challenge", if (existing == null) "New alarm" else "Edit alarm") { dialog?.dismiss() }
        val body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(body)
        val label = Kit.input(c, "Label (optional)").apply { setText(existing?.label ?: "") }

        lateinit var render: () -> Unit
        render = {
            body.removeAllViews()
            body.addView(Kit.card(c, background = R.drawable.bg_card_next).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(TextView(c).apply {
                    text = time(c, hour, minute)
                    setTextColor(Kit.FG)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    minimumHeight = Kit.dimen(c, R.dimen.tap_min)
                    setOnClickListener {
                        TimePickerDialog(c, R.style.ForgeDialogTheme, { _, h, m -> hour = h; minute = m; render() }, hour, minute, DateFormat.is24HourFormat(c)).show()
                    }
                })
                addView(Kit.sub(c, "Tap the time to change it").apply { gravity = Gravity.CENTER })
            })

            body.addView(Kit.label(c, "Repeat"))
            val (dRow, dChips) = Kit.chipRow(c)
            DAY_ORDER.forEach { d ->
                dChips.addView(Kit.chip(c, DAY_SHORT[d]!!, d in days) { if (d in days) days.remove(d) else days.add(d); render() })
            }
            body.addView(dRow)
            body.addView(Kit.sub(c, daysText(days) + if (days.isEmpty()) " — turns itself off after ringing" else "").apply { setPadding(0, 0, 0, Kit.dp(c, 12)) })

            body.addView(Kit.label(c, "To switch it off"))
            val (cRow, cChips) = Kit.chipRow(c)
            CHALLENGES.forEach { (key, name) -> cChips.addView(Kit.chip(c, name, challenge == key) { challenge = key; render() }) }
            body.addView(cRow)
            if (challenge != "math") {
                body.addView(Kit.spacer(c, 8))
                val (rRow, rChips) = Kit.chipRow(c)
                REPS.forEach { n -> rChips.addView(Kit.chip(c, "$n", reps == n) { reps = n; render() }) }
                body.addView(rRow)
                body.addView(Kit.sub(c, "$reps ${if (challenge == "squats") "squats" else "push-ups"}, counted by the camera. Sums are there if the camera can't see you.")
                    .apply { setPadding(0, 0, 0, Kit.dp(c, 12)) })
            } else {
                body.addView(Kit.sub(c, "Three sums, like 37 + 48 or 7 × 13.").apply { setPadding(0, Kit.dp(c, 4), 0, Kit.dp(c, 12)) })
            }

            body.addView(Kit.label(c, "Label"))
            (label.parent as? LinearLayout)?.removeView(label)
            body.addView(label)
            body.addView(Kit.spacer(c, 12))

            body.addView(Kit.primary(c, "Save alarm") {
                val save = {
                    val a = AlarmStore.Alarm(
                        existing?.id ?: AlarmStore.newId(c), hour, minute, days.toSet(), true, challenge,
                        if (challenge == "math") 3 else reps, label.text.toString().trim()
                    )
                    AlarmStore.save(c, a)
                    toastNext(c, a)
                    dialog?.dismiss()
                    done()
                }
                val activity = Photos.activityOf(c)
                if (challenge != "math" && activity != null) Photos.askCamera(activity) { granted ->
                    if (!granted) Toast.makeText(c, "Without the camera, this alarm will ask for sums instead", Toast.LENGTH_LONG).show()
                    save()
                } else save()
            })
            if (existing != null) {
                body.addView(Kit.quiet(c, "Delete alarm") {
                    AlertDialog.Builder(c, R.style.ForgeDialogTheme)
                        .setTitle("Delete this alarm?")
                        .setPositiveButton("Delete") { _, _ -> AlarmStore.delete(c, existing.id); dialog?.dismiss(); done() }
                        .setNegativeButton("Keep", null)
                        .show()
                })
            }
        }
        render()
        dialog = Kit.showFull(c, root)
    }
}
