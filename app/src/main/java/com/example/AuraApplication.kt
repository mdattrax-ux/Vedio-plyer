package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MediaRepository

class AuraApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var mediaRepository: MediaRepository
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        preferencesRepository = UserPreferencesRepository(this)
        mediaRepository = MediaRepository(this, database)
    }

    companion object {
        lateinit var instance: AuraApplication
            private set
    }
}
