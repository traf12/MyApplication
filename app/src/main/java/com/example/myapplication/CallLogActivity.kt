package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.View
import android.widget.*
import android.widget.SimpleAdapter.ViewBinder
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class CallLogActivity : Activity() {

    private lateinit var callList: ListView
    private lateinit var tabIncoming: TextView
    private lateinit var tabOutgoing: TextView
    private lateinit var tabMissed: TextView
    private lateinit var tabRejected: TextView
    private lateinit var tabViews: Array<TextView>
    private var selectedTab = 0
    private var selectedContactIndex = 0
    private val tabTypes = listOf(
        CallLog.Calls.INCOMING_TYPE,
        CallLog.Calls.OUTGOING_TYPE,
        CallLog.Calls.MISSED_TYPE,
        CallLog.Calls.REJECTED_TYPE
    )

    private val REQUEST_CODE_READ_CALL_LOG = 1

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_log)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        window.decorView.setOnTouchListener { _, _ -> true }

        callList = findViewById(R.id.callList)
        tabIncoming = findViewById(R.id.tabIncoming)
        tabOutgoing = findViewById(R.id.tabOutgoing)
        tabMissed = findViewById(R.id.tabMissed)
        tabRejected = findViewById(R.id.tabRejected)

        tabViews = arrayOf(tabIncoming, tabOutgoing, tabMissed, tabRejected)

        val btnOptions: TextView = findViewById(R.id.btnOptions)
        val btnBack: TextView = findViewById(R.id.btnBack)
        val btnCall: TextView = findViewById(R.id.btnCall)

        btnBack.setOnClickListener { finish() }
        btnOptions.setOnClickListener { }
        btnCall.setOnClickListener { initiateCall() }

        tabIncoming.setOnClickListener { onTabSelected(0) }
        tabOutgoing.setOnClickListener { onTabSelected(1) }
        tabMissed.setOnClickListener { onTabSelected(2) }
        tabRejected.setOnClickListener { onTabSelected(3) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), REQUEST_CODE_READ_CALL_LOG)
            }
        } else {
            onTabSelected(0)
        }
    }

    private fun onTabSelected(tabIndex: Int) {
        selectedTab = tabIndex
        highlightSelectedTab()
        loadCallLogs()
        tabViews[selectedTab].isFocusable = true
        tabViews[selectedTab].isFocusableInTouchMode = true
        tabViews[selectedTab].requestFocus()
    }

    private fun loadCallLogs() {
        val list = ArrayList<Map<String, Any>>()
        val selection = "${CallLog.Calls.TYPE} = ?"
        val selectionArgs = arrayOf(tabTypes[selectedTab].toString())

        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val formatter = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
            while (cursor.moveToNext()) {
                val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val name = getContactName(number)
                val date = Date(cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)))
                val typeText = tabViews[selectedTab].text.toString()

                val map = mutableMapOf<String, Any>(
                    "number" to number,
                    "info" to "$typeText — ${formatter.format(date)}",
                    "name" to (name ?: number),
                    "color" to if (name == null) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()
                )
                list.add(map)
            }
        }

        val adapter = SimpleAdapter(
            this, list, android.R.layout.simple_list_item_2,
            arrayOf("name", "info"), intArrayOf(android.R.id.text1, android.R.id.text2)
        )

        adapter.viewBinder = ViewBinder { view, data, _ ->
            if (view.id == android.R.id.text1) {
                (view as TextView).text = data.toString()
                val color = (view.tag as? Int?) ?: 0xFFFFFFFF.toInt()
                view.setTextColor(color)
                return@ViewBinder true
            }
            false
        }

        callList.adapter = adapter
        callList.post {
            selectedContactIndex = 0
            callList.setSelection(0)
        }
    }

    private fun getContactName(phoneNumber: String): String? {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(phoneNumber)
            .build()

        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        return null
    }

    private fun highlightSelectedTab() {
        tabViews.forEachIndexed { index, textView ->
            if (index == selectedTab) {
                textView.setBackgroundColor(0xFF444444.toInt())
                textView.setTextColor(0xFFFFFFFF.toInt())
            } else {
                textView.setBackgroundColor(0xFF222222.toInt())
                textView.setTextColor(0xFFAAAAAA.toInt())
            }
        }
    }

    private fun initiateCall() {
        val item = callList.adapter.getItem(selectedContactIndex) as? Map<String, *>
        val number = item?.get("number") as? String
        Toast.makeText(this, "Вызов: $number", Toast.LENGTH_SHORT).show()
        // Здесь можно вставить реальный интент на вызов
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (selectedTab > 0) {
                    selectedTab--
                    highlightSelectedTab()
                    loadCallLogs()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (selectedTab < tabTypes.lastIndex) {
                    selectedTab++
                    highlightSelectedTab()
                    loadCallLogs()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                initiateCall()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_READ_CALL_LOG -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onTabSelected(0)
                } else {
                    Toast.makeText(this, "Permission denied to read your call log", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
