package ru.hadron.kotlin_runtracker_mvvm.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.hadron.kotlin_runtracker_mvvm.R
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.ACTION_PAUSE_SERVICE
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.ACTION_SHOW_TRACKING_FRAGMENT
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.ACTION_START_OR_RESUME_SERVICE
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.ACTION_STOP_SERVICE
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.FASTEST_LOCATION_INTERVAL
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.LOCATION_UPDATE_INTERVAL
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.NOTIFICATION_CHANNEL_ID
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.NOTIFICATION_CHANNEL_NAME
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.NOTIFICATION_ID
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.TIMER_UPDATE_INTERVAL
import ru.hadron.kotlin_runtracker_mvvm.others.TrackingUtility
import ru.hadron.kotlin_runtracker_mvvm.ui.MainActivity
import timber.log.Timber
import javax.inject.Inject

typealias Polyline = MutableList<LatLng>
typealias Polylines = MutableList<Polyline>

/**
 * Чтобы мог передать instance  как owner, наследуется именно от LifecycleService()
 */
@AndroidEntryPoint
class TrackingService : LifecycleService() {

    var isFirstRun = true

    /**для отображения времени в notifications, чтобы оно не так часто обновлялось**/
    private var timeRunInSeconds = MutableLiveData<Long>()

    /**для наблюдения снаружи**/
    companion object {
        val isTracking = MutableLiveData<Boolean>()
       // val pathPoints = MutableLiveData<MutableList<MutableList<LatLng>>>()
        val pathPoints = MutableLiveData<Polylines>()

        val timeRunInMillis = MutableLiveData<Long>()
    }

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    @Inject
    lateinit var baseNotificationBuilder: NotificationCompat.Builder
    lateinit var curNotificationBuilder: NotificationCompat.Builder

    var serviceKilled = false

    /**
     * Вызывается, когда шлем intent сервису.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    if (isFirstRun) {
                        startForegroundService()
                        isFirstRun = false
                    } else {
                        Timber.d("resuming service....")
                        startTimer()
                    }
                }
                ACTION_PAUSE_SERVICE -> {
                    Timber.d("pause service")
                    pauseService()
                }
                ACTION_STOP_SERVICE -> {
                    Timber.d ("stop service")
                    killService()
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }


    private fun startForegroundService() {
       // addEmptyPolyline()
        startTimer()

        isTracking.postValue(true)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }
/* --DI-- baseNotificationBuilder
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_directions_run_black_24dp)
            .setContentText("00:00:00")
            .setContentIntent(getMainActivityPendingIntent())*/

        startForeground(NOTIFICATION_ID, baseNotificationBuilder.build())

        timeRunInSeconds.observe(this, Observer {
            if (!serviceKilled) {
                val notification = curNotificationBuilder
                    .setContentText(TrackingUtility.getFormattedStopWatchTime(ms = it*1000L))

                notificationManager.notify(NOTIFICATION_ID, notification.build())
            }
        })
    }

/*--DI--, эта функция inject в baseNotificationBuilder
        (чтобы открыть не main activity при нажатии на notification, прикрепить action к простому intent)
    private fun getMainActivityPendingIntent() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).also {
            it.action = ACTION_SHOW_TRACKING_FRAGMENT },
        FLAG_UPDATE_CURRENT
    )*/

    /**
     * Инициализация.
     */
    private fun postInitialValue() {
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())

        timeRunInSeconds.postValue(0L)
        timeRunInMillis.postValue(0L)
    }

    /**
     * Добавляет пустой polyline, куда позже будут записаны координаты.
     */
    private fun addEmptyPolyline() = pathPoints.value?.apply {
        add(mutableListOf())
        pathPoints.postValue(this)
    } ?: pathPoints.postValue(mutableListOf(mutableListOf()))

    /**
     * Добавляет координаты в последний (пустой) polyline.
     */
    private fun addPathPoint(location: Location?) {
        location?.let {
            val pos = LatLng(location.latitude, location.longitude)
            pathPoints.value?.apply {
                last().add(pos)
                pathPoints.postValue(this)
            }
        }
    }

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult?) {
            super.onLocationResult(result)
            if (isTracking.value!!) {
                result?.locations?.let {locations ->
                    for (location in locations) {
                        addPathPoint(location)

                        Timber.d("NEW LOCATION: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        }
    }

    /**
     * Обновляет location.
     * Если location tracking остановлен, location не должен обновляться.
     * @SuppressLint("MissingPermission") из-за использования EasyPermission вместо стандартной.
     */
    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking: Boolean) {
        if (isTracking) {
            if (TrackingUtility.hasLocationPermissions(this)) {
                val request = LocationRequest().apply {
                    interval = LOCATION_UPDATE_INTERVAL
                    fastestInterval = FASTEST_LOCATION_INTERVAL
                    priority = PRIORITY_HIGH_ACCURACY
                }

                fusedLocationProviderClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } else {
                fusedLocationProviderClient.removeLocationUpdates(locationCallback)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        curNotificationBuilder = baseNotificationBuilder

        postInitialValue()

        fusedLocationProviderClient = FusedLocationProviderClient(this)

        isTracking.observe(this, Observer {
            updateLocationTracking(it)
            updateNotificationTrackingState(it)
        })
    }

    private fun pauseService() {
        isTracking.postValue(false)
        isTimerEnable = false
    }

    private var isTimerEnable = false
    private var lapTime = 0L
    private var timeRun = 0L
    private var timeStarted = 0L
    private var lastSecondTimestamp = 0L

    /**
     * Стартует таймер.
     */
    private fun startTimer() {
        addEmptyPolyline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnable = true

        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value!!) {
                lapTime = System.currentTimeMillis() - timeStarted
                timeRunInMillis.postValue(timeRun + lapTime)

                if (timeRunInMillis.value!! >= lastSecondTimestamp + 1000L) {
                    timeRunInSeconds.postValue(timeRunInSeconds.value!! + 1)
                    lastSecondTimestamp += 1000L
                }
                delay(TIMER_UPDATE_INTERVAL)
            }
            timeRun += lapTime
        }
    }

    /**
     * Обновляет notification. Перед обновлением, удаляет все action.
     */
    private fun updateNotificationTrackingState(isTracking: Boolean) {
        val notificationActionText = if (isTracking) "Pause" else "Resume"

        val pendingIntent = if (isTracking) {
            val pauseIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            PendingIntent.getService(this, 1, pauseIntent, FLAG_UPDATE_CURRENT)
        } else {
            val resumeIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_START_OR_RESUME_SERVICE
            }
            PendingIntent.getService(this, 2, resumeIntent, FLAG_UPDATE_CURRENT)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        curNotificationBuilder.javaClass.getDeclaredField("mActions").apply {
            isAccessible = true
            set(curNotificationBuilder, ArrayList<NotificationCompat.Action>())
        }

        if (!serviceKilled) {
            curNotificationBuilder = baseNotificationBuilder
                .addAction(R.drawable.ic_pause_black_24dp, notificationActionText, pendingIntent)

            notificationManager.notify(NOTIFICATION_ID, curNotificationBuilder.build())
        }

    }

    private fun killService() {
        serviceKilled = true
        isFirstRun = true
        pauseService()
        postInitialValue()
        stopForeground(true)
        stopSelf()
    }
}