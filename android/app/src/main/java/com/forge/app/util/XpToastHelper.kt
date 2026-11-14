package com.forge.app.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.forge.app.R

object XpToastHelper {

    fun showXpToast(activity: Activity, amount: Int, source: String) {
        try {
            val inflater = LayoutInflater.from(activity)
            val toastView = inflater.inflate(R.layout.view_xp_toast, null)

            val tvAmount = toastView.findViewById<TextView>(R.id.tv_xp_toast_amount)
            val tvSource = toastView.findViewById<TextView>(R.id.tv_xp_toast_source)

            tvAmount.text = "+$amount XP"
            tvSource.text = "Task Completed"

            // Add to window
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 120
            }

            val windowManager = activity.windowManager
            windowManager.addView(toastView, params)

            // Animate in
            toastView.translationY = -200f
            toastView.alpha = 0f

            val slideIn = ObjectAnimator.ofFloat(toastView, View.TRANSLATION_Y, -200f, 0f)
            val fadeIn = ObjectAnimator.ofFloat(toastView, View.ALPHA, 0f, 1f)
            val scaleX = ObjectAnimator.ofFloat(toastView, View.SCALE_X, 0.5f, 1f)
            val scaleY = ObjectAnimator.ofFloat(toastView, View.SCALE_Y, 0.5f, 1f)

            val animIn = AnimatorSet()
            animIn.playTogether(slideIn, fadeIn, scaleX, scaleY)
            animIn.duration = 400
            animIn.interpolator = OvershootInterpolator(1.2f)
            animIn.start()

            // Animate out after delay
            Handler(Looper.getMainLooper()).postDelayed({
                val slideOut = ObjectAnimator.ofFloat(toastView, View.TRANSLATION_Y, 0f, -200f)
                val fadeOut = ObjectAnimator.ofFloat(toastView, View.ALPHA, 1f, 0f)

                val animOut = AnimatorSet()
                animOut.playTogether(slideOut, fadeOut)
                animOut.duration = 300
                animOut.start()

                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        windowManager.removeView(toastView)
                    } catch (_: Exception) {}
                }, 350)
            }, 1500)
        } catch (_: Exception) {
            // Silently fail if window operations fail
        }
    }
}
