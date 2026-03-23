package com.hyundaicompanion.bluelinktasker

import android.app.Application

class BlueLinkApp : Application() {
    lateinit var repository: BlueLinkRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BlueLinkRepository.create(this)
    }
}
