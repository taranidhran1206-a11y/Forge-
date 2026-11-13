package com.forge.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.ui.today.TodayViewModel
import com.forge.app.ui.plan.PlanViewModel
import com.forge.app.ui.progress.ProgressViewModel
import com.forge.app.ui.reflect.ReflectViewModel
import com.forge.app.ui.coach.CoachViewModel

class ForgeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = ForgeDatabase.getDatabase(application)
        val repository = ForgeRepository(db)
        return when {
            modelClass.isAssignableFrom(TodayViewModel::class.java) -> TodayViewModel(repository) as T
            modelClass.isAssignableFrom(PlanViewModel::class.java) -> PlanViewModel(repository) as T
            modelClass.isAssignableFrom(ProgressViewModel::class.java) -> ProgressViewModel(repository) as T
            modelClass.isAssignableFrom(ReflectViewModel::class.java) -> ReflectViewModel(repository) as T
            modelClass.isAssignableFrom(CoachViewModel::class.java) -> CoachViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
