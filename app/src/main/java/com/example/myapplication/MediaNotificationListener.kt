package com.example.myapplication

import android.content.ComponentName
import android.content.Intent
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateController()
    }

    private fun updateController() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(ComponentName(this, javaClass))

            val filteredControllers = controllers.filter { it.playbackState != null && it.metadata != null }

            if (filteredControllers.isNotEmpty()) {
                val newController = filteredControllers[0]
                val currentController = MediaControllerManager.mediaController

                if (currentController == null || currentController.sessionToken != newController.sessionToken) {
                    MediaControllerManager.mediaController = newController
                    Log.d("MediaListener", "Контроллер обновлён: ${newController.packageName}")

                    sendControllerUpdatedBroadcast()
                }
            } else {
                if (MediaControllerManager.mediaController != null) {
                    MediaControllerManager.mediaController = null
                    Log.d("MediaListener", "Контроллер очищен")
                    sendControllerUpdatedBroadcast()
                }
            }
        } catch (e: SecurityException) {
            Log.e("MediaNotification", "Нет разрешения", e)
        } catch (e: Exception) {
            Log.e("MediaNotification", "Ошибка при обновлении контроллера", e)
        }
    }

    private fun sendControllerUpdatedBroadcast() {
        val intent = Intent("com.example.myapplication.MEDIA_CONTROLLER_UPDATED")
        sendBroadcast(intent)
    }
}
