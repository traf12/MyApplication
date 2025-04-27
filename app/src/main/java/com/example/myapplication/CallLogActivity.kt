package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.View
import android.widget.*
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
        CallLog.Calls.OUTGOING_TYPE,
        CallLog.Calls.INCOMING_TYPE,
        CallLog.Calls.MISSED_TYPE,
        CallLog.Calls.REJECTED_TYPE
    )

    private var optionsDialog: AlertDialog? = null

    private lateinit var btnOptions: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnCall: TextView
    private var confirmationDialog: AlertDialog? = null
    private var dialogYesAction: (() -> Unit)? = null

    private val REQUEST_CODE_READ_CALL_LOG = 1

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
        callList.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                selectedContactIndex = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Ничего не делаем
            }
        })

        tabIncoming = findViewById(R.id.tabIncoming)
        tabOutgoing = findViewById(R.id.tabOutgoing)
        tabMissed = findViewById(R.id.tabMissed)
        tabRejected = findViewById(R.id.tabRejected)

        tabViews = arrayOf(tabIncoming, tabOutgoing, tabMissed, tabRejected)
        tabViews.forEach { it.isFocusable = false }

        btnOptions = findViewById(R.id.btnOptions)
        btnBack = findViewById(R.id.btnBack)
        btnCall = findViewById(R.id.btnCall)

        btnBack.setOnClickListener { finish() }
        btnOptions.setOnClickListener { showOptionsMenu() }
        btnCall.setOnClickListener { initiateCall() }

        tabOutgoing.setOnClickListener { onTabSelected(0) }
        tabIncoming.setOnClickListener { onTabSelected(1) }
        tabMissed.setOnClickListener { onTabSelected(2) }
        tabRejected.setOnClickListener { onTabSelected(3) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), REQUEST_CODE_READ_CALL_LOG)
        } else {
            onTabSelected(0)
            // Отключение системных жестов
            window.decorView.setOnTouchListener { _, _ -> true }

        }
    }

    private fun onTabSelected(tabIndex: Int) {
        selectedTab = tabIndex
        highlightSelectedTab()
        loadCallLogs()
        callList.post {
            callList.requestFocusFromTouch()
            callList.setSelection(0)
        }
    }

    private fun loadCallLogs() {
        val list = ArrayList<MutableMap<String, Any>>()
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
                val date = Date(cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)))
                val typeText = tabViews[selectedTab].text.toString()

                val map = mutableMapOf<String, Any>(
                    "number" to number,
                    "info" to "$typeText — ${formatter.format(date)}",
                    "name" to number,
                    "color" to 0xFF888888.toInt()
                )
                list.add(map)
            }
        }

        val adapter = SimpleAdapter(
            this, list, android.R.layout.simple_list_item_2,
            arrayOf("name", "info"), intArrayOf(android.R.id.text1, android.R.id.text2)
        )

        callList.adapter = adapter
        selectedContactIndex = 0
        callList.setSelection(0)

        Thread {
            list.forEachIndexed { _, map ->
                val name = getContactName(map["number"] as String)
                if (name != null) {
                    map["name"] = name
                    map["color"] = 0xFFFFFFFF.toInt()
                    runOnUiThread { adapter.notifyDataSetChanged() }
                }
            }
        }.start()
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
        val item = callList.adapter.getItem(selectedContactIndex) as? MutableMap<String, Any>
        val number = item?.get("number") as? String
        if (number != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 100)
                return
            }
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        } else {
            Toast.makeText(this, "Номер не найден", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSelectedCall() {
        // Получаем элемент, который выделен в списке
        val item = callList.adapter?.getItem(selectedContactIndex) as? MutableMap<String, Any>
        val number = item?.get("number") as? String ?: return
        confirmDeleteCall(number)
    }
    private fun deleteAllCalls() {
        contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        Toast.makeText(this, "Все вызовы удалены", Toast.LENGTH_SHORT).show()
        loadCallLogs() // Перезагружаем список вызовов
        confirmationDialog?.dismiss() // Закрыть диалог после удаления всех звонков
        optionsDialog?.dismiss() // Закрыть меню опций
    }

    private fun confirmDeleteAllCalls() {
        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(4, Color.GRAY)
            cornerRadius = 12f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = backgroundDrawable
            setPadding(32, 32, 32, 32)

            addView(TextView(this@CallLogActivity).apply {
                text = "Удалить все вызовы?"
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(16, 16, 16, 16)
            })
        }

        btnOptions.text = "Да"
        btnBack.text = "Нет"
        btnOptions.setTextColor(Color.WHITE)
        btnBack.setTextColor(Color.WHITE)

        confirmationDialog = AlertDialog.Builder(this, R.style.DarkDialog)
            .setView(layout)
            .create()

        dialogYesAction = {
            deleteAllCalls()
            confirmationDialog?.dismiss()
        }

        confirmationDialog?.setOnShowListener {
            layout.requestFocus()
            confirmationDialog?.setOnKeyListener { _, keyCode, _ ->
                when (keyCode) {
                    KeyEvent.KEYCODE_MENU -> {
                        dialogYesAction?.invoke()
                        true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        confirmationDialog?.dismiss()
                        true
                    }
                    else -> false
                }
            }
        }

        confirmationDialog?.setOnDismissListener {
            btnOptions.text = "Опции"
            btnBack.text = "Назад"
        }

        confirmationDialog?.show()
    }

    private fun confirmDeleteCall(number: String) {
        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(4, Color.GRAY)
            cornerRadius = 12f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = backgroundDrawable
            setPadding(32, 32, 32, 32)

            addView(TextView(this@CallLogActivity).apply {
                text = "Удалить вызов $number?"
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(16, 16, 16, 16)
            })
        }

        btnOptions.text = "Да"
        btnBack.text = "Нет"
        btnOptions.setTextColor(Color.WHITE)
        btnBack.setTextColor(Color.WHITE)

        confirmationDialog = AlertDialog.Builder(this, R.style.DarkDialog)
            .setView(layout)
            .create()

        dialogYesAction = {
            // Удаляем звонок по номеру
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number),
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))
                    contentResolver.delete(
                        CallLog.Calls.CONTENT_URI,
                        "${CallLog.Calls._ID} = ?",
                        arrayOf(id.toString())
                    )
                    Toast.makeText(this, "$number удалён", Toast.LENGTH_SHORT).show()
                    loadCallLogs() // Перезагружаем список вызовов
                    confirmationDialog?.dismiss() // Закрываем диалог после удаления
                    optionsDialog?.dismiss() // Закрываем меню опций после удаления
                }
            }
        }

        confirmationDialog?.setOnShowListener {
            layout.requestFocus()
            confirmationDialog?.setOnKeyListener { _, keyCode, _ ->
                when (keyCode) {
                    KeyEvent.KEYCODE_MENU -> {
                        dialogYesAction?.invoke()
                        true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        confirmationDialog?.dismiss() // Закрываем диалог при нажатии Back
                        true
                    }
                    else -> false
                }
            }
        }

        confirmationDialog?.setOnDismissListener {
            btnOptions.text = "Опции"
            btnBack.text = "Назад"
        }

        confirmationDialog?.show()
    }


// Обновленный метод для отображения меню опций
private fun showOptionsMenu() {
    val options = arrayOf("Добавить в контакты", "Удалить", "Удалить все")

    val backgroundDrawable = GradientDrawable().apply {
        setColor(Color.BLACK)
        setStroke(4, Color.GRAY)
        cornerRadius = 16f
    }

    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = backgroundDrawable
        setPadding(24, 24, 24, 24)
    }

    val views = options.mapIndexed { index, label ->
        TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundResource(android.R.drawable.list_selector_background)

            setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundColor(if (hasFocus) 0xFFFFA500.toInt() else Color.TRANSPARENT)
            }

            setOnClickListener {
                when (index) {
                    0 -> addNumberToContacts()
                    1 -> deleteSelectedCall()
                    2 -> confirmDeleteAllCalls()
                }
            }
        }
    }

    val adapter = callList.adapter
    val itemCount = adapter?.count ?: 0

    // Проверяем текущий номер
    var numberExistsInContacts = false
    if (itemCount > 0) {
        val item = adapter.getItem(selectedContactIndex) as? MutableMap<String, Any>
        val number = item?.get("number") as? String
        if (number != null) {
            numberExistsInContacts = getContactName(number) != null
        }
    }

    // Делаем пункты неактивными
    if (itemCount == 0) {
        // Если звонков нет — блокируем все пункты
        views.forEach {
            it.isEnabled = false
            it.setTextColor(Color.GRAY)
        }
    } else {
        // Если номер уже есть в контактах — блокируем "Добавить в контакты"
        if (numberExistsInContacts) {
            views[0].isEnabled = false
            views[0].setTextColor(Color.GRAY)
        }
    }

    views.forEach { layout.addView(it) }

    optionsDialog = AlertDialog.Builder(this, R.style.DarkDialog)
        .setView(layout)
        .create()

    optionsDialog?.setOnShowListener {
        val focusView = views.firstOrNull { it.isEnabled } ?: views.firstOrNull()
        focusView?.requestFocus()
    }

    optionsDialog?.show()
}

    private fun addNumberToContacts() {
        val item = callList.adapter.getItem(selectedContactIndex) as? MutableMap<String, Any>
        val number = item?.get("number") as? String
        if (number != null) {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, number)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Номер не найден", Toast.LENGTH_SHORT).show()
        }
        optionsDialog?.dismiss()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                showOptionsMenu()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (selectedTab > 0) {
                    onTabSelected(selectedTab - 1)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (selectedTab < tabTypes.lastIndex) {
                    onTabSelected(selectedTab + 1)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_CALL -> {
                initiateCall()
                return true
            }
        }

        if (confirmationDialog?.isShowing == true) {
            when (keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    dialogYesAction?.invoke()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    confirmationDialog?.dismiss()
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }



}
