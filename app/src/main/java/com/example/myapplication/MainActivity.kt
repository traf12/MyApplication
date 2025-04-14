package com.example.myapplication

import android.annotation.SuppressLint
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
    private var lastResumeTime: Long = 0

    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var lockOverlay: View
    private lateinit var lockIcon: ImageView
    private lateinit var unlockHint: TextView
    private lateinit var menuLabel: TextView
    private lateinit var contactsLabel: TextView
    private var homePressedReceiver: BroadcastReceiver? = null

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private val handler = Handler(Looper.getMainLooper())
    private val retryHintHandler = Handler(Looper.getMainLooper())
    private val screenOffHandler = Handler(Looper.getMainLooper())

    private val LONG_PRESS_THRESHOLD = 1000L
    private val autoLockTimeout = 30_000L
    private val screenOffTimeout = 10_000L

    private var isKeypadLocked = true
    private var centerPressed = false
    private var isKey7Handled = false
    private var isLeavingToSubActivity = false
    private var wasUnlockedFromLockScreen = false

    private var lastPlaybackState: Int = PlaybackState.STATE_NONE

    private val autoLockHandler = Handler(Looper.getMainLooper())
    private val autoLockRunnable = Runnable {
        isKeypadLocked = true
        centerPressed = false
        showLockUI("Нажмите OK для разблокировки")
    }

    private val screenOffRunnable = Runnable {
        if (isKeypadLocked) {
            lockScreenUsingDevicePolicy()
        }
    }

    private val retryHintRunnable = Runnable {
        if (isKeypadLocked && centerPressed) {
            centerPressed = false
            showLockUI("Нажмите OK для разблокировки")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isLeavingToSubActivity = intent?.getBooleanExtra("from_sub_activity", false) == true

        if (!isNotificationServiceEnabled(this)) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        wasUnlockedFromLockScreen = intent?.getBooleanExtra("unlocked", false) == true

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
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Нужны права администратора")
            }
            startActivity(intent)
        }

        mediaController = MediaControllerManager.mediaController
        updateTrackInfo()
        updateTime()
        updateBatteryAndNetwork()

        hideSystemUI()
        showLockUI("Нажмите OK для разблокировки")
        resetAutoLockTimer()

        handler.post(object : Runnable {
            override fun run() {
                mediaController = MediaControllerManager.mediaController
                updateTrackInfo()
                handler.postDelayed(this, 5000)
            }
        })

        handler.post(object : Runnable {
            override fun run() {
                updateBatteryAndNetwork()
                handler.postDelayed(this, 10000)
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isMusicPlaying = mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING

        if (isKeypadLocked) {
            return when (keyCode) {
                KeyEvent.KEYCODE_HOME -> {
                    if (!isLeavingToSubActivity) {
                        isLeavingToSubActivity = true
                        val intent = Intent(this, LockScreenActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                    }
                    true
                }
                KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_ENDCALL -> {
                    isKeypadLocked = true
                    centerPressed = false
                    showLockUI("Нажмите OK для разблокировки")
                    lockScreenUsingDevicePolicy()
                    true
                }
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    centerPressed = true
                    showLockUI("Нажмите *")
                    retryHintHandler.removeCallbacks(retryHintRunnable)
                    retryHintHandler.postDelayed(retryHintRunnable, 5000)
                    true
                }
                KeyEvent.KEYCODE_STAR -> {
                    if (centerPressed) {
                        isKeypadLocked = false
                        centerPressed = false
                        hideLockUI()
                        retryHintHandler.removeCallbacks(retryHintRunnable)
                        resetAutoLockTimer()
                    } else {
                        showLockUI("Нажмите OK для разблокировки")
                    }
                    true
                }
                KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_SOFT_RIGHT -> {
                    if (!isLeavingToSubActivity) {
                        isLeavingToSubActivity = true
                        //startActivity(Intent(this, ContactActivity::class.java))
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
                    if (isMusicPlaying) audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE); true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isMusicPlaying) audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE); true
                }
                else -> true
            }
        }

        resetAutoLockTimer()

        return when (keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SOFT_LEFT -> {
                if (!isLeavingToSubActivity) {
                    isLeavingToSubActivity = true
                    startActivity(Intent(this, MenuActivity::class.java))
                }
                true
            }
            KeyEvent.KEYCODE_7 -> {
                if (!isKey7Handled) {
                    isKey7Handled = true // Блокируем повторную активацию
                    handler.postDelayed({
                        // Запускаем активность только если ключ всё еще не был обработан
                        if (isKey7Handled) {
                            if (!isLeavingToSubActivity) {
                                isLeavingToSubActivity = true
                                startActivity(Intent(this, AllAppsActivity::class.java))
                            }
                            isKey7Handled = false // Сбрасываем флаг, чтобы кнопка не сработала повторно
                        }
                    }, LONG_PRESS_THRESHOLD)
                    true
                } else {
                    false // Если кнопка уже была обработана, не делаем ничего
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!isLeavingToSubActivity) {
                    isLeavingToSubActivity = true
                //    startActivity(Intent(this, ContactActivity::class.java))
                }
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isMusicPlaying) mediaController?.transportControls?.skipToPrevious(); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isMusicPlaying) mediaController?.transportControls?.skipToNext(); true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isMusicPlaying) audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isMusicPlaying) audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }





    private fun updateTrackInfo() {
        val metadata = mediaController?.metadata
        val playbackState = mediaController?.playbackState

        if (playbackState?.state != lastPlaybackState) {
            lastPlaybackState = playbackState?.state ?: PlaybackState.STATE_NONE
            handler.postDelayed({ updateTrackUI(metadata, playbackState) }, 1000)
        } else {
            updateTrackUI(metadata, playbackState)
        }
    }

    private fun updateTrackUI(metadata: MediaMetadata?, playbackState: PlaybackState?) {
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
        val handler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                val now = Calendar.getInstance().time
                timeView.text = formatTime.format(now)
                dateView.text = formatDate.format(now)
                val delay = 60_000 - (System.currentTimeMillis() % 60_000)
                handler.postDelayed(this, delay)
            }
        }

        handler.post(runnable)
    }

    private fun updateBatteryAndNetwork() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val batteryPct = (level / scale.toFloat() * 100).toInt()

            val batteryIconRes = when {
                isCharging && batteryPct >= 100 -> R.drawable.battery12
                isCharging && batteryPct < 100 -> R.drawable.battery11
                batteryPct < 5 -> R.drawable.battery0
                batteryPct < 10 -> R.drawable.battery1
                batteryPct < 20 -> R.drawable.battery2
                batteryPct < 30 -> R.drawable.battery3
                batteryPct < 40 -> R.drawable.battery4
                batteryPct < 50 -> R.drawable.battery5
                batteryPct < 60 -> R.drawable.battery6
                batteryPct < 70 -> R.drawable.battery7
                batteryPct < 80 -> R.drawable.battery8
                batteryPct < 90 -> R.drawable.battery9
                batteryPct <= 100 -> R.drawable.battery10
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

    private fun resetAutoLockTimer() {
        autoLockHandler.removeCallbacks(autoLockRunnable)
        autoLockHandler.postDelayed(autoLockRunnable, autoLockTimeout)

        screenOffHandler.removeCallbacks(screenOffRunnable)
        if (isKeypadLocked) {
            screenOffHandler.postDelayed(screenOffRunnable, screenOffTimeout)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        isLeavingToSubActivity = false
        wasUnlockedFromLockScreen = false
        lastResumeTime = System.currentTimeMillis()
        resetAutoLockTimer()

        homePressedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                    val reason = intent.getStringExtra("reason")
                    if (reason == "homekey") {
                        if (!isKeypadLocked) {
                            isKeypadLocked = true
                            centerPressed = false
                            showLockUI("Нажмите OK для разблокировки")
                        } else {
                            lockScreenUsingDevicePolicy()
                        }
                    }
                }
            }
        }
        registerReceiver(homePressedReceiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
    }

    override fun onPause() {
        super.onPause()
        homePressedReceiver?.let {
            unregisterReceiver(it)
            homePressedReceiver = null
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        wasUnlockedFromLockScreen = intent?.getBooleanExtra("unlocked", false) == true
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val cn = ComponentName(context, MediaNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }

    @Suppress("DEPRECATION")
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

    private fun showLockUI(hint: String) {
        lockOverlay.visibility = View.VISIBLE
        lockIcon.visibility = View.VISIBLE
        unlockHint.visibility = View.VISIBLE
        unlockHint.text = hint
        menuLabel.alpha = 0.3f
        contactsLabel.alpha = 0.3f
    }

    private fun hideLockUI() {
        lockOverlay.visibility = View.GONE
        lockIcon.visibility = View.GONE
        unlockHint.visibility = View.GONE
        menuLabel.alpha = 1f
        contactsLabel.alpha = 1f
    }

    private fun lockScreenUsingDevicePolicy() {
        if (::devicePolicyManager.isInitialized && devicePolicyManager.isAdminActive(componentName)) {
            try {
                devicePolicyManager.lockNow()
            } catch (e: SecurityException) {
                // ignore
            }
        }
    }
}
