package uz.islom.radio

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.*
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.yalantis.audio.lib.AudioUtil
import com.yalantis.waves.util.Horizon


class MainActivity : AppCompatActivity(), ServiceConnection, PlayerService.OnStateChangedListener, View.OnClickListener, AudioRecord.OnRecordPositionUpdateListener{


    private var audioRecord: AudioRecord? = null
    private var horizon: Horizon? = null
    private var surface: GLSurfaceView? = null
    private var play: ImageView? = null
    private var error: TextView? = null
    private var title:TextView? = null
    private var playerService: PlayerService? = null
    private var serviceIntent: Intent? = null
    private var buffer: ByteArray? = null
    private var share:TextView? = null
    private var site:TextView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surface = findViewById(R.id.surface)
        error = findViewById(R.id.error)
        play = findViewById(R.id.play)
        title = findViewById(R.id.title)
        site = findViewById(R.id.site)
        share = findViewById(R.id.share)

        play!!.setOnClickListener(this)
        share!!.setOnClickListener(this)
        site!!.setOnClickListener(this)

        horizon = Horizon(surface!!, resources.getColor(R.color.bg), 44100, 1, 16)
        horizon!!.setMaxVolumeDb(120)

        serviceIntent = Intent(this, PlayerService::class.java)

    }


    override fun onStart() {
        super.onStart()
        bindService(serviceIntent, this, Context.BIND_AUTO_CREATE)
        if(playerService!=null && playerService!!.isPlaying()){
            record()
        }
    }

    override fun onResume() {
        super.onResume()
        surface!!.onResume()

    }

    override fun onPause() {
        super.onPause()
        surface!!.onPause()
    }

    override fun onStop() {
        super.onStop()
        stopRecording()
        unbindService(this)
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        playerService = (service as PlayerService.PlayerBinder).service
        playerService!!.setOnStateChangedListener(this)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        playerService!!.setOnStateChangedListener(null)
    }

    override fun onStateChanged(state: Int) {
        when (state) {
            1 -> {
                error!!.visibility = View.INVISIBLE
                surface!!.visibility = View.VISIBLE
                record()
                play!!.setImageResource(R.drawable.ic_stop_big)
            }
            0 -> {
                error!!.visibility = View.INVISIBLE
                surface!!.visibility = View.VISIBLE
                stopRecording()
                play!!.setImageResource(R.drawable.ic_start_big)
            }
            -1 -> {
                error!!.visibility = View.INVISIBLE
                surface!!.visibility = View.VISIBLE
                stopService(serviceIntent)
                Handler().postDelayed({ finish() }, 50)
            }
            -2 -> {
                error!!.visibility = View.VISIBLE
                stopRecording()
                surface!!.visibility = View.INVISIBLE
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.play ->{
                if (playerService!!.isPlaying()) {
                    serviceIntent!!.action = STOP
                    startService(serviceIntent)
                } else {
                    serviceIntent!!.action = START
                    startService(serviceIntent)
                }
            }
            R.id.site -> {
                val adhanUz = Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE))
                startActivity(adhanUz)
            }
            R.id.share ->{
                val shareBody = resources.getString(R.string.share_link)
                val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                sharingIntent.type = "text/plain"
                sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.share_head))
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody)
                startActivity(Intent.createChooser(sharingIntent, resources.getString(R.string.share)))
            }

        }
    }

    override fun onMarkerReached(recorder: AudioRecord) {}

    override fun onPeriodicNotification(recorder: AudioRecord) {
        if (audioRecord!!.recordingState == AudioRecord.RECORDSTATE_RECORDING && audioRecord!!.read(buffer!!, 0, buffer!!.size) != -1) {
            horizon!!.updateView(buffer!!)
        }
    }

    private fun startRecording() {
        val bufferSize = 2 * AudioRecord.getMinBufferSize(44100, 1, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, 1, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        AudioUtil.initProcessor(44100, 1, 16)

        val recordingThread = object : Thread("recorder") {
            override fun run() {
                super.run()
                buffer = ByteArray(bufferSize)
                Looper.prepare()
                audioRecord!!.setRecordPositionUpdateListener(this@MainActivity, Handler(Looper.myLooper()))
                val bytePerSample = 16 / 8
                val samplesToDraw = (bufferSize / bytePerSample).toFloat()
                audioRecord!!.positionNotificationPeriod = samplesToDraw.toInt()
                audioRecord!!.read(buffer!!, 0, bufferSize)
                Looper.loop()
            }
        }

        if (audioRecord != null) {
            audioRecord!!.startRecording()
        }
        recordingThread.start()
    }

    private fun openSettings() {
        Toast.makeText(this, R.string.settings, Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == 101) {
            if(grantResults[0]==PackageManager.PERMISSION_GRANTED){
                startRecording()
            }else if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.RECORD_AUDIO)){
                openSettings()
            }else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),101)
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    override fun onMetaChanged(title: String?) {
        this.title!!.text = title
    }


    private fun record(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED){
            startRecording()
        }else {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.RECORD_AUDIO)){
                openSettings()
            }else {
                ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),101)
            }
        }
    }

    private fun stopRecording(){
        surface!!.onPause()
        if (audioRecord != null) {
            audioRecord!!.release()
        }
    }

}
