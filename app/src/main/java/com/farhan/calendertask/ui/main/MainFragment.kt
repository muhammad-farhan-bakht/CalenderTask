package com.farhan.calendertask.ui.main

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.farhan.calendertask.databinding.FragmentMainBinding
import com.farhan.calendertask.ui.main.viewmodel.MainViewModel
import java.util.*
import kotlin.collections.ArrayList


private const val TAG = "MainFragment"

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val viewModel by viewModels<MainViewModel>()
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var eventID: ArrayList<Long> = arrayListOf()
    private val calID: Long = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addPermissionListener()
        setUpListeners()
    }

    private fun setUpListeners() {
        binding.btnAddEvent.setOnClickListener {
            if (checkPermissions()) {
                addEventInCalendar()
            }
        }

        binding.btnDeleteEvent.setOnClickListener {
            if (checkPermissions()) {
                if (eventID.isNotEmpty()) {
                    eventID.forEach {
                        val deleteUri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it)
                        requireActivity().contentResolver.delete(deleteUri, null, null)
                        updateCalendarDb(it)
                        syncCalendarsOne()
                    }

                    Toast.makeText(requireContext(), "Event Deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Event Id is null", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addEventInCalendar() {

        var count = 0
        while (count < 3) {

            val startMillis: Long = Calendar.getInstance().run {
                set(2022, 9, 18, (9..17).random(), 0)
                timeInMillis
            }
            val endMillis: Long = Calendar.getInstance().run {
                set(2022, 9, 18, (9..17).random(), 30)
                timeInMillis
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "Test Event Title")
                put(CalendarContract.Events.DESCRIPTION, "Test Event Description")
                put(CalendarContract.Events.CALENDAR_ID, calID)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri: Uri? =
                requireActivity().applicationContext.contentResolver.insert(
                    CalendarContract.Events.CONTENT_URI,
                    values
                )

            // get the event ID that is the last element in the Uri
            uri?.lastPathSegment?.toLong()?.let {
                eventID.add(it)
                updateCalendarDb(it)
                syncCalendarsOne()
            }
            Toast.makeText(requireContext(), "Id:$eventID - Event Created", Toast.LENGTH_SHORT)
                .show()

            count++
        }

    }

    private fun updateCalendarDb(_eventId: Long?) {
        if (_eventId != null) {
            val updateUri: Uri =
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, _eventId)
            requireActivity().applicationContext.contentResolver.update(
                updateUri,
                ContentValues(),
                null,
                null
            )

            val values2 = ContentValues()
            values2.put(Calendars.SYNC_EVENTS, 1)
            values2.put(Calendars.VISIBLE, 1)
            requireActivity().applicationContext.contentResolver.update(
                ContentUris.withAppendedId(
                    Calendars.CONTENT_URI,
                    calID
                ), values2, null, null
            )
        }
    }

    private fun syncCalendarsOne() {
        val extras = Bundle()
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        requireActivity().applicationContext.contentResolver.startSync(
            CalendarContract.Events.CONTENT_URI,
            extras
        )
        syncCalendarsTwo()
    }

    private fun syncCalendarsTwo() {
        val accounts = AccountManager.get(context).accounts
        Log.d(TAG, "Refreshing " + accounts.size + " accounts")
        val authority = Calendars.CONTENT_URI.authority
        for (i in accounts.indices) {
            Log.d(TAG, "Refreshing calendars for: " + accounts[i])
            val extras = Bundle()
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            ContentResolver.requestSync(accounts[i], authority, extras)
        }
    }

    private fun addPermissionListener() {
        // Setup permission request launcher
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it == true) {
                    Toast.makeText(
                        requireContext(),
                        "Permission Granted! You can Now Add the Event",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Please allow this app to access your calendar",
                        Toast.LENGTH_SHORT
                    ).show()
                    checkPermissions()
                }
            }
    }

    private fun checkPermissions(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}