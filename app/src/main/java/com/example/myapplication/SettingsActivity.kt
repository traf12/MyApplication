package com.example.myapplication

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*

class SettingsActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("lockscreen_prefs", Context.MODE_PRIVATE)
    }

    private val keys = listOf("*", "#", "0", "Назад")
    private val settingsItems = listOf("Кнопка разблокировки")

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>

    private val focusedDrawable by lazy {
        GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(4, Color.CYAN)
            cornerRadius = 12f
        }
    }

    private val defaultDrawable by lazy {
        GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        hideSystemUI()
        disableTouch()

        listView = findViewById(R.id.settingsListView)
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setBackgroundColor(Color.BLACK)

        val currentKey = prefs.getString("unlock_key_2", "*") ?: "*"
        val rows = settingsItems.map { "$it: $currentKey" }.toMutableList()

        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = super.getView(position, convertView, parent) as TextView
                tv.setTextColor(Color.WHITE)
                tv.textSize = 20f
                tv.setPadding(40, 30, 40, 30)
                tv.background = defaultDrawable
                tv.isFocusable = true
                tv.isFocusableInTouchMode = true

                tv.setOnFocusChangeListener { v, hasFocus ->
                    v.background = if (hasFocus) focusedDrawable else defaultDrawable
                }
                return tv
            }
        }

        listView.adapter = adapter

        listView.post {
            listView.setItemChecked(0, true)
            listView.requestFocusFromTouch()
        }

        // Открываем диалог по клику
        listView.setOnItemClickListener { _, _, position, _ ->
            if (settingsItems[position] == "Кнопка разблокировки") {
                showKeyDialog(position)
            }
        }

        // Открываем диалог по DPad Center (KEYCODE_DPAD_CENTER или KEYCODE_ENTER)
        listView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                val pos = listView.checkedItemPosition.takeIf { it >= 0 } ?: 0
                if (settingsItems[pos] == "Кнопка разблокировки") {
                    showKeyDialog(pos)
                    true
                } else false
            } else false
        }
    }

    private fun showKeyDialog(rowPos: Int) {
        val current = prefs.getString("unlock_key_2", "*") ?: "*"
        val currentIdx = keys.indexOf(current).takeIf { it >= 0 } ?: 0

        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(4, Color.GRAY)
            cornerRadius = 12f
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = backgroundDrawable
            setPadding(24, 24, 24, 24)
        }

        val textViews = mutableListOf<TextView>()

        keys.forEachIndexed { index, key ->
            val tv = TextView(this).apply {
                text = key
                textSize = 20f
                setPadding(30, 30, 30, 30)

                if (index == currentIdx) {
                    // Неактивный пункт
                    isEnabled = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setTextColor(Color.GRAY)
                    setBackgroundColor(Color.TRANSPARENT)
                } else {
                    // Активные пункты
                    isEnabled = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setTextColor(Color.WHITE)
                    setBackgroundResource(android.R.drawable.list_selector_background)

                    setOnClickListener {
                        prefs.edit().putString("unlock_key_2", key).apply()
                        updateRow(rowPos, key)
                        dialog?.dismiss()
                    }
                }
            }
            textViews.add(tv)
            container.addView(tv)
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .create()

        this.dialog = dialog

        dialog.setOnShowListener {
            // Фокус ставим на первый активный элемент (т.е. не на текущий выбранный)
            val firstFocusable = textViews.firstOrNull { it.isEnabled }
            firstFocusable?.requestFocus()
        }

        dialog.setOnDismissListener {
            this.dialog = null
        }

        dialog.show()
    }

    private var dialog: AlertDialog? = null

    private fun updateRow(pos: Int, selected: String) {
        adapter.remove(adapter.getItem(pos))
        adapter.insert("${settingsItems[pos]}: $selected", pos)
        adapter.notifyDataSetChanged()
        listView.setItemChecked(pos, true)
    }

    override fun onBackPressed() = finish()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_SOFT_LEFT -> {
            val pos = listView.checkedItemPosition.takeIf { it >= 0 } ?: 0
            if (settingsItems[pos] == "Кнопка разблокировки") {
                showKeyDialog(pos)
                true
            } else false
        }
        KeyEvent.KEYCODE_SOFT_RIGHT -> {
            finish()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    private fun hideSystemUI() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
    }

    private fun disableTouch() {
        window.decorView.setOnTouchListener { _, _ -> true }
    }
}
