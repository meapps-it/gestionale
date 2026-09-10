package com.meapps.gestionale.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meapps.gestionale.GestionaleApplication
import com.meapps.gestionale.backup.BackupManager
import com.meapps.gestionale.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as GestionaleApplication).repository
    val backup = BackupManager(app, repository)
    val entries = repository.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { viewModelScope.launch { repository.ensureSeeded() } }
    fun reminders(id: Long) = repository.reminders(id)
    fun history(id: Long) = repository.history(id)
    fun save(draft: EntryDraft, done: () -> Unit) = viewModelScope.launch { repository.save(draft); done() }
    fun complete(item: EntryWithCategory) = viewModelScope.launch { repository.complete(item) }
    fun renew(item: EntryWithCategory, date: Long, amount: Long?) = viewModelScope.launch { repository.renew(item,date,amount) }
    fun delete(item: EntryWithCategory, done: () -> Unit) = viewModelScope.launch { repository.delete(item); done() }
    fun addCategory(name: String, icon: String="●") = viewModelScope.launch { if(name.isNotBlank()) repository.addCategory(name,icon) }
}
