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

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName
    private var screenOffReceiver: BroadcastReceiver? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isAppGoingToBackground = true

    private var isKey7Handled = false
    private val LONG_PRESS_THRESHOLD = 1000L
    private val REQUEST_CODE_DEVICE_ADMIN = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isNotificationServiceEnabled(this)) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            Toast.makeText(this, "Пожалуйста, включите доступ к уведомлениям", Toast.LENGTH_LONG).show()
        }

        setContentView(R.layout.activity_main)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        trackTitle = findViewById(R.id.trackTitle)
        trackArtist = findViewById(R.id.trackArtist)

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

        hideSystemUI()

        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    val lockIntent = Intent(this@MainActivity, LockScreenActivity::class.java)
                    lockIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(lockIntent)
                }
            }
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        timeView = findViewById(R.id.timeView)
        dateView = findViewById(R.id.dateView)
        batteryIcon = findViewById(R.id.batteryIcon)
        networkIcon = findViewById(R.id.networkIcon)

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
        updateBatteryAndNetwork()

        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread { updateBatteryAndNetwork() }
            }
        }, 0, 10000)
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
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        isAppGoingToBackground = true

        handler.postDelayed({
            if (isAppGoingToBackground && ::devicePolicyManager.isInitialized && ::componentName.isInitialized) {
                lockScreenUsingDevicePolicy()
            }
        }, 300)
    }

    override fun onPause() {
        super.onPause()
        isAppGoingToBackground = false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isMusicPlaying = mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING

        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                lockScreenUsingDevicePolicy(); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isMusicPlaying) mediaController?.transportControls?.skipToPrevious()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isMusicPlaying) mediaController?.transportControls?.skipToNext()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isMusicPlaying) audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isMusicPlaying) audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                true
            }
            KeyEvent.KEYCODE_7 -> {
                if (!isKey7Handled) {
                    isKey7Handled = true
                    handler.postDelayed({
                        Toast.makeText(this, "Долгое нажатие на 7", Toast.LENGTH_SHORT).show()
                        vibrateShort()
                        try {
                            startActivity(Intent(this, AllAppsActivity::class.java))
                        } catch (e: Exception) {
                            Toast.makeText(this, "Ошибка запуска AllAppsActivity", Toast.LENGTH_LONG).show()
                        }
                    }, LONG_PRESS_THRESHOLD)
                }
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                startActivity(Intent(this, MenuActivity::class.java))
                overridePendingTransition(0, 0)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_7) {
            if (!isKey7Handled) {
                Toast.makeText(this, "Кнопка 7 не удержана достаточно долго", Toast.LENGTH_SHORT).show()
            }
            handler.removeCallbacksAndMessages(null)
            isKey7Handled = false
            return true
        }
        return super.onKeyUp(keyCode, event)
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

    private fun updateBatteryAndNetwork() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val isCharging = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
            val batteryPct = (level / scale.toFloat() * 100).toInt()

            val batteryIconRes = when {
                isCharging -> R.drawable.battery12
                batteryPct >= 95 -> R.drawable.battery10
                batteryPct >= 90 -> R.drawable.battery9
                batteryPct >= 80 -> R.drawable.battery8
                batteryPct >= 70 -> R.drawable.battery7
                batteryPct >= 60 -> R.drawable.battery6
                batteryPct >= 50 -> R.drawable.battery5
                batteryPct >= 40 -> R.drawable.battery4
                batteryPct >= 30 -> R.drawable.battery3
                batteryPct >= 20 -> R.drawable.battery2
                batteryPct >= 10 -> R.drawable.battery1
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
            5 -> R.drawable.signal6
            else -> R.drawable.signal1
        }
        networkIcon.setImageResource(networkIconRes)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            // безопасно игнорируем
        }
    }
}
