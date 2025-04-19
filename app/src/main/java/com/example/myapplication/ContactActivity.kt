package com.example.myapplication

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class ContactActivity : AppCompatActivity() {

    private lateinit var contactListView: ListView
    private lateinit var searchView: EditText
    private lateinit var menuLabel: TextView
    private lateinit var backLabel: TextView

    private val contacts = mutableListOf<Pair<String, String>>() // Имя - номер
    private var filteredContacts = mutableListOf<Pair<String, String>>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact)

        contactListView = findViewById(R.id.contactListView)
        menuLabel = findViewById(R.id.menuLabel)
        backLabel = findViewById(R.id.contactsLabel)

        loadContacts()

        menuLabel.setOnClickListener { showOptionsMenu() }
        backLabel.setOnClickListener { finish() }

        contactListView.setOnItemClickListener { _, _, position, _ ->
            openContactCard(filteredContacts[position])
        }

        contactListView.requestFocusFromTouch()
        contactListView.setSelection(0)

        disableTouchInput()
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
    }

    private fun setupSearch() {
        searchView.setOnKeyListener { _, _, _ ->
            filterContacts()
            false
        }
        searchView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                contactListView.clearFocus()
            }
        }
    }

    private fun filterContacts() {
        val query = searchView.text.toString().lowercase()
        filteredContacts = contacts.filter {
            it.first.lowercase().contains(query)
        }.toMutableList()
        updateList()
    }

    private fun openContactCard(contact: Pair<String, String>) {
        val (name, number) = contact
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24)
            addView(TextView(this@ContactActivity).apply {
                text = "Имя: $name"
                textSize = 20f
                setTextColor(resources.getColor(android.R.color.white))
            })
            addView(Button(this@ContactActivity).apply {
                text = "Изменить"
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_EDIT).apply {
                        data = Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_URI,
                            getContactId(name)
                        )
                    }
                    startActivity(intent)
                }
            })
            addView(TextView(this@ContactActivity).apply {
                text = "Номер: $number"
                textSize = 18f
                setTextColor(resources.getColor(android.R.color.white))
            })
            addView(Button(this@ContactActivity).apply {
                text = "Позвонить"
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    startActivity(intent)
                }
            })
            addView(Button(this@ContactActivity).apply {
                text = "Сообщение"
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("sms:$number")
                    }
                    startActivity(intent)
                }
            })
        }

        AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Контакт")
            .setView(view)
            .setPositiveButton("OK", null)
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
        AlertDialog.Builder(this, R.style.DarkDialog)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(Intent.ACTION_INSERT).apply {
                        type = ContactsContract.Contacts.CONTENT_TYPE
                    })
                    1 -> Toast.makeText(this, "Удаление реализовать", Toast.LENGTH_SHORT).show()
                    2 -> showSourcesDialog()
                    3 -> Toast.makeText(this, "Импорт/экспорт реализовать", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
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

    private fun disableTouchInput() {
        window.decorView.setOnTouchListener { _, _ -> true }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                val pos = contactListView.selectedItemPosition
                if (pos >= 0 && pos < filteredContacts.size) {
                    openContactCard(filteredContacts[pos])
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
