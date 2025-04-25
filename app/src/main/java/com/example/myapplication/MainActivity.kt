package com.example.myapplication

import MediaUpdateReceiver
import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.*
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private lateinit var mediaUpdateReceiver: MediaUpdateReceiver


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

    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            val now = Calendar.getInstance().time
            timeView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            dateView.text = SimpleDateFormat("dd MMMM, EEEE", Locale.getDefault()).format(now)
            val delay = 60_000 - (System.currentTimeMillis() % 60_000)
            timeHandler.postDelayed(this, delay)
        }
    }




    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateTrackInfo()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateTrackInfo()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryIcon(intent)
        }
    }

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: android.telephony.SignalStrength?) {
            super.onSignalStrengthsChanged(signalStrength)
            updateNetworkIcon(signalStrength)
        }
    }


    private val autoLockHandler = Handler(Looper.getMainLooper())
    private val autoLockRunnable = Runnable {
        if (!isKeypadLocked) {
            isKeypadLocked = true
            centerPressed = false
            showLockUI("Нажмите OK для разблокировки")
        }
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

    private val permissions = arrayOf(
        android.Manifest.permission.BIND_DEVICE_ADMIN,
        android.Manifest.permission.WRITE_SETTINGS,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.PROCESS_OUTGOING_CALLS,
        android.Manifest.permission.DISABLE_KEYGUARD,
        android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
        android.Manifest.permission.QUERY_ALL_PACKAGES,
        android.Manifest.permission.VIBRATE,
        android.Manifest.permission.WAKE_LOCK,
        android.Manifest.permission.MEDIA_CONTENT_CONTROL,
        android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.WRITE_CONTACTS,
        android.Manifest.permission.WRITE_CALL_LOG
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверка разрешений
        checkPermissions()

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
        mediaController?.registerCallback(mediaCallback)
        updateTrackInfo()
        updateTime()

        hideSystemUI()
        showLockUI("Нажмите OK для разблокировки")
        resetAutoLockTimer()


    }

    private fun requestMediaControllerUpdate() {
        val intent = Intent("com.example.myapplication.REQUEST_MEDIA_UPDATE")
        sendBroadcast(intent)
    }

    // Новый метод для проверки разрешений
    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 123)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123) {
            // Логика на случай, если разрешения не предоставлены
            if (grantResults.any { it == PackageManager.PERMISSION_DENIED }) {
                // Если какие-то разрешения не были предоставлены, можно показать уведомление или что-то сделать
                // например, попросить пользователя предоставить разрешения
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetAutoLockTimer()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        isLeavingToSubActivity = false
        wasUnlockedFromLockScreen = false
        lastResumeTime = System.currentTimeMillis()
        requestMediaControllerUpdate()
        resetAutoLockTimer()
        updateTrackInfo()
        updateTime()

        mediaController = MediaControllerManager.mediaController
        mediaController?.registerCallback(mediaCallback)

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)

        // Регистрируем MediaUpdateReceiver
        mediaUpdateReceiver = MediaUpdateReceiver(trackTitle, trackArtist)
        val filter = IntentFilter("com.example.myapplication.MEDIA_CONTROLLER_UPDATED")
        registerReceiver(mediaUpdateReceiver, filter)

        // Регистрируем Receiver для домашней кнопки (система)
        homePressedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                    val reason = intent.getStringExtra("reason")
                    if (reason == "homekey") {
                        // Отключаем экран при нажатии HOME
                        lockScreenUsingDevicePolicy()
                        // Блокируем клавиатуру только после отключения экрана
                        if (!isKeypadLocked) {
                            isKeypadLocked = true
                            centerPressed = false
                            showLockUI("Нажмите OK для разблокировки")
                        }
                    }
                }
            }
        }
        registerReceiver(homePressedReceiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        })
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        wasUnlockedFromLockScreen = intent?.getBooleanExtra("unlocked", false) == true
    }

    override fun onPause() {
        super.onPause()
        screenOffHandler.removeCallbacks(screenOffRunnable)

        timeHandler.removeCallbacks(timeRunnable)
        unregisterReceiver(mediaUpdateReceiver)  // Отписка от MediaUpdateReceiver

        mediaController?.unregisterCallback(mediaCallback)

        unregisterReceiver(batteryReceiver)
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)

        homePressedReceiver?.let {
            unregisterReceiver(it)
            homePressedReceiver = null
        }
    }


    private fun resetAutoLockTimer() {
        autoLockHandler.removeCallbacks(autoLockRunnable)
        autoLockHandler.postDelayed(autoLockRunnable, autoLockTimeout)

        screenOffHandler.removeCallbacks(screenOffRunnable)

        if (isKeypadLocked) {
            screenOffHandler.postDelayed(screenOffRunnable, screenOffTimeout)
        }
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isMusicPlaying = mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING

        if (!isKeypadLocked && (keyCode == KeyEvent.KEYCODE_HOME ||
                    keyCode == KeyEvent.KEYCODE_POWER ||
                    keyCode == KeyEvent.KEYCODE_ENDCALL)) {
            // При нажатии HOME, блокируем экран
            isKeypadLocked = true
            centerPressed = false
            showLockUI("Нажмите OK для разблокировки")
            lockScreenUsingDevicePolicy()
            return true
        }

        if (isKeypadLocked) {
            return when (keyCode) {
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
                    if (isKeypadLocked) {
                        lockScreenUsingDevicePolicy()
                    } else {
                        isKeypadLocked = true
                        centerPressed = false
                        showLockUI("Нажмите OK для разблокировки")
                    }
                    return true
                }
                else -> true
            }
        }

        resetAutoLockTimer()

        return when (keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SOFT_LEFT -> {
                isLeavingToSubActivity = true
                startActivity(Intent(this, MenuActivity::class.java))
                true
            }
            KeyEvent.KEYCODE_7 -> {
                if (!isKey7Handled) {
                    isKey7Handled = true
                    handler.postDelayed({
                        isLeavingToSubActivity = true
                        startActivity(Intent(this, AllAppsActivity::class.java))
                        isKey7Handled = false
                    }, LONG_PRESS_THRESHOLD)
                }
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                isLeavingToSubActivity = true
                startActivity(Intent(this, ContactActivity::class.java))
                true
            }
            KeyEvent.KEYCODE_CALL -> {
                isLeavingToSubActivity = true
                startActivity(Intent(this, CallLogActivity::class.java))
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_7) {
            handler.removeCallbacksAndMessages(null)
            isKey7Handled = false
            return true
        }
        return super.onKeyUp(keyCode, event)
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

    private fun updateTrackInfo() {
        val metadata = mediaController?.metadata
        val playbackState = mediaController?.playbackState

        if (metadata != null && playbackState?.state == PlaybackState.STATE_PLAYING) {
            trackTitle.text = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "No Title"
            trackArtist.text = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "No Artist"
            trackTitle.visibility = View.VISIBLE
            trackArtist.visibility = View.VISIBLE
        } else {
            trackTitle.visibility = View.GONE
            trackArtist.visibility = View.GONE
        }
    }


    private fun updateTime() {
        timeHandler.post(timeRunnable)
    }


    private fun updateBatteryIcon(intent: Intent?) {
        intent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val batteryPct = (level / scale.toFloat() * 100).toInt()

            val batteryIconRes = when {
                isCharging && batteryPct >= 100 -> R.drawable.battery12
                isCharging && batteryPct < 100  -> R.drawable.battery11
                batteryPct < 5   -> R.drawable.battery0
                batteryPct < 10  -> R.drawable.battery1
                batteryPct < 20  -> R.drawable.battery2
                batteryPct < 30  -> R.drawable.battery3
                batteryPct < 40  -> R.drawable.battery4
                batteryPct < 50  -> R.drawable.battery5
                batteryPct < 60  -> R.drawable.battery6
                batteryPct < 70  -> R.drawable.battery7
                batteryPct < 80  -> R.drawable.battery8
                batteryPct < 90  -> R.drawable.battery9
                batteryPct <= 100 -> R.drawable.battery10
                else -> R.drawable.battery0
            }

            batteryIcon.setImageResource(batteryIconRes)
        }
    }

    private fun updateNetworkIcon(signal: android.telephony.SignalStrength?) {
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
                // ignore
            }
        }
    }
}