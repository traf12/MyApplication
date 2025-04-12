package com.example.myapplication

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaNotificationListener : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager

    override fun onListenerConnected() {
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        updateController()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateController()
    }

    private fun updateController() {
        try {
            val controllers: List<MediaController> =
                mediaSessionManager.getActiveSessions(ComponentName(this, javaClass))
            if (controllers.isNotEmpty()) {
                MediaControllerManager.mediaController = controllers[0]
                Log.d("MediaListener", "Контроллер обновлён: ${controllers[0].packageName}")
            }
        } catch (e: SecurityException) {
            Log.e("MediaNotification", "Нет разрешения", e)
        }
    }
}