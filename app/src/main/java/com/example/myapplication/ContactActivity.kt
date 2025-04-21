package com.example.myapplication

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class ContactActivity : AppCompatActivity() {

    private lateinit var contactListView: ListView
    private val contacts = mutableListOf<Pair<String, String>>()
    private var filteredContacts = mutableListOf<Pair<String, String>>()
    private lateinit var adapter: ArrayAdapter<String>

    private val t9Map = mapOf(
        KeyEvent.KEYCODE_2 to "абвг",
        KeyEvent.KEYCODE_3 to "дежз",
        KeyEvent.KEYCODE_4 to "ийкл",
        KeyEvent.KEYCODE_5 to "мноп",
        KeyEvent.KEYCODE_6 to "рсту",
        KeyEvent.KEYCODE_7 to "фхцч",
        KeyEvent.KEYCODE_8 to "шщъы",
        KeyEvent.KEYCODE_9 to "ьэюя"
    )

    private var currentKey = -1
    private var currentIndex = 0
    private val t9Handler = Handler(Looper.getMainLooper())
    private var t9Runnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact)

        contactListView = findViewById(R.id.contactListView)

        loadContacts()

        contactListView.setOnItemClickListener { _, _, position, _ ->
            if (position >= 0 && position < filteredContacts.size) {
                openContactCard(filteredContacts[position])
            }
        }

        contactListView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && filteredContacts.isNotEmpty()) {
                contactListView.setSelection(0)
            }
        }

        disableTouchInput()
        hideStatusBar()
    }

    private fun loadContacts() {
        contacts.clear()
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val number = it.getString(numberIndex)
                contacts.add(name to number)
            }
        }

        filteredContacts = contacts.toMutableList()
        updateList()
    }

    private fun updateList() {
        val names = filteredContacts.map { it.first }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        contactListView.adapter = adapter

        if (filteredContacts.isNotEmpty()) {
            contactListView.post {
                contactListView.setSelection(0)
                contactListView.requestFocusFromTouch()
            }
        } else {
            contactListView.clearFocus()
        }
    }


    private fun filterContactsByT9Input(input: String) {
        filteredContacts = contacts.filter {
            it.first.lowercase().startsWith(input) || it.second.startsWith(input)
        }.toMutableList()
        updateList()
    }

    private fun showT9Popup(char: String) {
        val popup = findViewById<TextView>(R.id.letterPopup)
        popup.text = char
        popup.visibility = View.VISIBLE

        t9Runnable?.let { t9Handler.removeCallbacks(it) }
        t9Runnable = Runnable {
            popup.visibility = View.GONE
            currentKey = -1
            currentIndex = 0
        }.also {
            t9Handler.postDelayed(it, 2000)
        }
    }

    private fun openContactCard(contact: Pair<String, String>) {
        val (name, number) = contact

        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(4, Color.GRAY)
            cornerRadius = 12f
        }

        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24)
            background = backgroundDrawable

            val editButton = Button(this@ContactActivity).apply {
                text = "Изменить"
                isFocusableInTouchMode = true
                requestFocus()
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_EDIT).apply {
                        data = Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_URI,
                            getContactId(name)
                        )
                    }
                    startActivity(intent)
                }
            }

            addView(TextView(this@ContactActivity).apply {
                text = "Имя: $name"
                textSize = 20f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@ContactActivity).apply {
                text = "Номер: $number"
                textSize = 18f
                setTextColor(Color.WHITE)
            })
            addView(editButton)
            addView(Button(this@ContactActivity).apply {
                text = "Позвонить"
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
                }
            })
            addView(Button(this@ContactActivity).apply {
                text = "Сообщение"
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sms:$number")))
                }
            })
        }

        AlertDialog.Builder(this, R.style.DarkDialog)
            .setView(view)
            .create()
            .show()
    }

    private fun getContactId(name: String): String? {
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME} = ?",
            arrayOf(name),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(it.getColumnIndex(ContactsContract.Contacts._ID))
            }
        }
        return null
    }

    private fun showOptionsMenu() {
        val items = arrayOf("Добавить", "Удалить", "Источники", "Импорт/Экспорт")

        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(4, Color.GRAY)
            cornerRadius = 12f
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = backgroundDrawable
            setPadding(24)
        }

        val textViews = mutableListOf<TextView>()

        items.forEachIndexed { index, item ->
            val textView = TextView(this).apply {
                text = item
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(16, 16, 16, 16)
                isFocusable = true
                isFocusableInTouchMode = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    when (index) {
                        0 -> startActivity(Intent(Intent.ACTION_INSERT).apply {
                            type = ContactsContract.Contacts.CONTENT_TYPE
                        })
                        1 -> {
                            val pos = contactListView.selectedItemPosition
                            if (pos >= 0 && pos < filteredContacts.size) {
                                val (name, _) = filteredContacts[pos]
                                AlertDialog.Builder(this@ContactActivity, R.style.DarkDialog)
                                    .setTitle("Удалить контакт?")
                                    .setMessage("Вы уверены, что хотите удалить $name?")
                                    .setPositiveButton("Да") { _, _ -> deleteContact(name) }
                                    .setNegativeButton("Нет", null)
                                    .show()
                            } else {
                                Toast.makeText(this@ContactActivity, "Контакт не выбран", Toast.LENGTH_SHORT).show()
                            }
                        }
                        2 -> showSourcesDialog()
                        3 -> showImportExportDialog()
                    }
                }
            }
            textViews.add(textView)
            container.addView(textView)
        }

        val dialog = AlertDialog.Builder(this, R.style.DarkDialog)
            .setView(container)
            .create()

        dialog.setOnShowListener {
            textViews.firstOrNull()?.requestFocus()
        }

        dialog.show()
    }

    private fun showSourcesDialog() {
        val sources = arrayOf("SIM", "Телефон", "Google")
        val checked = booleanArrayOf(true, true, false)

        AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Источники")
            .setMultiChoiceItems(sources, checked) { _, _, _ -> }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showImportExportDialog() {
        AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Импорт/Экспорт")
            .setItems(arrayOf("Импорт с SIM", "Экспорт на SIM")) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Импорт с SIM пока не реализован", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "Экспорт на SIM пока не реализован", Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun deleteContact(name: String) {
        val id = getContactId(name) ?: return
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id)
        contentResolver.delete(uri, null, null)
        Toast.makeText(this, "$name удалён", Toast.LENGTH_SHORT).show()
        loadContacts()
    }

    private fun disableTouchInput() {
        window.decorView.setOnTouchListener { _, _ -> true }
    }

    private fun hideStatusBar() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (t9Map.containsKey(keyCode)) {
            if (keyCode == currentKey) {
                currentIndex = (currentIndex + 1) % (t9Map[keyCode]?.length ?: 1)
            } else {
                currentKey = keyCode
                currentIndex = 0
            }
            val letters = t9Map[keyCode] ?: ""
            val char = letters[currentIndex].toString()
            filterContactsByT9Input(char)
            showT9Popup(char)
            return true
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                val pos = contactListView.selectedItemPosition
                if (pos >= 0 && pos < filteredContacts.size) {
                    openContactCard(filteredContacts[pos])
                    true
                } else false
            }
            KeyEvent.KEYCODE_CALL -> {
                val pos = contactListView.selectedItemPosition
                if (pos >= 0 && pos < filteredContacts.size) {
                    val number = filteredContacts[pos].second
                    try {
                        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                        startActivity(intent)
                    } catch (e: SecurityException) {
                        Toast.makeText(this, "Нет разрешения на звонок", Toast.LENGTH_SHORT).show()
                    }
                    true
                } else false
            }
            KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU -> {
                showOptionsMenu(); true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish(); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}