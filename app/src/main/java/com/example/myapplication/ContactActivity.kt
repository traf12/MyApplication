package com.example.myapplication

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.setPadding
import java.io.File
import android.widget.Toast
import androidx.core.content.FileProvider


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

    private var confirmationDialog: AlertDialog? = null
    private var dialogYesAction: (() -> Unit)? = null

    private var currentKey = -1
    private var currentIndex = 0
    private val t9Handler = Handler(Looper.getMainLooper())
    private var t9Runnable: Runnable? = null

    private lateinit var letterPopup: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact)

        contactListView = findViewById(R.id.contactListView)
        letterPopup = findViewById(R.id.letterPopup)

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

            val callButton = Button(this@ContactActivity).apply {
                text = "Позвонить"
                isFocusableInTouchMode = true
                setOnClickListener {
                    if (ActivityCompat.checkSelfPermission(
                            this@ContactActivity, Manifest.permission.CALL_PHONE
                        ) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                            this@ContactActivity, arrayOf(Manifest.permission.CALL_PHONE), 1
                        )
                    } else {
                        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
                    }
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
            addView(callButton)
            addView(Button(this@ContactActivity).apply {
                text = "Сообщение"
                isFocusableInTouchMode = true
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
        val items = arrayOf("Добавить", "Удалить", "Удалить все")

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
        val menuLabel = findViewById<TextView>(R.id.menuLabel)
        val backLabel = findViewById<TextView>(R.id.contactsLabel)

        // Применяем белый цвет для текста кнопок "Да" и "Нет"
        menuLabel.setTextColor(Color.WHITE)
        backLabel.setTextColor(Color.WHITE)

        items.forEachIndexed { index, item ->
            val textView = TextView(this).apply {
                text = item
                textSize = 18f
                setTextColor(Color.WHITE)  // Белый цвет текста
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
                            menuLabel.text = "Да"
                            backLabel.text = "Нет"
                            val pos = contactListView.selectedItemPosition
                            if (pos >= 0 && pos < filteredContacts.size) {
                                val (name, _) = filteredContacts[pos]
                                confirmDeleteContact(name)
                            } else {
                                Toast.makeText(this@ContactActivity, "Контакт не выбран", Toast.LENGTH_SHORT).show()
                            }
                        }
                        2 -> {
                            menuLabel.text = "Да"
                            backLabel.text = "Нет"
                            confirmDeleteAllContacts()
                        }
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

        dialog.setOnDismissListener {
            // После закрытия диалога восстанавливаем текст кнопок
            menuLabel.text = "Опции"
            backLabel.text = "Назад"
        }

        dialog.show()
    }

    private fun confirmDeleteContact(name: String) {
        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(4, Color.GRAY)
            cornerRadius = 12f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = backgroundDrawable
            setPadding(32)

            addView(TextView(this@ContactActivity).apply {
                text = "Удалить контакт $name?"
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(16, 16, 16, 16)
            })
        }

        // Явно устанавливаем белый цвет для кнопок "Да" и "Нет"
        val menuLabel = findViewById<TextView>(R.id.menuLabel)
        val backLabel = findViewById<TextView>(R.id.contactsLabel)
        menuLabel.setTextColor(Color.WHITE)
        backLabel.setTextColor(Color.WHITE)
        menuLabel.text = "Да"
        backLabel.text = "Нет"

        confirmationDialog = AlertDialog.Builder(this, R.style.DarkDialog)
            .setView(layout)
            .create()

        dialogYesAction = {
            val id = getContactId(name)
            if (id != null) {
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id)
                contentResolver.delete(uri, null, null)
                Toast.makeText(this, "$name удалён", Toast.LENGTH_SHORT).show()
                loadContacts()
            }
            confirmationDialog?.dismiss()
        }

        confirmationDialog?.setOnShowListener {
            layout.requestFocus()

            confirmationDialog?.setOnKeyListener { _, keyCode, _ ->
                when (keyCode) {
                    KeyEvent.KEYCODE_MENU -> {
                        dialogYesAction?.invoke()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        confirmationDialog?.dismiss()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        confirmationDialog?.setOnDismissListener {
            menuLabel.text = "Опции"
            backLabel.text = "Назад"
        }

        confirmationDialog?.show()
    }

    private fun confirmDeleteAllContacts() {
        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(4, Color.GRAY)
            cornerRadius = 12f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = backgroundDrawable
            setPadding(32)

            addView(TextView(this@ContactActivity).apply {
                text = "Удалить все контакты?"
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(16, 16, 16, 16)
            })
        }

        // Белые кнопки "Да" и "Нет"
        val menuLabel = findViewById<TextView>(R.id.menuLabel)
        val backLabel = findViewById<TextView>(R.id.contactsLabel)
        menuLabel.setTextColor(Color.WHITE)
        backLabel.setTextColor(Color.WHITE)
        menuLabel.text = "Да"
        backLabel.text = "Нет"

        confirmationDialog = AlertDialog.Builder(this, R.style.DarkDialog)
            .setView(layout)
            .create()

        dialogYesAction = {
            contentResolver.delete(ContactsContract.Contacts.CONTENT_URI, null, null)
            Toast.makeText(this, "Все контакты удалены", Toast.LENGTH_SHORT).show()
            loadContacts()
            confirmationDialog?.dismiss()
        }

        confirmationDialog?.setOnShowListener {
            layout.requestFocus()

            confirmationDialog?.setOnKeyListener { _, keyCode, _ ->
                when (keyCode) {
                    KeyEvent.KEYCODE_MENU -> {
                        dialogYesAction?.invoke()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        confirmationDialog?.dismiss()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        confirmationDialog?.setOnDismissListener {
            menuLabel.text = "Опции"
            backLabel.text = "Назад"
        }

        confirmationDialog?.show()
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

    private fun filterContactsByT9Input(input: String) {
        filteredContacts = contacts.filter {
            it.first.firstOrNull()?.equals(input.firstOrNull() ?: ' ', ignoreCase = true) == true
        }.toMutableList()
        updateList()
    }


    private fun showT9Popup(char: String) {
        letterPopup.text = char
        letterPopup.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            letterPopup.visibility = View.GONE
        }, 1000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            val pos = contactListView.selectedItemPosition
            if (pos >= 0 && pos < filteredContacts.size) {
                val (_, number) = filteredContacts[pos]
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.CALL_PHONE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.CALL_PHONE), 1
                    )
                } else {
                    startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
                }
            } else {
                Toast.makeText(this, "Контакт не выбран", Toast.LENGTH_SHORT).show()
            }
            return true
        }

        // Открытие меню по кнопке "Menu"
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showOptionsMenu()
            return true
        }

        if (t9Map.containsKey(keyCode)) {
            val letters = t9Map[keyCode]!!
            if (currentKey == keyCode) {
                currentIndex = (currentIndex + 1) % letters.length
            } else {
                currentKey = keyCode
                currentIndex = 0
            }
            val currentLetter = letters[currentIndex].toString()
            showT9Popup(currentLetter)
            filterContactsByT9Input(currentLetter)
            return true
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
