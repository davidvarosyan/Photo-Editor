package com.varos.imageenhance

import android.app.Application
import com.varos.imageenhance.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Starts Koin with the single [appModule] composition root. */
class ImageEnhanceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ImageEnhanceApp)
            modules(appModule)
        }
    }
}
