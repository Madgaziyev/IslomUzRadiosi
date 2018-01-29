package uz.islom.radio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.View.INVISIBLE
import kotlinx.android.synthetic.main.activity_main.*

/**
 * $developer = JavokhirKadirov
 * $project = IslomUzRadiosi
 * $date = 1/29/18
 * $web_site = https://kadirov.me
 * $email = kadirov.me@gmail.com
 * $github = github.com/javokhirkadirov
 * $linkidin = linkedin.com/in/javokhirkadirov
 **/

class MainActivity : AppCompatActivity(),
        ServiceConnection,
        PlayerService.PlayerListener,
        View.OnClickListener{

    private var playerService: PlayerService? = null
    private var serviceIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ivPlay.setOnClickListener(this)
        tvShare.setOnClickListener(this)
        tvSite.setOnClickListener(this)
        serviceIntent = Intent(this, PlayerService::class.java)
    }

    override fun onStart() {
        super.onStart()
        bindService(serviceIntent, this, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(this)
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        playerService = (service as PlayerService.PlayerBinder).service
        playerService?.setOnStateChangedListener(this)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        playerService?.setOnStateChangedListener(null)
    }

    override fun onStateChanged(state: Int) {
        when (state) {
            1 -> {
                tvError.visibility = View.INVISIBLE
                tvTitle.visibility= View.VISIBLE
                vumeter.resume(true)
                ivPlay.setImageResource(R.drawable.ic_stop_big)
                wv.loadUrl(WEBPAGE)
            }
            0 -> {
                tvError.visibility = View.INVISIBLE
                vumeter.stop(true)
                ivPlay.setImageResource(R.drawable.ic_start_big)
            }
            -1 -> {
                stopService(serviceIntent)
                Handler().postDelayed({ finish() }, 50)
            }
            -2 -> {
                tvError.visibility = View.VISIBLE
                tvTitle.visibility= View.INVISIBLE
            }
            2 ->{
                tvError.text="Loading.."
                tvError.visibility = View.VISIBLE
            }
            3 ->{
                tvError.visibility=INVISIBLE
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.ivPlay -> {
                if (playerService!!.isPlaying()) {
                    serviceIntent!!.action = STOP
                    startService(serviceIntent)
                } else {
                    if(isNetworkAvailable()) {
                        serviceIntent!!.action = START
                        startService(serviceIntent)
                    }else{
                        tvError.visibility = View.VISIBLE
                        tvTitle.visibility = View.INVISIBLE
                    }
                }
            }
            R.id.tvSite -> {
                val adhanUz = Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE))
                startActivity(adhanUz)
            }
            R.id.tvShare -> {
                val shareBody = resources.getString(R.string.share_link)
                val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                sharingIntent.type = "text/plain"
                sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.share_head))
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody)
                startActivity(Intent.createChooser(sharingIntent, resources.getString(R.string.share)))
            }
        }
    }


    override fun onMetaChanged(title: String?) {
        this.tvTitle.text = title
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

}
