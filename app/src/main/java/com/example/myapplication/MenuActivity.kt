package com.example.myapplication

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.Toast

class MenuActivity : Activity() {

    private lateinit var gridLayout: GridLayout
    private var selectedButtonIndex = 4  // Центральная кнопка (appButton5)
    private lateinit var buttons: Array<Button>
    private lateinit var installedApps: List<String>
    private lateinit var appIcons: Array<Drawable?>
    private val buttonAppMap = mutableMapOf<Int, String?>() // Для хранения привязанных приложений

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        hideSystemUI()  // Скрытие системной шторки

        gridLayout = findViewById(R.id.gridLayout)

        buttons = arrayOf(
            findViewById<Button>(R.id.appButton1),
            findViewById<Button>(R.id.appButton2),
            findViewById<Button>(R.id.appButton3),
            findViewById<Button>(R.id.appButton4),
            findViewById<Button>(R.id.appButton5),  // Центральная кнопка
            findViewById<Button>(R.id.appButton6),
            findViewById<Button>(R.id.appButton7),
            findViewById<Button>(R.id.appButton8),
            findViewById<Button>(R.id.appButton9)
        )

        installedApps = getInstalledApps()

        // Загружаем иконки приложений
        appIcons = Array(9) { null }
        installedApps.forEachIndexed { index, packageName ->
            if (index < 9) {
                appIcons[index] = packageManager.getApplicationIcon(packageName)
                buttons[index].setCompoundDrawablesWithIntrinsicBounds(null, appIcons[index], null, null)
            }
        }

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                val appPackage = buttonAppMap[index]
                if (appPackage != null) {
                    launchApp(appPackage)
                }
            }

            button.setOnLongClickListener {
                showAppSelectionDialog(index) // Показать диалог для выбора приложения
                true
            }
        }

        // Устанавливаем центральную кнопку как выбранную изначально
        updateSelectedButton(selectedButtonIndex)
    }

    private fun getInstalledApps(): List<String> {
        val packageManager: PackageManager = packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.map { it.packageName }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Приложение не найдено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppSelectionDialog(index: Int) {
        // Показываем диалог для выбора приложения
        val packageManager = packageManager
        val apps = getInstalledApps()
        val appNames = apps.map { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString() }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Выберите приложение")
            .setItems(appNames.toTypedArray()) { _, which ->
                // Привязываем выбранное приложение к кнопке
                val selectedAppPackage = apps[which]
                buttonAppMap[index] = selectedAppPackage
                Toast.makeText(this, "Приложение $selectedAppPackage добавлено", Toast.LENGTH_SHORT).show()

                // Обновляем иконку и название кнопки
                val appIcon = packageManager.getApplicationIcon(selectedAppPackage)
                val appLabel = packageManager.getApplicationLabel(packageManager.getApplicationInfo(selectedAppPackage, 0)).toString()
                buttons[index].setText(appLabel)
                buttons[index].setCompoundDrawablesWithIntrinsicBounds(null, appIcon, null, null)
            }
            .create()

        dialog.show()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                // Открыть приложение при удержании кнопки
                buttons[selectedButtonIndex].performClick()
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                val newIndex = if (selectedButtonIndex > 2) selectedButtonIndex - 3 else selectedButtonIndex + 6
                updateSelectedButton(newIndex)
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val newIndex = if (selectedButtonIndex < 6) selectedButtonIndex + 3 else selectedButtonIndex - 6
                updateSelectedButton(newIndex)
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val newIndex = if (selectedButtonIndex % 3 != 0) selectedButtonIndex - 1 else selectedButtonIndex + 2
                updateSelectedButton(newIndex)
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val newIndex = if (selectedButtonIndex % 3 != 2) selectedButtonIndex + 1 else selectedButtonIndex - 2
                updateSelectedButton(newIndex)
                return true
            }

            KeyEvent.KEYCODE_SOFT_LEFT -> {
                finish()
                return true
            }

            // Удержание кнопки 7 для открытия списка приложений
            KeyEvent.KEYCODE_7 -> {
                openAllApps()
                return true
            }

            else -> return super.onKeyDown(keyCode, event)
        }
    }

    private fun updateSelectedButton(newIndex: Int) {
        // Снимаем выделение с предыдущей кнопки
        buttons[selectedButtonIndex].isSelected = false
        buttons[selectedButtonIndex].setBackgroundResource(0) // Убираем эффект выделения

        // Обновляем индекс
        selectedButtonIndex = newIndex

        // Устанавливаем выделение на новую кнопку
        buttons[selectedButtonIndex].isSelected = true
        buttons[selectedButtonIndex].setBackgroundResource(android.R.drawable.btn_default) // Можно использовать любой другой стиль

        // Устанавливаем фокус
        buttons[selectedButtonIndex].requestFocus()
    }

    override fun onResume() {
        super.onResume()
        // Убедитесь, что при возвращении на экран центральная кнопка выделена
        updateSelectedButton(4) // Центральная кнопка (appButton5)
    }

    private fun openAllApps() {
        // Открыть список установленных приложений
        val intent = Intent(this, AllAppsActivity::class.java)
        startActivity(intent)
    }
}
