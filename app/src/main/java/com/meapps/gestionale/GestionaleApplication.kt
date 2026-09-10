package com.meapps.gestionale

import android.app.Application
import com.meapps.gestionale.data.AppDatabase
import com.meapps.gestionale.data.AppRepository
import com.meapps.gestionale.notifications.NotificationScheduler

class GestionaleApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val scheduler by lazy { NotificationScheduler(this) }
    val repository by lazy { AppRepository(database, scheduler) }
}
