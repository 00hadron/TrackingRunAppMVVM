package ru.hadron.kotlin_runtracker_mvvm.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_tracking.*
import ru.hadron.kotlin_runtracker_mvvm.R
import ru.hadron.kotlin_runtracker_mvvm.db.Run
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.ACTION_PAUSE_SERVICE
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.ACTION_START_OR_RESUME_SERVICE
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.ACTION_STOP_SERVICE
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.MAP_ZOOM
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.POLYLINE_COLOR
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.POLYLINE_WIDTH
import ru.hadron.kotlin_runtracker_mvvm.others.TrackingUtility
import ru.hadron.kotlin_runtracker_mvvm.services.Polyline
import ru.hadron.kotlin_runtracker_mvvm.services.TrackingService
import ru.hadron.kotlin_runtracker_mvvm.ui.viewmodels.MainViewModel
import java.lang.Math.round
import java.util.*
import javax.inject.Inject

const val CANCEL_TRACKING_DIALOG_TAG = "CancelDialog"

@AndroidEntryPoint
class TrackingFragment : Fragment(R.layout.fragment_tracking) {

    private val viewModel: MainViewModel by viewModels()
    private var map: GoogleMap? = null

    private var isTracking = false
    private var pathPoints = mutableListOf<Polyline>()

    private var curTimeInMillis = 0L

    private var menu: Menu? = null

    @set: Inject
    var weight = 80f

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            val cancelTrackingDialog = parentFragmentManager.findFragmentByTag(
                CANCEL_TRACKING_DIALOG_TAG) as CancelTrackingDialog?
            cancelTrackingDialog?.setYesListener { stopRun() }
        }

        mapView?.onCreate(savedInstanceState)


        btnToggleRun.setOnClickListener {
            //sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
            toggleRun()
        }

        btnFinishRun.setOnClickListener {
            zoomToSeeWholeTrack()
            endRunAndSaveToDb()
        }

        /** Загрузка карты.
         * Т.к. MapView, а не MapFragment, переопрелить все методы lifecycle
         */
        mapView.getMapAsync {
            map = it

            addAllPolylines()
        }

        subscribeToObservers()


    }

    /**
     * Отправляет intent сервису.
     */
    private fun sendCommandToService(action: String) =
        Intent(requireContext(), TrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }


    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    /**
     * Чтобы карта не грузилась каждый раз, кеширвать ее в onSaveInstanceState.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    /**
     * Соединяет точки на карте каждый раз, когда изменяется список.
     */
    private fun addLatestPolyline() {
        if (pathPoints.isNotEmpty() && pathPoints.last().size > 1) {
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
            val lastLatLng = pathPoints.last().last()
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng)
                .add(lastLatLng)
            map?.addPolyline(polylineOptions)
        }
    }

    /**
     * Перерисовывает сразу все линии (напр, при повороте экрана).
     */
    private fun addAllPolylines() {
        for (polyline in pathPoints) {
            val polylineOptions =  PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyline)
            map?.addPolyline(polylineOptions)

        }
    }

    /**
     * Передвинуть камеру в местоположение пользователя на карте.
     */
    private fun moveCameraToUser() {
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    MAP_ZOOM
                )
            )
        }
    }

    /**
     * Управляет изменениями графических компонентов.
     */
    private fun updateTracking(isTracking: Boolean) {
        this.isTracking = isTracking
        if (!isTracking && curTimeInMillis > 0L) {
            btnToggleRun.text = "start"
            btnFinishRun.visibility = View.VISIBLE

            menu?.getItem(0)?.isVisible = true
        } else if (isTracking) {
            btnToggleRun.text = "stop"
            btnFinishRun.visibility = View.GONE
        }
    }

    /**
     * Функциональность кнопки.
     */
    private fun toggleRun() {
        if (isTracking) {
            menu?.getItem(0)?.isVisible = true

            sendCommandToService(ACTION_PAUSE_SERVICE)
        } else {
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
        }
    }

    /**
     * Подписки на обновления, подтягивания данных из сервиса, обработка.
     */
    private fun subscribeToObservers() {
        TrackingService.isTracking.observe(viewLifecycleOwner, Observer {
            updateTracking(it)
        })

        TrackingService.pathPoints.observe(viewLifecycleOwner, Observer {
            pathPoints = it
            addLatestPolyline()
            moveCameraToUser()
        })

        TrackingService.timeRunInMillis.observe(viewLifecycleOwner, Observer {
            curTimeInMillis = it
           // println(curTimeInMillis)
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(
                ms =  curTimeInMillis,
                includeMillis = true)

            tvTimer.text = formattedTime
        })
    }
//---------------------------------------------------------
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.toolbar_tracking_menu, menu)
        this.menu = menu
    }

    /**
     * Если начался трекинг, то появляется кнопка меню Х
     */
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        if (curTimeInMillis > 0L) {
            this.menu?.getItem(0)?.isVisible = true
        }
    }

    /**
     * Показывает диалог для отмены трекинга.
     * Передает функцию stopRun в CancelTrackingDialog.
     */
    private fun showCancelTrackingDialog() {
 /*       val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setTitle("Cancel the Run?")
            .setMessage("Are you sure to cancel current Run and delete all it's data?")
            .setIcon(R.drawable.ic_delete)
            .setPositiveButton("Yes") {_, _ ->
                stopRun()}
            .setNegativeButton("No") {dialogInterface, _ ->
                dialogInterface.cancel()}
            .create()

        dialog.show()*/

        CancelTrackingDialog().apply {
            setYesListener { stopRun() }
        }.show(parentFragmentManager, CANCEL_TRACKING_DIALOG_TAG)
    }

    /**
     * Остановить run
     */
    private fun stopRun() {
        tvTimer.text = "00:00:00:00"
        sendCommandToService(ACTION_STOP_SERVICE)
        findNavController().navigate(R.id.action_trackingFragment_to_runFragment2)
    }

    /**
     * обработка нажатия на menu item (Х)
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.miCancelTracking -> {showCancelTrackingDialog()}
        }
        return super.onOptionsItemSelected(item)
    }
    //-----------------------------------------------

    private fun zoomToSeeWholeTrack() {
        val bounds = LatLngBounds.Builder()
        for (polyline in pathPoints) {
            for (pos in polyline) {
                bounds.include(pos)
            }
        }

        map?.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                mapView.width,
                mapView.height,
                (mapView.height * 0.05f).toInt()
            )
        )
    }


    private fun endRunAndSaveToDb() {
        map?.snapshot { bmp ->
            var distanceInMeters = 0
            for (polyline in pathPoints) {
                distanceInMeters = TrackingUtility.calculatePolylineLength(polyline).toInt()
            }

            val avgSpeed = round((distanceInMeters / 1000f) / (curTimeInMillis / 1000f / 60 / 60) * 10) / 10f
            val dateTimeStamp = Calendar.getInstance().timeInMillis
            val caloriesBurned = ((distanceInMeters / 1000f) * weight).toInt()
            val run = Run(
                bmp,
                dateTimeStamp,
                avgSpeed,
                distanceInMeters,
                curTimeInMillis,
                caloriesBurned
            )

            viewModel.insertRun(run)
            Snackbar.make(
                requireActivity().findViewById(R.id.rootView),
                getString(R.string.run_saved_successfully),
                Snackbar.LENGTH_LONG
            ).show()

            stopRun()
        }
    }
}
