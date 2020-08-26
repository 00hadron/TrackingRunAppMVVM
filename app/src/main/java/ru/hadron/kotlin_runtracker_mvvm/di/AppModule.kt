package ru.hadron.kotlin_runtracker_mvvm.di

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.hadron.kotlin_runtracker_mvvm.db.RunningDatabase
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.KEY_FIRST_TIME_TOGGLE
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.KEY_NAME
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.KEY_WEIGHT
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.RUNNING_DATABASE_NAME
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.SHARED_PREFERENCES_NAME
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideRunningDatabase(
        @ApplicationContext app: Context
    ) = Room.databaseBuilder(
        app,
        RunningDatabase::class.java,
        RUNNING_DATABASE_NAME
    ).build()

    @Provides
    @Singleton
    fun provideRunDao(db: RunningDatabase) = db.getRunDao()

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext app: Context
    ) = app.getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideName(sharedPref: SharedPreferences) =
        sharedPref.getString(KEY_NAME, "")?: ""

    @Provides
    @Singleton
    fun provideWeight(sharedPref: SharedPreferences) =
        sharedPref.getFloat(KEY_WEIGHT, 80f)

    @Provides
    @Singleton
    fun provideFirstTimeToggle(sharedPref: SharedPreferences) =
        sharedPref.getBoolean(KEY_FIRST_TIME_TOGGLE, true)
}