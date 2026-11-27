package com.forge.app.sync

import android.app.AlertDialog
import android.text.format.DateUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.forge.app.R
import com.forge.app.ui.kit.Kit
import kotlinx.coroutines.launch

/** The "Sync across devices" card on the Progress page. Hidden when this build has no sync set up. */
object AccountUi {

    fun bind(fragment: Fragment, label: TextView, card: LinearLayout) {
        if (!Account.configured) {
            label.visibility = View.GONE
            card.visibility = View.GONE
            return
        }
        label.visibility = View.VISIBLE
        card.visibility = View.VISIBLE
        render(fragment, card)
    }

    private fun render(fragment: Fragment, card: LinearLayout, busy: String? = null) {
        if (!fragment.isAdded) return
        val c = fragment.requireContext()
        card.removeAllViews()
        val user = Account.user(c)

        if (user == null) {
            card.addView(Kit.row(c, "Use Forge on your phone and tablet"))
            card.addView(Kit.sub(c, "Sign in with Google to keep labors, workouts, meals, verdicts and your rank the same on every device. Photos stay on the device they were taken on.")
                .apply { setPadding(0, Kit.dp(c, 4), 0, Kit.dp(c, 12)) })
            if (busy != null) {
                card.addView(Kit.sub(c, busy))
                return
            }
            card.addView(Kit.primary(c, "Sign in with Google") {
                val activity = fragment.activity ?: return@primary
                render(fragment, card, "Signing in…")
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    when (val r = Account.signIn(activity)) {
                        is Account.SignIn.Ok -> {
                            render(fragment, card, "Syncing for the first time…")
                            runSync(fragment, card, first = true)
                        }
                        is Account.SignIn.Cancelled -> render(fragment, card)
                        is Account.SignIn.Failed -> {
                            render(fragment, card)
                            if (fragment.isAdded) Toast.makeText(c, "Couldn't sign in: ${r.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }.apply { setLastMarginZero() })
            return
        }

        card.addView(Kit.row(c, user.name.ifBlank { user.email }))
        if (user.name.isNotBlank() && user.email.isNotBlank()) card.addView(Kit.sub(c, user.email))
        val last = SyncEngine.lastSync(c)
        val status = when {
            busy != null -> busy
            SyncEngine.lastError(c) != null -> "Last sync didn't finish: ${SyncEngine.lastError(c)}"
            last == 0L -> "Not synced yet"
            else -> "Synced " + DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        }
        card.addView(Kit.sub(c, status, if (busy == null && SyncEngine.lastError(c) != null) Kit.AMBER else Kit.FG2)
            .apply { setPadding(0, Kit.dp(c, 4), 0, Kit.dp(c, 12)) })
        if (busy != null) return
        card.addView(Kit.secondary(c, "Sync now") {
            render(fragment, card, "Syncing…")
            fragment.viewLifecycleOwner.lifecycleScope.launch { runSync(fragment, card, first = false) }
        })
        card.addView(Kit.quiet(c, "Sign out of this device") {
            AlertDialog.Builder(c, R.style.ForgeDialogTheme)
                .setTitle("Sign out?")
                .setMessage("Everything stays on this device and in your account. This device just stops syncing until you sign in again.")
                .setPositiveButton("Sign out") { _, _ ->
                    SyncWorker.stop(c)
                    Account.signOut(c)
                    render(fragment, card)
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
    }

    private suspend fun runSync(fragment: Fragment, card: LinearLayout, first: Boolean) {
        val c = fragment.context?.applicationContext ?: return
        val message = try {
            val r = SyncEngine.sync(c)
            SyncWorker.kick(c, force = true)
            when {
                first -> "Signed in. ${r.pulled} things came in from your other devices."
                r.pulled + r.pushed == 0 -> "Already up to date"
                else -> "Synced: ${r.pushed} sent, ${r.pulled} received"
            }
        } catch (e: Exception) {
            "Sync didn't finish: ${e.message ?: "unknown error"}"
        }
        if (!fragment.isAdded) return
        render(fragment, card)
        Toast.makeText(c, message, Toast.LENGTH_SHORT).show()
    }

    private fun View.setLastMarginZero() {
        (layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = 0
    }
}
