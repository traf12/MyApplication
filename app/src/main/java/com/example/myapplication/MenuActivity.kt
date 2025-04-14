package com.example.myapplication

import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.view.accessibility.AccessibilityEvent

class MenuActivity : Activity() {

    private lateinit var gridLayout: GridLayout
    private lateinit var buttons: Array<ImageButton>
    private lateinit var headerTextView: TextView

    private val handler = Handler(Looper.getMainLooper())

    private var selectedIndex = 0

    private val customIcons = listOf(
        R.drawable.ic_slot1, // Звонки
        R.drawable.ic_slot2, // Контакты
        R.drawable.ic_slot3, // Часы
        R.drawable.ic_slot4, // Радио
        R.drawable.ic_slot5, // СМС
        R.drawable.ic_slot6, // Калькулятор
        R.drawable.ic_slot7, // Настройки
        R.drawable.ic_slot8, // Календарь
        R.drawable.ic_slot9  // SIM-меню
    )

    private val customLabels = listOf(
        "Звонки",
        "Контакты",
        "Часы",
        "Радио",
        "СМС",
        "Калькулятор",
        "Настройки",
        "Календарь",
        "SIM-меню"
    )

    private val predefinedPackages = listOf(
        "com.android.dialer",              // Звонки
        "com.android.contacts",            // Контакты
        "com.google.android.clock",        // Часы
        "com.android.fmradio",             // Радио
        "com.android.mms",                 // СМС
        "com.android.calculator",          // Калькулятор
        "com.android.settings",            // Настройки
        "com.android.calendar",            // Календарь
        "com.android.stk"                  // SIM-меню
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        hideSystemUI()

        headerTextView = findViewById(R.id.headerTextView)
        gridLayout = findViewById(R.id.gridLayout)

        buttons = arrayOf(
            findViewById(R.id.appButton1),
            findViewById(R.id.appButton2),
            findViewById(R.id.appButton3),
            findViewById(R.id.appButton4),
            findViewById(R.id.appButton5),
            findViewById(R.id.appButton6),
            findViewById(R.id.appButton7),
            findViewById(R.id.appButton8),
            findViewById(R.id.appButton9)
        )

        buttons.forEachIndexed { index, button ->
            button.isFocusable = true
            button.isFocusableInTouchMode = true
            button.setImageResource(customIcons[index])
            button.setOnClickListener {
                val pkg = predefinedPackages.getOrNull(index)
                pkg?.let { launchApp(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed({
            updateSelection(4)
            buttons[4].sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }, 100)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                val pkg = predefinedPackages.getOrNull(selectedIndex)
                pkg?.let { launchApp(it) }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                val newIndex = if (selectedIndex >= 3) selectedIndex - 3 else selectedIndex + 6
                updateSelection(newIndex)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val newIndex = if (selectedIndex <= 5) selectedIndex + 3 else selectedIndex - 6
                updateSelection(newIndex)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val newIndex = if (selectedIndex % 3 != 0) selectedIndex - 1 else selectedIndex + 2
                updateSelection(newIndex)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val newIndex = if (selectedIndex % 3 != 2) selectedIndex + 1 else selectedIndex - 2
                updateSelection(newIndex)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                intent.putExtra("from_sub_activity", true)
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateSelection(index: Int) {
        buttons[selectedIndex].background = getDrawable(R.drawable.button_selector)
        selectedIndex = index
        buttons[selectedIndex].background = getDrawable(R.drawable.button_selector)
        headerTextView.text = customLabels.getOrNull(index) ?: ""
        buttons[selectedIndex].requestFocus()
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.putExtra("from_sub_activity", true)
            startActivity(intent)
            overridePendingTransition(0, 0)
        } else {
            Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }
}
