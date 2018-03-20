package com.nsizintsev.doorbell.iot.peripheral

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import com.nsizintsev.doorbell.iot.base.IActivityProvider

class FxManager(private val activityProvider: IActivityProvider,
                private val path: String) : LifecycleObserver {

    private var mediaPlayer: MediaPlayer? = null

    private var fd: AssetFileDescriptor? = null

    private var prepared = false

    private var playing = false

    private var repeat: Boolean = false

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        val assetFileDescriptor = activityProvider.getActivity().assets.openFd(path)
        this.fd = assetFileDescriptor

        val mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(assetFileDescriptor)
        mediaPlayer.setOnPreparedListener {
            prepared = true
        }
        mediaPlayer.setOnCompletionListener {
            playing = false
            if (repeat) {
                playSound(repeat)
            }
        }
        mediaPlayer.prepareAsync()
        this.mediaPlayer = mediaPlayer
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        prepared = false
        playing = false
        repeat = false

        mediaPlayer?.release()
        mediaPlayer = null

        fd?.close()
        fd = null
    }

    fun playSound(repeat: Boolean) {
        if (prepared) {
            playing = true
            this.repeat = repeat

            mediaPlayer?.seekTo(0)
            mediaPlayer?.start()
        }
    }

    fun stopPlay(quitly: Boolean) {
        repeat = false
        if (!quitly) {
            mediaPlayer?.pause()
        }
    }

}