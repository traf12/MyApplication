package com.example.myapplication

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.*
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.*
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var batteryIcon: ImageView
    private lateinit var networkIcon: ImageView
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSessionManager: MediaSessionManager
    private var mediaController: MediaController? = null

    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var lockOverlay: View
    private lateinit var lockIcon: ImageView
    private lateinit var unlockHint: TextView
    private lateinit var menuLabel: TextView
    private lateinit var contactsLabel: TextView

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private val handler = Handler(Looper.getMainLooper())
    private var isKey7Handled = false
    private val LONG_PRESS_THRESHOLD = 1000L
    private val REQUEST_CODE_DEVICE_ADMIN = 1

    private var isKeypadLocked = true
    private var centerPressed = false
    private var isLeavingToSubActivity = false

    private val autoLockTimeout = 30_000L
    private val autoLockHandler = Handler(Looper.getMainLooper())
    private val autoLockRunnable = Runnable {
        if (!isFinishing) {
            isKeypadLocked = true
            centerPressed = false
            showLockUI("Клавиши автоматически заблокированы")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isNotificationServiceEnabled(this)) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            Toast.makeText(this, "Пожалуйста, включите доступ к уведомлениям", Toast.LENGTH_LONG).show()
        }

        setContentView(R.layout.activity_main)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        timeView = findViewById(R.id.timeView)
        dateView = findViewById(R.id.dateView)
        batteryIcon = findViewById(R.id.batteryIcon)
        networkIcon = findViewById(R.id.networkIcon)
        trackTitle = findViewById(R.id.trackTitle)
        trackArtist = findViewById(R.id.trackArtist)
        lockOverlay = findViewById(R.id.lockOverlay)
        lockIcon = findViewById(R.id.lockIcon)
        unlockHint = findViewById(R.id.unlockHint)
        menuLabel = findViewById(R.id.menuLabel)
        contactsLabel = findViewById(R.id.contactsLabel)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        if (!devicePolicyManager.isAdminActive(componentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Приложение требует права администратора для блокировки экрана.")
            }
            startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN)
        }

        mediaController = MediaControllerManager.mediaController
        updateTrackInfo()

        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    mediaController = MediaControllerManager.mediaController
                    updateTrackInfo()
                }
            }
        }, 0, 5000)

        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread { updateBatteryAndNetwork() }
            }
        }, 0, 10000)

        updateTime()
        updateBatteryAndNetwork()
        hideSystemUI()
        showLockUI("Нажмите OK для разблокировки")
        resetAutoLockTimer()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetAutoLockTimer()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isLeavingToSubActivity && isKeypadLocked) {
            lockScreenUsingDevicePolicy()
        }
    }

    override fun onResume() {
        super.onResume()
        isLeavingToSubActivity = false
        resetAutoLockTimer()
    }

    private fun resetAutoLockTimer() {
        autoLockHandler.removeCallbacks(autoLockRunnable)
        autoLockHandler.postDelayed(autoLockRunnable, autoLockTimeout)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isMusicPlaying = mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING

        when (keyCode) {
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_ENDCALL -> {
                if (isKeypadLocked) {
                    lockScreenUsingDevicePolicy()
                } else {
                    isKeypadLocked = true
                    centerPressed = false
                    showLockUI("Клавиши заблокированы")
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (isKeypadLocked) {
                    lockScreenUsingDevicePolicy()
                } else {
                    isKeypadLocked = true
                    centerPressed = false
                    showLockUI("Клавиши заблокированы")
                }
                return true
            }
        }

        if (isKeypadLocked) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    centerPressed = true
                    showLockUI("Нажмите *")
                    true
                }
                KeyEvent.KEYCODE_STAR -> {
                    if (centerPressed) {
                        isKeypadLocked = false
                        centerPressed = false
                        hideLockUI()
                        Toast.makeText(this, "Клавиши разблокированы", Toast.LENGTH_SHORT).show()
                        resetAutoLockTimer()
                    } else {
                        showLockUI("Сначала нажмите OK")
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isMusicPlaying) mediaController?.transportControls?.skipToPrevious(); true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isMusicPlaying) mediaController?.transportControls?.skipToNext(); true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (isMusicPlaying) audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isMusicPlaying) audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); true
                }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_7 -> true
                else -> true // все остальные блокируем
            }
        }

        resetAutoLockTimer()

        return when (keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SOFT_LEFT -> {
                isLeavingToSubActivity = true
                startActivity(Intent(this, MenuActivity::class.java)); true
            }

            KeyEvent.KEYCODE_7 -> {
                if (!isKey7Handled) {
                    isKey7Handled = true
                    handler.postDelayed({
                        isLeavingToSubActivity = true
                        try {
                            startActivity(Intent(this, AllAppsActivity::class.java))
                        } catch (e: Exception) {
                            Toast.makeText(this, "Ошибка запуска AllAppsActivity", Toast.LENGTH_LONG).show()
                        }
                    }, LONG_PRESS_THRESHOLD)
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onBackPressed() {
        if (isKeypadLocked) {
            lockScreenUsingDevicePolicy()
        } else {
            isKeypadLocked = true
            centerPressed = false
            showLockUI("Клавиши заблокированы")
        }
    }

    private fun showLockUI(hint: String) {
        lockOverlay.visibility = View.VISIBLE
        lockIcon.visibility = View.VISIBLE
        unlockHint.visibility = View.VISIBLE
        unlockHint.text = hint

        lockIcon.alpha = 1f
        unlockHint.alpha = 1f
        unlockHint.setTextColor(resources.getColor(android.R.color.white))
        trackTitle.alpha = 1f
        trackArtist.alpha = 1f
        trackTitle.setTextColor(resources.getColor(android.R.color.white))
        trackArtist.setTextColor(resources.getColor(android.R.color.white))
        menuLabel.alpha = 0.3f
        contactsLabel.alpha = 0.3f
    }

    private fun hideLockUI() {
        lockOverlay.visibility = View.GONE
        lockIcon.visibility = View.GONE
        unlockHint.visibility = View.GONE
        menuLabel.alpha = 1f
        contactsLabel.alpha = 1f
        trackTitle.alpha = 1f
        trackArtist.alpha = 1f
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val cn = ComponentName(context, MediaNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
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
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun updateTrackInfo() {
        val metadata = mediaController?.metadata
        val playbackState = mediaController?.playbackState

        if (metadata != null && playbackState?.state == PlaybackState.STATE_PLAYING) {
            trackTitle.text = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            trackArtist.text = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            trackTitle.visibility = View.VISIBLE
            trackArtist.visibility = View.VISIBLE
        } else {
            trackTitle.visibility = View.GONE
            trackArtist.visibility = View.GONE
        }
    }

    private fun updateTime() {
        val formatTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val formatDate = SimpleDateFormat("dd MMMM, EEEE", Locale.getDefault())
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    val now = Date()
                    timeView.text = formatTime.format(now)
                    dateView.text = formatDate.format(now)
                }
            }
        }, 0, 60000)
    }

    private fun updateBatteryAndNetwork() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val isCharging = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
            val batteryPct = (level / scale.toFloat() * 100).toInt()

            val batteryIconRes = when {
                isCharging && batteryPct == 100 -> R.drawable.battery11
                isCharging && batteryPct < 100 -> R.drawable.battery12
                batteryPct in 90..100 -> R.drawable.battery10
                batteryPct in 80..89 -> R.drawable.battery9
                batteryPct in 70..79 -> R.drawable.battery8
                batteryPct in 60..69 -> R.drawable.battery7
                batteryPct in 50..59 -> R.drawable.battery6
                batteryPct in 40..49 -> R.drawable.battery4
                batteryPct in 30..39 -> R.drawable.battery3
                batteryPct in 20..29 -> R.drawable.battery2
                batteryPct in 10..19 -> R.drawable.battery1
                else -> R.drawable.battery0
            }
            batteryIcon.setImageResource(batteryIconRes)
        }

        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val signal = telephonyManager.signalStrength
        val level = signal?.level ?: 0

        val networkIconRes = when (level) {
            0 -> R.drawable.signal1
            1 -> R.drawable.signal2
            2 -> R.drawable.signal3
            3 -> R.drawable.signal4
            4 -> R.drawable.signal5
            else -> R.drawable.signal1
        }
        networkIcon.setImageResource(networkIconRes)
    }

    private fun lockScreenUsingDevicePolicy() {
        if (::devicePolicyManager.isInitialized && devicePolicyManager.isAdminActive(componentName)) {
            try {
                devicePolicyManager.lockNow()
            } catch (e: SecurityException) {
                Toast.makeText(this, "Ошибка при попытке заблокировать экран", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun vibrateShort() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }
}
