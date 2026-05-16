package com.airouter.di

import android.content.Context
import android.app.Application
import androidx.room.Room
import com.airouter.data.local.db.AppDatabase
import com.airouter.data.local.db.dao.MessageDao
import com.airouter.data.local.db.dao.ProviderConfigDao
import com.airouter.data.local.db.dao.SessionDao
import com.airouter.data.local.prefs.AppConfig
import com.airouter.data.local.AttachmentStorage
import com.airouter.data.repository.ChatRepository
import com.airouter.data.repository.ProviderRepository
import com.airouter.data.repository.SessionRepository
import com.airouter.domain.usecase.SendChatMessageUseCase
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {

    // === AppConfig ===
    single { AppConfig() }

    // === Room Database & DAOs ===
    single<AppDatabase> {
        Room.databaseBuilder(get(), AppDatabase::class.java, "airouter.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()
    }
    single<SessionDao> { get<AppDatabase>().sessionDao() }
    single<MessageDao> { get<AppDatabase>().messageDao() }
    single<ProviderConfigDao> { get<AppDatabase>().providerConfigDao() }

    // === Network ===
    single<Json> {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
    single<OkHttpClient> {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()
    }

    // === Repositories (Singleton) ===
    single { SessionRepository(get(), get(), get()) }
    single { ProviderRepository(get(), get()) }
    single { ChatRepository(get<OkHttpClient>()) }
    single { AttachmentStorage(get<Application>().applicationContext) }

    // === ViewModels ===
    viewModel { (savedStateHandle: androidx.lifecycle.SavedStateHandle) ->
        com.airouter.ui.screen.home.HomeViewModel(get(), get())
    }

    // === UseCases ===
    single { SendChatMessageUseCase(get(), get(), get(), get()) }

    viewModel { (savedStateHandle: androidx.lifecycle.SavedStateHandle) ->
        com.airouter.ui.screen.chat.ChatViewModel(savedStateHandle, get(), get(), get(), get())
    }

    viewModel { (savedStateHandle: androidx.lifecycle.SavedStateHandle) ->
        com.airouter.ui.screen.provider.ProviderListViewModel(get())
    }

    viewModel { (savedStateHandle: androidx.lifecycle.SavedStateHandle) ->
        com.airouter.ui.screen.provider.ProviderEditViewModel(savedStateHandle, get(), get())
    }

    viewModel { (savedStateHandle: androidx.lifecycle.SavedStateHandle) ->
        com.airouter.ui.screen.settings.SettingsViewModel(get())
    }

    viewModel { com.airouter.ui.download.DownloadViewModel(get()) }
}
