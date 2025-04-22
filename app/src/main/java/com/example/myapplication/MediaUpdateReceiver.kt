import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.view.View
import com.example.myapplication.MediaControllerManager
import android.media.session.MediaController
import android.media.session.PlaybackState




class MediaUpdateReceiver(private val trackTitle: TextView, private val trackArtist: TextView) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.myapplication.MEDIA_CONTROLLER_UPDATED") {
            val controller = MediaControllerManager.mediaController
            // Обновляем информацию о треке
            controller?.let {
                updateTrackInfo(it)
            }
        }
    }

    private fun updateTrackInfo(controller: MediaController) {
        val metadata = controller.metadata
        val playbackState = controller.playbackState

        if (metadata != null && playbackState?.state == PlaybackState.STATE_PLAYING) {
            // Обновляем информацию о треке
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "No Title"
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "No Artist"

            // Обновляем UI в главном потоке
            Handler(Looper.getMainLooper()).post {
                trackTitle.text = title
                trackArtist.text = artist
                trackTitle.visibility = View.VISIBLE
                trackArtist.visibility = View.VISIBLE
            }
        } else {
            // Скрываем UI, если трек не воспроизводится
            Handler(Looper.getMainLooper()).post {
                trackTitle.visibility = View.GONE
                trackArtist.visibility = View.GONE
            }
        }
    }
}
