package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.example.myapplication.R

class LockScreenActivity : Activity() {

    private var centerPressed = false
    private lateinit var statusText: TextView
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private var isLockedScreenActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        isLockedScreenActive = true
        setContentView(R.layout.activity_lock_screen)
        statusText = findViewById(R.id.statusText)

        // Препятствие выключению экрана
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Инициализация PowerManager для управления экраном
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::LockScreenWakeLock")
        wakeLock.acquire()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                centerPressed = true
                statusText.text = "Теперь нажмите *"
                true
            }

            KeyEvent.KEYCODE_STAR -> {
                if (centerPressed) {
                    unlockScreen()
                } else {
                    Toast.makeText(this, "Сначала нажмите центральную кнопку", Toast.LENGTH_SHORT).show()
                }
                true
            }

            KeyEvent.KEYCODE_HOME -> {
                // Выключаем экран по кнопке Home
                lockDevice()
                true
            }

            else -> true // блокируем другие кнопки
        }
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isLockedScreenActive) {
            lockDevice()
        }
    }


    private fun unlockScreen() {
        isLockedScreenActive = false // <- помечаем, что разблокировано
        Toast.makeText(this, "Разблокировано", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("unlocked", true)
        startActivity(intent)

        finish()
    }


    private fun lockDevice() {
        val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val compName = android.content.ComponentName(this, MyDeviceAdminReceiver::class.java)
        if (devicePolicyManager.isAdminActive(compName)) {
            devicePolicyManager.lockNow()
        } else {
            Toast.makeText(this, "Разреши админ-доступ", Toast.LENGTH_SHORT).show()
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Для блокировки экрана")
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        // Блокируем кнопку Назад
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock.isHeld) {
            wakeLock.release() // Освобождаем WakeLock при уничтожении активити
        }
    }
}
