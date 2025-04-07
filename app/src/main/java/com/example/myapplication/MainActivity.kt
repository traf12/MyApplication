package com.example.myapplication

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var batteryView: TextView

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private val REQUEST_CODE_DEVICE_ADMIN = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()

        timeView = findViewById(R.id.timeView)
        dateView = findViewById(R.id.dateView)
        batteryView = findViewById(R.id.batteryView)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        if (!devicePolicyManager.isAdminActive(componentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Приложение требует права администратора для блокировки экрана.")
            }
            startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN)
        }

        updateTime()
        updateBatteryLevel()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun lockScreenUsingDevicePolicy() {
        if (devicePolicyManager.isAdminActive(componentName)) {
            try {
                devicePolicyManager.lockNow()
            } catch (e: SecurityException) {
                Toast.makeText(this, "Ошибка при попытке заблокировать экран", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTime() {
        val timer = Timer()
        val formatTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val formatDate = SimpleDateFormat("dd MMMM, EEEE", Locale.getDefault())
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    val now = Date()
                    timeView.text = formatTime.format(now)
                    dateView.text = formatDate.format(now)
                }
            }
        }, 0, 60000)
    }

    private fun updateBatteryLevel() {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let {
            val level: Int = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct: Float = level / scale.toFloat() * 100
            batteryView.text = "${batteryPct.toInt()}%"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_DEVICE_ADMIN) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Права администратора получены!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Не удалось получить права администратора", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                lockScreenUsingDevicePolicy()
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                // Центр D-Pad — здесь можно добавить действие выбора
                Toast.makeText(this, "Выбрано", Toast.LENGTH_SHORT).show()
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Навигация — пока просто выводим направление
                val direction = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> "Вверх"
                    KeyEvent.KEYCODE_DPAD_DOWN -> "Вниз"
                    KeyEvent.KEYCODE_DPAD_LEFT -> "Влево"
                    KeyEvent.KEYCODE_DPAD_RIGHT -> "Вправо"
                    else -> ""
                }
                Toast.makeText(this, direction, Toast.LENGTH_SHORT).show()
                return true
            }

            KeyEvent.KEYCODE_MENU -> {
                // Меню — переход в меню
                val intent = Intent(this, MenuActivity::class.java)
                startActivity(intent)
                return true
            }

            KeyEvent.KEYCODE_SOFT_RIGHT -> {
                // Правая клавиша — например, открыть контакты
                Toast.makeText(this, "Открыть контакты", Toast.LENGTH_SHORT).show()
                return true
            }

            else -> return super.onKeyDown(keyCode, event)
        }
    }
}
