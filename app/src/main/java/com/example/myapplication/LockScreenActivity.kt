package com.example.myapplication

import android.app.Activity
import android.content.*
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.*
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.util.Timer
import java.util.TimerTask

class LockScreenActivity : Activity() {

    private var centerPressed = false
    private lateinit var statusText: TextView
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private var isLockedScreenActive = true

    private lateinit var batteryIcon: ImageView
    private lateinit var networkIcon: ImageView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView

    private lateinit var audioManager: AudioManager
    private lateinit var mediaSessionManager: MediaSessionManager
    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        isLockedScreenActive = true
        setContentView(R.layout.activity_lock_screen)

        statusText = findViewById(R.id.statusText)
        batteryIcon = findViewById(R.id.batteryIcon)
        networkIcon = findViewById(R.id.networkIcon)
        trackTitle = findViewById(R.id.trackTitle)
        trackArtist = findViewById(R.id.trackArtist)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::LockScreenWakeLock")
        wakeLock.acquire()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        initMediaController()
        updateBatteryAndNetwork()
        updateTrackInfo()

        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    updateBatteryAndNetwork()
                    updateTrackInfo()
                }
            }
        }, 0, 5000)
    }

    private fun initMediaController() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(null)
            if (controllers.isNotEmpty()) {
                mediaController = controllers[0]
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Нет разрешения на чтение медиа", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTrackInfo() {
        val metadata = mediaController?.metadata
        val playbackState = mediaController?.playbackState

        if (metadata != null && playbackState != null && playbackState.state == android.media.session.PlaybackState.STATE_PLAYING) {
            trackTitle.text = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
            trackArtist.text = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            trackTitle.visibility = View.VISIBLE
            trackArtist.visibility = View.VISIBLE
        } else {
            trackTitle.visibility = View.GONE
            trackArtist.visibility = View.GONE
        }
    }

    private fun updateBatteryAndNetwork() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val isCharging = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
            val batteryPct = (level / scale.toFloat() * 100).toInt()

            val batteryIconRes = when {
                isCharging -> R.drawable.battery11
                batteryPct >= 95 -> R.drawable.battery12
                batteryPct >= 90 -> R.drawable.battery10
                batteryPct >= 80 -> R.drawable.battery9
                batteryPct >= 70 -> R.drawable.battery8
                batteryPct >= 60 -> R.drawable.battery7
                batteryPct >= 50 -> R.drawable.battery6
                batteryPct >= 40 -> R.drawable.battery5
                batteryPct >= 30 -> R.drawable.battery4
                batteryPct >= 20 -> R.drawable.battery3
                batteryPct >= 10 -> R.drawable.battery2
                batteryPct > 5 -> R.drawable.battery1
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isMusicPlaying = mediaController?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING

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

            KeyEvent.KEYCODE_HOME -> {
                lockDevice()
                true
            }

            else -> true
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isLockedScreenActive) {
            lockDevice()
        }
    }

    private fun unlockScreen() {
        isLockedScreenActive = false
        Toast.makeText(this, "Разблокировано", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("unlocked", true)
        startActivity(intent)
        finish()
    }

    private fun lockDevice() {
        val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val compName = ComponentName(this, MyDeviceAdminReceiver::class.java)
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
        // блокируем кнопку Назад
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}
