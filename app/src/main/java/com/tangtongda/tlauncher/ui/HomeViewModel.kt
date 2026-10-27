package com.tangtongda.tlauncher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tangtongda.tlauncher.data.AppInfo
import com.tangtongda.tlauncher.data.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)

    /** null = still loading */
    val apps: StateFlow<List<AppInfo>?> = repo.observeApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun launch(app: AppInfo) {
        repo.launch(app.packageName)
    }
}
