package ru.hadron.kotlin_runtracker_mvvm.ui.fragments

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.hadron.kotlin_runtracker_mvvm.R

class CancelTrackingDialog : DialogFragment() {
    private var yesListener: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(getString(R.string.dialog_cancel_the_run_q))
            .setMessage(getString(R.string.dialog_cancel_the_run_message_q))
            .setIcon(R.drawable.ic_delete)
            .setPositiveButton("Yes") {_, _ ->
               yesListener?.let { yes -> yes() } //stopRun()
            }
            .setNegativeButton("No") {dialogInterface, _ ->
                dialogInterface.cancel()}
            .create()

    }
    fun setYesListener(listener: () -> Unit) {
        yesListener = listener
    }
}