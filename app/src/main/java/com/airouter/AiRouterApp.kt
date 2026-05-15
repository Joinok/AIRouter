package com.airouter

import android.app.Application
import com.airouter.di.appModule
import com.tencent.mmkv.MMKV
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AiRouterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        MMKV.initialize(this)
        startKoin {
            androidContext(this@AiRouterApp)
            modules(appModule)
        }
    }

    companion object {
        lateinit var INSTANCE: AiRouterApp
            private set
    }
}
