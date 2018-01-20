package uz.islom.radio

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.opengl.GLSurfaceView
import android.util.Log

/**
 * Created by axrorxoja on 1/21/18.
 */
class SurfaceListener(private val view: GLSurfaceView) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() = view.onResume().apply { Log.d("mylog","onResume") }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() = view.onPause().apply { Log.d("mylog","onPause") }
}