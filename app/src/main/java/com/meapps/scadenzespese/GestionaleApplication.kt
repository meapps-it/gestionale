package com.meapps.scadenzespese

import android.app.Application
import com.meapps.scadenzespese.data.AppDatabase
import com.meapps.scadenzespese.data.AppRepository
import com.meapps.scadenzespese.notifications.NotificationScheduler

class GestionaleApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val scheduler by lazy { NotificationScheduler(this) }
    val repository by lazy { AppRepository(database, scheduler) }
}
