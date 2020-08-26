package ru.hadron.kotlin_runtracker_mvvm.repositories

import ru.hadron.kotlin_runtracker_mvvm.db.Run
import ru.hadron.kotlin_runtracker_mvvm.db.RunDao
import javax.inject.Inject

class MainRepository @Inject constructor(
    val runDao: RunDao
) {
    /*run*/
    suspend fun insertRun(run: Run) = runDao.insertRun(run)
    suspend fun deleteRun(run: Run) = runDao.deleteRun(run)
    fun getAllRunsSortedByDate() = runDao.getAllRunsSortedByDate()
    fun getAllRunsSortedByAvrSpeed() = runDao.getAllRunsSortedByAvrSpeedInKMH()
    fun getAllRunsSortedByDistance() = runDao.getAllRunsSortedByDistanceInMeters()
    fun getAllRunsSortedByTimes() = runDao.getAllRunsSortedByTimesInMillis()
    fun getAllRunsSortedByCaloriesBurned() = runDao.getAllRunsSortedByCaloriesBurned()
    /*statistics*/
    fun getTotalTime() = runDao.getTotalTimeInMillis()
    fun getTotalCaloriesBurned() = runDao.getTotalCaloriesBurned()
    fun getTotalDistance() = runDao.getTotalDistanceInMeters()
    fun getTotalAvrSpeed() = runDao.getTotalAvrSpeedInKMH()
}