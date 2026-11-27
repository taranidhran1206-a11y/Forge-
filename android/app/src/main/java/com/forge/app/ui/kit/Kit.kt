package com.forge.app.ui.kit

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import com.forge.app.R

/**
 * Building blocks for Forge screens made in code. Everything here reuses the app's own
 * drawables, colours and text sizes, so new screens look exactly like Today and Gym.
 */
object Kit {

    const val FG = 0xFFF5F5F5.toInt()
    const val FG2 = 0xFF9E9E9E.toInt()
    const val FG3 = 0xFF555555.toInt()
    const val SOFT = 0xFFC4C4C4.toInt()
    const val RED = 0xFFE53935.toInt()
    const val RED_TEXT = 0xFFFF8A80.toInt()
    const val GREEN = 0xFF00E676.toInt()
    const val AMBER = 0xFFFFB300.toInt()
    const val BLUE = 0xFF64B5F6.toInt()
    const val CARD = 0xFF111111.toInt()
    const val LINE = 0xFF1E1E1E.toInt()
    const val TRACK = 0xFF1E1E1E.toInt()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun dpf(c: Context, v: Float) = v * c.resources.displayMetrics.density
    private fun px(c: Context, dimen: Int) = c.resources.getDimension(dimen)
    fun dimen(c: Context, dimen: Int) = c.resources.getDimensionPixelSize(dimen)

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, bottom: Int = 0, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { bottomMargin = bottom; topMargin = top }

    fun weight(w: Float = 1f) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)

    /** Scrolling page with the same max width and padding as the Gym page. Returns (root, content). */
    fun page(c: Context): Pair<View, LinearLayout> {
        val scroll = NestedScrollView(c).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            isFillViewport = true
        }
        val frame = ConstraintLayout(c)
        val content = LinearLayout(c).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            val pad = dimen(c, R.dimen.page_pad)
            setPadding(pad, dp(c, 20), pad, dp(c, 24))
        }
        frame.addView(content, ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            matchConstraintMaxWidth = dimen(c, R.dimen.content_max_width)
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        })
        scroll.addView(frame, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        return scroll to content
    }

    fun label(c: Context, text: String, bottom: Int = 8) = TextView(c).apply {
        setTextAppearance(R.style.Forge_Label)
        this.text = text
        layoutParams = lp(ViewGroup.LayoutParams.WRAP_CONTENT, bottom = dp(c, bottom))
    }

    fun title(c: Context, text: String, bottom: Int = 16) = TextView(c).apply {
        setTextAppearance(R.style.Forge_Title)
        this.text = text
        layoutParams = lp(ViewGroup.LayoutParams.WRAP_CONTENT, bottom = dp(c, bottom))
    }

    /** Bold row text, like labor names and "Log today's workout". */
    fun row(c: Context, text: String, color: Int = FG) = TextView(c).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, R.dimen.t_row))
        setTypeface(typeface, Typeface.BOLD)
    }

    fun body(c: Context, text: String, color: Int = FG) = TextView(c).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, R.dimen.t_body))
        setLineSpacing(dpf(c, 2f), 1f)
    }

    fun sub(c: Context, text: String, color: Int = FG2) = TextView(c).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, R.dimen.t_small))
        setLineSpacing(dpf(c, 2f), 1f)
    }

    fun num(c: Context, text: String, color: Int = FG) = TextView(c).apply {
        setTextAppearance(R.style.Forge_Num)
        this.text = text
        setTextColor(color)
    }

    fun mono(c: Context, text: String, color: Int = SOFT) = TextView(c).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, R.dimen.t_small))
        typeface = Typeface.MONOSPACE
    }

    fun card(c: Context, bottom: Int = 12, vertical: Boolean = true, background: Int = R.drawable.bg_card) = LinearLayout(c).apply {
        orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        setBackgroundResource(background)
        val pad = dimen(c, R.dimen.card_pad)
        setPadding(pad, pad, pad, pad)
        layoutParams = lp(bottom = dp(c, bottom))
    }

    /** The Today page's link cards: small label, bold line, chevron. */
    fun linkCard(c: Context, label: String, line: String, onClick: () -> Unit): Pair<LinearLayout, TextView> {
        val card = card(c, vertical = false).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dimen(c, R.dimen.tap_min)
            setOnClickListener { onClick() }
        }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(c, label, bottom = 0))
        val text = row(c, line)
        col.addView(text)
        card.addView(col, weight())
        card.addView(chevron(c))
        return card to text
    }

    fun chevron(c: Context) = ImageView(c).apply {
        setImageResource(R.drawable.ic_chevron)
        contentDescription = null
        layoutParams = LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)).apply { marginStart = dp(c, 8) }
    }

    private fun button(c: Context, text: String, bg: Int, color: Int, onClick: () -> Unit) = TextView(c).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, R.dimen.t_body))
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundResource(bg)
        setPadding(dp(c, 14), 0, dp(c, 14), 0)
        layoutParams = lp(h = dimen(c, R.dimen.tap_min), bottom = dp(c, 12))
        setOnClickListener { onClick() }
    }

    /** Red, like "Start workout". */
    fun primary(c: Context, text: String, onClick: () -> Unit) = button(c, text, R.drawable.bg_button_primary, Color.WHITE, onClick)

    /** Dark pill, like "Add exercise". */
    fun secondary(c: Context, text: String, onClick: () -> Unit) = button(c, text, R.drawable.bg_chip_neutral, FG, onClick)

    /** Red outline, like "Add set". */
    fun accent(c: Context, text: String, onClick: () -> Unit) = button(c, text, R.drawable.bg_chip_on, RED_TEXT, onClick)

    /** Grey text link, like "Discard workout". */
    fun quiet(c: Context, text: String, onClick: () -> Unit) = TextView(c).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(FG2)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, R.dimen.t_body))
        setPadding(0, dp(c, 10), 0, dp(c, 10))
        layoutParams = lp()
        setOnClickListener { onClick() }
    }

    fun chip(c: Context, text: String, on: Boolean, onClick: () -> Unit) = TextView(c).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(if (on) RED_TEXT else FG)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, R.dimen.t_small))
        setTypeface(typeface, if (on) Typeface.BOLD else Typeface.NORMAL)
        setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip_neutral)
        setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(c, 8)
        }
        setOnClickListener { onClick() }
    }

    /** A sideways-scrolling row of chips. */
    fun chipRow(c: Context): Pair<HorizontalScrollView, LinearLayout> {
        val scroll = HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = lp(bottom = dp(c, 12))
        }
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        scroll.addView(row)
        return scroll to row
    }

    fun input(c: Context, hint: String, type: Int = InputType.TYPE_CLASS_TEXT, single: Boolean = true) = EditText(c).apply {
        this.hint = hint
        inputType = type
        setTextColor(FG)
        setHintTextColor(FG2)
        setBackgroundResource(R.drawable.bg_input)
        setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12))
        isSingleLine = single
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, R.dimen.t_body))
        layoutParams = lp(bottom = dp(c, 10))
    }

    fun twoColumn(c: Context, left: String, right: String, bold: Boolean = false, rightColor: Int = SOFT) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(c, 6), 0, dp(c, 6))
        addView(TextView(c).apply {
            text = left
            setTextColor(FG)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, R.dimen.t_body))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }, weight())
        addView(mono(c, right, rightColor))
    }

    /** Number over a small label, used three to a card. */
    fun stat(c: Context, value: String, label: String, color: Int = FG): Pair<LinearLayout, TextView> {
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val n = num(c, value, color)
        col.addView(n)
        col.addView(label(c, label, bottom = 0))
        return col to n
    }

    fun statsCard(c: Context, vararg stats: Pair<String, String>): Pair<LinearLayout, List<TextView>> {
        val card = card(c, bottom = 18, vertical = false).apply { isBaselineAligned = false }
        val values = stats.map { (v, l) ->
            val (col, n) = stat(c, v, l)
            card.addView(col, weight())
            n
        }
        return card to values
    }

    /** Thin rounded bar, like the labors progress bar. */
    fun bar(c: Context, fraction: Float, color: Int = GREEN, height: Int = 6): FrameLayout {
        val track = FrameLayout(c).apply {
            background = GradientDrawable().apply { setColor(TRACK); cornerRadius = dpf(c, 99f) }
            layoutParams = lp(h = dp(c, height))
        }
        val fill = View(c).apply {
            background = GradientDrawable().apply { setColor(color); cornerRadius = dpf(c, 99f) }
        }
        track.addView(fill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
        track.post {
            val w = (track.width * fraction.coerceIn(0f, 1f)).toInt()
            fill.layoutParams = FrameLayout.LayoutParams(if (fraction > 0f) maxOf(w, dp(c, height)) else 0, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        return track
    }

    fun dot(c: Context, color: Int, size: Int = 8) = View(c).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(dp(c, size), dp(c, size)).apply { marginEnd = dp(c, 8) }
    }

    fun spacer(c: Context, h: Int) = View(c).apply { layoutParams = lp(h = dp(c, h)) }

    /** Shows a view as a full-screen Forge-styled dialog. */
    fun showFull(c: Context, view: View): android.app.Dialog {
        val d = android.app.Dialog(c, R.style.Theme_Forge)
        d.setContentView(view)
        d.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            statusBarColor = 0xFF0A0A0A.toInt()
            navigationBarColor = 0xFF0A0A0A.toInt()
            setBackgroundDrawableResource(android.R.color.black)
        }
        d.show()
        return d
    }

    /** Full-screen dialog body: a page with a title row and a close link. */
    fun sheet(c: Context, labelText: String, titleText: String, onClose: () -> Unit): Pair<View, LinearLayout> {
        val (root, content) = page(c)
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(c, labelText, bottom = 0))
        col.addView(title(c, titleText, bottom = 0))
        head.addView(col, weight())
        head.addView(TextView(c).apply {
            text = "Close"
            setTextColor(FG2)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, R.dimen.t_body))
            setPadding(dp(c, 12), dp(c, 8), 0, dp(c, 8))
            setOnClickListener { onClose() }
        })
        head.layoutParams = lp(bottom = dp(c, 16))
        content.addView(head)
        return root to content
    }
}
