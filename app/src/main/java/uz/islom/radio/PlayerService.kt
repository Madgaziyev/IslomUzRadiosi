package uz.islom.radio

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.widget.RemoteViews
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.vodyasov.amr.AudiostreamMetadataManager
import com.vodyasov.amr.OnNewMetadataListener
import com.vodyasov.amr.UserAgent
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * $developer = JavokhirKadirov
 * $project = IslomUzRadiosi
 * $date = 1/29/18
 * $web_site = https://kadirov.me
 * $email = kadirov.me@gmail.com
 * $github = github.com/javokhirkadirov
 * $linkidin = linkedin.com/in/javokhirkadirov
 **/

class PlayerService : Service(), AudioManager.OnAudioFocusChangeListener, OnNewMetadataListener, Player.EventListener {

    private var playerListener: PlayerListener? = null
    private var player: SimpleExoPlayer? = null
    private var start: PendingIntent? = null
    private var stop: PendingIntent? = null
    private var notificationLayout: RemoteViews? = null
    private var binder = PlayerBinder()
    private var playing: Boolean? = false
    private var title: String = ""
    private var mediaSource: ExtractorMediaSource? = null

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        disableSSL()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (null != intent && null != intent.action) {
            if (intent.action == START) {
                preparePlayer(true)
                startUpdateMeta()
            }
            if (intent.action == STOP) {
                preparePlayer(false)
            }
            if (intent.action == CLOSE) {
                close()
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopUpdateMeta()
        if (player != null) {
            player!!.release()
            player = null
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                preparePlayer(true)
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                preparePlayer(false)
            }
        }
    }

    override fun onNewHeaders(stringUri: String?, name: MutableList<String>?, desc: MutableList<String>?, br: MutableList<String>?, genre: MutableList<String>?, info: MutableList<String>?) {
    }

    override fun onNewStreamTitle(stringUri: String?, streamTitle: String?) {
        playerListener!!.onMetaChanged(streamTitle)
        title = streamTitle!!
        updateNotification()
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {}

    override fun onSeekProcessed() {}

    override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {}

    override fun onPlayerError(error: ExoPlaybackException?) {
        preparePlayer(false)
        playerListener?.onStateChanged(-2)

    }

    override fun onLoadingChanged(isLoading: Boolean) {
        if (!isLoading) {
            preparePlayer(false)
            playerListener?.onStateChanged(-2)
        }
    }

    override fun onPositionDiscontinuity(reason: Int) {}

    override fun onRepeatModeChanged(repeatMode: Int) {}

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}

    override fun onTimelineChanged(timeline: Timeline?, manifest: Any?) {}

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        if(playbackState == ExoPlayer.STATE_BUFFERING){
            playerListener?.onStateChanged(2)
        }
        if(playbackState == ExoPlayer.STATE_READY){
            playerListener?.onStateChanged(3)
        }

        if (playWhenReady) {
            playerListener?.onStateChanged(1)
            player!!.seekToDefaultPosition()
        } else {
            playerListener?.onStateChanged(0)
        }
        updateNotification()
    }


    fun setOnStateChangedListener(onStateChangedListener: PlayerListener?) {
        this.playerListener = onStateChangedListener
        if(isPlaying()){
            playerListener?.onStateChanged(1)
            playerListener?.onMetaChanged(title)
        }
    }

    fun isPlaying(): Boolean = player?.playWhenReady == true

    private fun preparePlayer(playWhenReady: Boolean) {
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)
        if (player == null) {
            val bandwidthMeter = DefaultBandwidthMeter()
            val trackSelector = DefaultTrackSelector(AdaptiveTrackSelection.Factory(bandwidthMeter))
            val dataSourceFactory = DefaultHttpDataSourceFactory(Util.getUserAgent(this, "PlayerService"), bandwidthMeter, 10000, 10000, true)
            mediaSource = ExtractorMediaSource(Uri.parse(ADDRESS), dataSourceFactory, DefaultExtractorsFactory(), null, null)

            player = ExoPlayerFactory.newSimpleInstance(this, trackSelector)
            player!!.addListener(this)
            player!!.prepare(mediaSource)
        }
        player?.playWhenReady = playWhenReady
    }

    private fun updateNotification() {
        if (notificationLayout == null) {
            val openIntent = Intent(this, MainActivity::class.java)
            val open = PendingIntent.getActivity(this, 0, openIntent, 0)

            val closeIntent = Intent(this, PlayerService::class.java)
            closeIntent.action = CLOSE
            val close = PendingIntent.getService(this, 0, closeIntent, 0)

            val startIntent = Intent(this, PlayerService::class.java)
            startIntent.action = START
            start = PendingIntent.getService(this, 0, startIntent, 0)

            val stopIntent = Intent(this, PlayerService::class.java)
            stopIntent.action = STOP
            stop = PendingIntent.getService(this, 0, stopIntent, 0)

            notificationLayout = RemoteViews(packageName, R.layout.notification_main)
            notificationLayout!!.setOnClickPendingIntent(R.id.notification_root, open)
            notificationLayout!!.setOnClickPendingIntent(R.id.notification_close, close)
        }
        notificationLayout!!.setTextViewText(R.id.notification_title, title)

        if (isPlaying()) {
            notificationLayout!!.setImageViewResource(R.id.notification_start, R.drawable.ic_stop)
            notificationLayout!!.setOnClickPendingIntent(R.id.notification_start, stop)
        } else {
            notificationLayout!!.setImageViewResource(R.id.notification_start, R.drawable.ic_start)
            notificationLayout!!.setOnClickPendingIntent(R.id.notification_start, start)
        }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContent(notificationLayout)
                .setOngoing(playing!!)
                .build()

        startForeground(101, notification)
    }

    private fun close() {
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_LOSS)
        playing = false
        if (null != player) {
            player!!.release()
        }
        stopForeground(true)
        if (null != playerListener) {
            playerListener!!.onStateChanged(-1)
        }
    }

    private fun disableSSL() {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate>? {
                return null
            }
        })
        try {
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        } catch (e: KeyManagementException) {
            e.printStackTrace()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

    }

    private fun startUpdateMeta() {
        AudiostreamMetadataManager.getInstance()
                .setUri(ADDRESS)
                .setOnNewMetadataListener(this)
                .setUserAgent(UserAgent.WINDOWS_MEDIA_PLAYER)
                .start()
    }

    private fun stopUpdateMeta() {
        AudiostreamMetadataManager.getInstance().stop()
    }

    inner class PlayerBinder : Binder() {
        val service: PlayerService
            get() = this@PlayerService
    }

    interface PlayerListener {
        fun onStateChanged(state: Int)
        fun onMetaChanged(title: String?)
    }
}
