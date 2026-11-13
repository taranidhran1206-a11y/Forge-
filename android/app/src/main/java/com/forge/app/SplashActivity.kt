package com.forge.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val bootLines = listOf(
        Pair("[forge] booting system...", 0),
        Pair("[core] loading enforcers...", 400),
        Pair("[xp] restoring progress...", 800),
        Pair("[ai] initializing engine...", 1200),
        Pair("[rize] time tracker ready...", 1600),
        Pair("[sys] device: mobile", 2000),
        Pair("[forge] ready.", 2300)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Full screen dark
        window.statusBarColor = 0xFF0A0A0A.toInt()
        window.navigationBarColor = 0xFF0A0A0A.toInt()
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        val container = findViewById<LinearLayout>(R.id.ll_boot_lines)
        val enterButton = findViewById<TextView>(R.id.tv_enter_forge)

        // Animate boot lines one by one
        bootLines.forEachIndexed { index, (text, delay) ->
            handler.postDelayed({
                val lineView = TextView(this).apply {
                    this.text = text
                    textSize = 14f
                    typeface = android.graphics.Typeface.MONOSPACE
                    if (text.contains("ready.")) {
                        setTextColor(0xFFF5F5F5.toInt())
                    } else {
                        setTextColor(0xFF9E9E9E.toInt())
                    }
                    setPadding(0, 4, 0, 4)
                    visibility = View.INVISIBLE
                }
                container.addView(lineView)

                // Fade in
                val fadeIn = AlphaAnimation(0f, 1f).apply {
                    duration = 200
                    fillAfter = true
                }
                lineView.visibility = View.VISIBLE
                lineView.startAnimation(fadeIn)

                // Add OK label after a brief delay (except for sys line and ready line)
                if (!text.contains("[sys]") && !text.contains("ready.") && !text.contains("booting")) {
                    handler.postDelayed({
                        val okView = TextView(this).apply {
                            this.text = "OK"
                            textSize = 14f
                            typeface = android.graphics.Typeface.MONOSPACE
                            setTextColor(0xFF4CAF50.toInt())
                        }
                        // Replace text to include OK
                        lineView.text = "$text OK"
                        val spannable = android.text.SpannableString(lineView.text)
                        val okStart = spannable.length - 2
                        spannable.setSpan(
                            android.text.style.ForegroundColorSpan(0xFF4CAF50.toInt()),
                            okStart, spannable.length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        lineView.setText(spannable, TextView.BufferType.SPANNABLE)
                    }, 200)
                }

                // Show ENTER THE FORGE button after last line
                if (index == bootLines.lastIndex) {
                    handler.postDelayed({
                        enterButton.visibility = View.VISIBLE
                        val fadeInBtn = AlphaAnimation(0f, 1f).apply {
                            duration = 400
                            fillAfter = true
                        }
                        enterButton.startAnimation(fadeInBtn)

                        // Also auto-navigate after 1.5s
                        handler.postDelayed({
                            navigateToMain()
                        }, 1500)
                    }, 500)
                }
            }, delay.toLong())
        }

        enterButton.setOnClickListener {
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        handler.removeCallbacksAndMessages(null)
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
