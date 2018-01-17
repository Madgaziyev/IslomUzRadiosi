package uz.islom.radio

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.widget.RemoteViews
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
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

class PlayerService : Service(), AudioManager.OnAudioFocusChangeListener, OnNewMetadataListener {

    private var onStateChangedListener: OnStateChangedListener? = null
    private var audioManager: AudioManager? = null
    private var player: SimpleExoPlayer? = null
    private var start: PendingIntent? = null
    private var stop:PendingIntent? = null
    private var notificationLayout: RemoteViews? = null

    private val binder = PlayerBinder()
    private var playing: Boolean? = false
    private var cm: ConnectivityManager?=null
    private var activeNetwork: NetworkInfo? = null
    private var title:String? =null

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        disableSSL()
        initNotification()

        AudiostreamMetadataManager.getInstance()
                .setUri(ADDRESS)
                .setOnNewMetadataListener(this)
                .setUserAgent(UserAgent.WINDOWS_MEDIA_PLAYER)
                .start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (null != intent && null != intent.action) {
            if (intent.action == START) {
                start()
            }
            if (intent.action == STOP) {
                stop()
            }
            if (intent.action == CLOSE) {
                close()
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        AudiostreamMetadataManager.getInstance().stop()
        if (player != null) {
            player!!.release()
            player = null
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> start()
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stop()
        }
    }

    fun setOnStateChangedListener(onStateChangedListener: OnStateChangedListener?) {
        this.onStateChangedListener = onStateChangedListener
    }

    fun isPlaying(): Boolean = ( playing!=null && playing==true)



    private fun initNotification() {
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
        notificationLayout!!.setOnClickPendingIntent(R.id.notification_adhanFm_root, open)
        notificationLayout!!.setOnClickPendingIntent(R.id.notification_adhanFm_close, close)
    }

    private fun updateNotification() {

        notificationLayout!!.setTextViewText(R.id.notification_title,title)

        if (playing!!) {
            notificationLayout!!.setImageViewResource(R.id.notification_adhanFm_start, R.drawable.ic_stop)
            notificationLayout!!.setOnClickPendingIntent(R.id.notification_adhanFm_start, stop)
        } else {
            notificationLayout!!.setImageViewResource(R.id.notification_adhanFm_start, R.drawable.ic_start)
            notificationLayout!!.setOnClickPendingIntent(R.id.notification_adhanFm_start, start)
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
        playing = false
        if (null != player) {
            player!!.release()
        }
        stopForeground(true)
        audioManager!!.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_LOSS)
        if (null != onStateChangedListener) {
            onStateChangedListener!!.onStateChanged(-1)
        }
    }


    private fun start() {
        try {
            cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            activeNetwork = cm!!.activeNetworkInfo
            if (activeNetwork == null || !activeNetwork!!.isConnectedOrConnecting) {
                if (null != onStateChangedListener) {
                    onStateChangedListener!!.onStateChanged(-2)
                }
            } else {

                val bandwidthMeter = DefaultBandwidthMeter()
                val trackSelector = DefaultTrackSelector(AdaptiveTrackSelection.Factory(bandwidthMeter))
                val dataSourceFactory = DefaultHttpDataSourceFactory(Util.getUserAgent(this, "PlayerService"), bandwidthMeter, 10000, 10000, true)
                val mediaSource = ExtractorMediaSource(Uri.parse(ADDRESS), dataSourceFactory, DefaultExtractorsFactory(), null, null)


                player = ExoPlayerFactory.newSimpleInstance(this, trackSelector)
                player!!.playWhenReady = true

                audioManager!!.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN)
                player!!.prepare(mediaSource)
                playing = true
                updateNotification()
                if (null != onStateChangedListener) {
                    onStateChangedListener!!.onStateChanged(1)
                }
            }
        } catch (e: Exception) {
            if (null != onStateChangedListener) {
                onStateChangedListener!!.onStateChanged(-2)
            }
        }

    }

    private fun stop() {
        playing = false
        if (null != player)
            player!!.release()
        updateNotification()
        if (null != onStateChangedListener) {
            onStateChangedListener!!.onStateChanged(0)
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

    inner class PlayerBinder : Binder() {
        val service: PlayerService
            get() = this@PlayerService
    }

    interface OnStateChangedListener {
        fun onStateChanged(state: Int)
        fun onMetaChanged(title:String?)
    }

    override fun onNewHeaders(stringUri: String?, name: MutableList<String>?, desc: MutableList<String>?, br: MutableList<String>?, genre: MutableList<String>?, info: MutableList<String>?) {
    }

    override fun onNewStreamTitle(stringUri: String?, streamTitle: String?) {
        if(streamTitle!=null && streamTitle.length>17){
            title=streamTitle.substring(0,17)
            title+="..."
        }
        title = streamTitle
        onStateChangedListener!!.onMetaChanged(title)
        updateNotification()
    }


}
