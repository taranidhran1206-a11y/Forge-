package com.forge.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.forge.app.R

object NotificationHelper {

    private const val CHANNEL_GENERAL = "forge_notifications"
    private const val CHANNEL_XP = "forge_xp"
    private const val CHANNEL_STREAK = "forge_streak"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "Forge Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General Forge notifications"
            }

            val xpChannel = NotificationChannel(
                CHANNEL_XP,
                "XP Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "XP earned notifications"
            }

            val streakChannel = NotificationChannel(
                CHANNEL_STREAK,
                "Streak Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Streak milestone notifications"
            }

            nm.createNotificationChannels(listOf(generalChannel, xpChannel, streakChannel))
        }
    }

    fun showXpNotification(context: Context, amount: Int, source: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_XP)
            .setSmallIcon(R.drawable.ic_xp_star)
            .setContentTitle("⭐ +$amount XP")
            .setContentText("Earned from: ${source.replaceFirstChar { it.uppercase() }}")
            .setColor(0xFFE53935.toInt())
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showStreakNotification(context: Context, streak: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_STREAK)
            .setSmallIcon(R.drawable.ic_streak_flame)
            .setContentTitle("🔥 $streak Day Streak!")
            .setContentText("Keep the forge burning. Don't break the chain.")
            .setColor(0xFFFFB300.toInt())
            .setAutoCancel(true)
            .build()

        nm.notify(2000 + streak, notification)
    }
}
