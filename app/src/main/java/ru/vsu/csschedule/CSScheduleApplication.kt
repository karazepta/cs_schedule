package ru.vsu.csschedule

import android.app.Application
import androidx.room.Room
import okhttp3.OkHttpClient
import ru.vsu.csschedule.data.importing.RealSheetManualMapper
import ru.vsu.csschedule.data.importing.ScheduleSourceClient
import ru.vsu.csschedule.data.importing.SheetManualMapper
import ru.vsu.csschedule.data.local.AppDatabase
import ru.vsu.csschedule.data.preferences.ThemePreferencesRepository
import ru.vsu.csschedule.data.repository.ScheduleImportRepository
import ru.vsu.csschedule.data.repository.ScheduleRepository
import java.util.concurrent.TimeUnit

class CSScheduleApplication : Application() {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "cs_schedule.db",
        ).fallbackToDestructiveMigration(true).build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val sheetManualMapper: SheetManualMapper by lazy {
        RealSheetManualMapper()
    }

    val themePreferencesRepository: ThemePreferencesRepository by lazy {
        ThemePreferencesRepository(this)
    }

    val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(database.scheduleDao())
    }

    val scheduleImportRepository: ScheduleImportRepository by lazy {
        ScheduleImportRepository(
            scheduleDao = database.scheduleDao(),
            scheduleSourceClient = ScheduleSourceClient(okHttpClient),
            sheetManualMapper = sheetManualMapper,
            context = this,
        )
    }
}
