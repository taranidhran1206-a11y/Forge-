package com.forge.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.Labor
import com.forge.app.databinding.ActivityMainBinding
import com.forge.app.data.model.RankSystem
import com.forge.app.util.WidgetHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ForgeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Dark status & nav bars
        window.statusBarColor = 0xFF0A0A0A.toInt()
        window.navigationBarColor = 0xFF0A0A0A.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(0, 0)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize database and repository
        val db = ForgeDatabase.getDatabase(applicationContext)
        repository = ForgeRepository(db)

        // Create notification channels
        createNotificationChannels()

        // Set up navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        // Seed default labors on first run
        val prefs = getSharedPreferences("forge_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("labors_seeded", false)) {
            seedDefaultLabors()
            prefs.edit().putBoolean("labors_seeded", true).apply()
        }

        // Update app bar XP and rank
        lifecycleScope.launch {
            repository.getTotalXp().collectLatest { xp ->
                val rank = RankSystem.getRank(xp)
                binding.tvAppbarXp.text = "$xp XP"
                binding.tvAppbarRank.text = rank.title
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Update widgets when app goes to background
        lifecycleScope.launch(Dispatchers.IO) {
            WidgetHelper.updateAllWidgets(this@MainActivity, repository)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // General notifications
            val generalChannel = NotificationChannel(
                "forge_notifications",
                "Forge Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General Forge notifications"
            }
            notificationManager.createNotificationChannel(generalChannel)

            // Time tracking
            val trackingChannel = NotificationChannel(
                "forge_time_tracking",
                "Time Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active time tracking notification"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(trackingChannel)

            // XP notifications
            val xpChannel = NotificationChannel(
                "forge_xp",
                "XP Earned",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "XP earned notifications"
            }
            notificationManager.createNotificationChannel(xpChannel)
        }
    }

    private fun seedDefaultLabors() {
        lifecycleScope.launch(Dispatchers.IO) {
            val today = repository.getTodayDate()
            val defaultLabors = listOf(
                Labor(
                    name = "Morning Brief",
                    emoji = "🌅",
                    description = "Review today's plan",
                    time = "06:00",
                    timeOfDay = "MORNING",
                    xpReward = 10,
                    date = today,
                    sortOrder = 0
                ),
                Labor(
                    name = "Meditate",
                    emoji = "🧘",
                    description = "10 minutes mindfulness",
                    time = "06:30",
                    timeOfDay = "MORNING",
                    xpReward = 15,
                    date = today,
                    sortOrder = 1
                ),
                Labor(
                    name = "Exercise",
                    emoji = "💪",
                    description = "Workout session",
                    time = "07:00",
                    timeOfDay = "MORNING",
                    xpReward = 20,
                    date = today,
                    sortOrder = 2
                ),
                Labor(
                    name = "Deep Work Block",
                    emoji = "⚔️",
                    description = "Main task focus",
                    time = "09:00",
                    timeOfDay = "AFTERNOON",
                    xpReward = 25,
                    date = today,
                    sortOrder = 3
                ),
                Labor(
                    name = "Learn",
                    emoji = "📚",
                    description = "Study or read",
                    time = "14:00",
                    timeOfDay = "AFTERNOON",
                    xpReward = 20,
                    date = today,
                    sortOrder = 4
                ),
                Labor(
                    name = "Admin",
                    emoji = "📧",
                    description = "Emails and comms",
                    time = "16:00",
                    timeOfDay = "AFTERNOON",
                    xpReward = 10,
                    date = today,
                    sortOrder = 5
                ),
                Labor(
                    name = "Evening Reflection",
                    emoji = "📝",
                    description = "Journal and reflect",
                    time = "20:00",
                    timeOfDay = "EVENING",
                    xpReward = 15,
                    date = today,
                    sortOrder = 6
                ),
                Labor(
                    name = "Shield Wall",
                    emoji = "🛡️",
                    description = "Review and plan tomorrow",
                    time = "21:00",
                    timeOfDay = "EVENING",
                    xpReward = 15,
                    date = today,
                    sortOrder = 7
                ),
                Labor(
                    name = "Wind Down",
                    emoji = "😴",
                    description = "Prepare for rest",
                    time = "22:00",
                    timeOfDay = "EVENING",
                    xpReward = 10,
                    date = today,
                    sortOrder = 8
                )
            )
            defaultLabors.forEach { repository.insertLabor(it) }
        }
    }
}
