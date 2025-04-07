package com.example.myapplication

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
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

    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private val REQUEST_CODE_DEVICE_ADMIN = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация кнопок и представлений
        val lockButton: Button = findViewById(R.id.lockButton)
        timeView = findViewById(R.id.timeView)
        dateView = findViewById(R.id.dateView)
        batteryView = findViewById(R.id.batteryView)
        val endCallButton: Button = findViewById(R.id.endCallButton)

        // Инициализация PowerManager для блокировки экрана
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "MyApp::ScreenLock")

        // Инициализация DevicePolicyManager для блокировки экрана через администратора устройства
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Проверяем, активирован ли администратор устройства
        if (!devicePolicyManager.isAdminActive(componentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Приложение требует права администратора для блокировки экрана.")
            startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN)
        }

        // Обработчик кнопки для блокировки экрана
        lockButton.setOnClickListener {
            lockScreenUsingDevicePolicy()
        }

        // Обработчик кнопки завершения вызова
        endCallButton.setOnClickListener {
            lockScreenUsingDevicePolicy()
        }

        // Обновление времени
        updateTime()
        updateBatteryLevel()
    }

    // Скрытие системных панелей после получения фокуса
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowInsetsController = window.insetsController
                windowInsetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                windowInsetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            }
        }
    }

    private fun lockScreenUsingDevicePolicy() {
        if (devicePolicyManager.isAdminActive(componentName)) {
            try {
                devicePolicyManager.lockNow()
            } catch (e: SecurityException) {
                Toast.makeText(this, "Ошибка при попытке заблокировать экран", Toast.LENGTH_SHORT).show()
            }
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Для блокировки экрана требуется доступ администратора.")
            startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN)
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
                // Теперь можно использовать функцию блокировки экрана
            } else {
                Toast.makeText(this, "Не удалось получить права администратора", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            lockScreenUsingDevicePolicy()  // Блокировка экрана через DevicePolicyManager
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            lockScreenUsingDevicePolicy()  // Блокировка экрана через DevicePolicyManager
            return true
        }
        return super.onKeyDown(keyCode, event)
    }




}
