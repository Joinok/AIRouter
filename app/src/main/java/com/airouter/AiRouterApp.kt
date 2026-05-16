package com.airouter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.airouter.di.appModule
import com.airouter.domain.provider.ProviderFactory
import com.tencent.mmkv.MMKV
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AiRouterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        MMKV.initialize(this)
        createNotificationChannel()
        startKoin {
            androidContext(this@AiRouterApp)
            modules(appModule)
        }
        ProviderFactory.initDefaults()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "模型下载",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示模型下载进度"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        lateinit var INSTANCE: AiRouterApp
            private set

        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1001
    }
}
