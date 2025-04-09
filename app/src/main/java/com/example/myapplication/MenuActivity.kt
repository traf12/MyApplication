package com.example.myapplication

import android.app.Activity
import android.app.AlertDialog
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

    private var isBackHandled = false
    private var isCenterHandled = false
    private var wasBackLongPress = false
    private var wasCenterLongPress = false

    private val HOLD_THRESHOLD = 1500L

    private val buttonAppMap = mutableMapOf<Int, String?>()
    private var selectedIndex = 0

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
            button.setOnClickListener {
                buttonAppMap[index]?.let { pkg -> launchApp(pkg) }
            }
        }

        loadAppMappings()
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
            KeyEvent.KEYCODE_BACK -> {
                if (!isBackHandled) {
                    isBackHandled = true
                    handler.postDelayed({
                        if (buttonAppMap[selectedIndex] != null) {
                            handleLongPress(selectedIndex)
                            wasBackLongPress = true
                        }
                        isBackHandled = false
                    }, HOLD_THRESHOLD)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (!isCenterHandled) {
                    isCenterHandled = true
                    handler.postDelayed({
                        showAppSelectionDialog(selectedIndex)
                        wasCenterLongPress = true
                        isCenterHandled = false
                    }, HOLD_THRESHOLD)
                }
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
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                handler.removeCallbacksAndMessages(null)
                if (!wasBackLongPress) {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                }
                wasBackLongPress = false
                isBackHandled = false
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                handler.removeCallbacksAndMessages(null)
                if (!wasCenterLongPress) {
                    buttonAppMap[selectedIndex]?.let { launchApp(it) }
                }
                wasCenterLongPress = false
                isCenterHandled = false
                return true
            }
        }

        return super.onKeyUp(keyCode, event)
    }

    private fun updateSelection(index: Int) {
        buttons[selectedIndex].background = getDrawable(R.drawable.button_selector)
        selectedIndex = index
        buttons[selectedIndex].background = getDrawable(R.drawable.button_selector)

        val label = buttonAppMap[index]?.let {
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString()
            } catch (e: Exception) {
                ""
            }
        } ?: ""

        headerTextView.text = label
        buttons[selectedIndex].requestFocus()
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
            overridePendingTransition(0, 0)
        } else {
            Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppSelectionDialog(index: Int) {
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val appPackages = apps.map { it.packageName }
        val appNames = apps.map {
            packageManager.getApplicationLabel(it).toString()
        }

        AlertDialog.Builder(this)
            .setTitle("Выберите приложение")
            .setItems(appNames.toTypedArray()) { _, which ->
                val selectedPackage = appPackages[which]
                buttonAppMap[index] = selectedPackage
                saveAppMappings()
                try {
                    val icon = packageManager.getApplicationIcon(selectedPackage)
                    buttons[index].setImageDrawable(icon)
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка загрузки иконки", Toast.LENGTH_SHORT).show()
                }
                updateSelection(index)
            }
            .show()
    }

    private fun handleLongPress(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("Удалить ярлык?")
            .setMessage("Удалить назначенное приложение?")
            .setPositiveButton("Удалить") { _, _ ->
                buttonAppMap[index] = null
                buttons[index].setImageDrawable(null)
                saveAppMappings()
                if (selectedIndex == index) {
                    headerTextView.text = ""
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun saveAppMappings() {
        val prefs = getSharedPreferences("app_mappings", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        buttonAppMap.forEach { (index, pkg) ->
            if (pkg != null) {
                editor.putString("button_$index", pkg)
            } else {
                editor.remove("button_$index")
            }
        }
        editor.apply()
    }

    private fun loadAppMappings() {
        val prefs = getSharedPreferences("app_mappings", Context.MODE_PRIVATE)
        for (i in buttons.indices) {
            val pkg = prefs.getString("button_$i", null)
            buttonAppMap[i] = pkg
            if (pkg != null) {
                try {
                    val icon = packageManager.getApplicationIcon(pkg)
                    buttons[i].setImageDrawable(icon)
                } catch (e: Exception) {
                    buttonAppMap[i] = null
                    buttons[i].setImageDrawable(null)
                }
            } else {
                buttons[i].setImageDrawable(null)
            }
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
