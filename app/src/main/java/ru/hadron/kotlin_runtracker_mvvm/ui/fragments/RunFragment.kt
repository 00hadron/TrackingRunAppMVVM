package ru.hadron.kotlin_runtracker_mvvm.ui.fragments

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_run.*
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import ru.hadron.kotlin_runtracker_mvvm.R
import ru.hadron.kotlin_runtracker_mvvm.adapters.RunAdapter
import ru.hadron.kotlin_runtracker_mvvm.db.Run
import ru.hadron.kotlin_runtracker_mvvm.others.Constants.REQUEST_CODE_LOCATION_PERMISSION
import ru.hadron.kotlin_runtracker_mvvm.others.SortType
import ru.hadron.kotlin_runtracker_mvvm.others.TrackingUtility
import ru.hadron.kotlin_runtracker_mvvm.ui.viewmodels.MainViewModel

@AndroidEntryPoint
class RunFragment : Fragment(R.layout.fragment_run), EasyPermissions.PermissionCallbacks {
    private val viewModel: MainViewModel by viewModels()

    private lateinit var runAdapter: RunAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestPermissions()

//----------------------------------------------
        setupRecyclerView()
        when (viewModel.sortType) {
            SortType.DATE -> spFilter.setSelection(0)
            SortType.DISTANCE -> spFilter.setSelection(1)
            SortType.CALORIES_BURNED-> spFilter.setSelection(2)
            SortType.RUNNING_TIME -> spFilter.setSelection(3)
            SortType.AVG_SPEED -> spFilter.setSelection(4)
        }
        spFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { /*NO-OP*/ }

            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View?,
                pos: Int,
                id: Long
            ) {
                when (pos) {
                    0 -> viewModel.sortRuns(sortType = SortType.DATE)
                    1 -> viewModel.sortRuns(sortType = SortType.DISTANCE)
                    2 -> viewModel.sortRuns(sortType = SortType.CALORIES_BURNED)
                    3 -> viewModel.sortRuns(sortType = SortType.RUNNING_TIME)
                    4 -> viewModel.sortRuns(sortType = SortType.AVG_SPEED)
                }
            }
        }
        viewModel.runs.observe(viewLifecycleOwner, Observer {
            runAdapter.submitList(it)
        })
//-----------------------------------------------

        fab.setOnClickListener {
            findNavController().navigate(R.id.action_runFragment_to_trackingFragment)
        }
    }

    /**
     * Запрашивает разрешения, если нужно.
     */
    private fun requestPermissions() {
        if (TrackingUtility.hasLocationPermissions(requireContext())) { return }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.you_need_to_accept_permissions),
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.you_need_to_accept_permissions),
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
    }

    /**
     * impl EasyPermissions.PermissionCallbacks
     */
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
       if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
           AppSettingsDialog.Builder(this).build().show()
       } else {
           requestPermissions()
       }
    }

    /**
     * impl EasyPermissions.PermissionCallbacks
     */
    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) { /*NO-OP*/ }

    /**
     * Стандартный хендлер для разрешений.
     * Вызвать библиотеку  EasyPermissions.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        EasyPermissions.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults,
            this
        )
    }

    private fun setupRecyclerView() = rvRuns.apply {
        runAdapter = RunAdapter()
        adapter = runAdapter
        layoutManager = LinearLayoutManager(requireContext())
    }
}