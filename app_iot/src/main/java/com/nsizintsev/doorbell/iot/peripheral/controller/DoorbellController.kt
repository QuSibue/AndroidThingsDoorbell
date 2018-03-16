package com.nsizintsev.doorbell.iot.peripheral.controller

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.OnLifecycleEvent
import android.os.Handler
import android.os.HandlerThread
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.GpioCallback
import com.nsizintsev.doorbell.common.entity.ImageData
import com.nsizintsev.doorbell.iot.base.IActivityProvider
import com.nsizintsev.doorbell.iot.base.IHandlerProvider
import com.nsizintsev.doorbell.iot.base.IPermissionCallback
import com.nsizintsev.doorbell.iot.base.IPermissionManager
import com.nsizintsev.doorbell.iot.peripheral.BtnManager
import com.nsizintsev.doorbell.iot.peripheral.CameraManager
import com.nsizintsev.doorbell.iot.peripheral.LedManager
import com.nsizintsev.doorbell.iot.peripheral.FxManager

/**
 * Created by nsizintsev on 3/12/2018.
 */

class DoorbellController(lifecycle: Lifecycle,
                         private val activityProvider: IActivityProvider,
                         private val permissionManager: IPermissionManager,
                         private val uiProvider: CameraManager.UiProvider)

    : IActivityProvider by activityProvider,
        IPermissionManager by permissionManager,
        CameraManager.UiProvider by uiProvider,
        IHandlerProvider,
        LifecycleObserver,
        IPermissionCallback {

    val photoLiveData = MutableLiveData<ImageData>()

    private var callbackThread: HandlerThread? = null

    private var callbackHandler: Handler? = null

    private val btnCallback = object : GpioCallback {

        override fun onGpioEdge(p0: Gpio): Boolean {
            ledHandler.setValue(p0.value)
            if (p0.value) {
                cameraHandler.takePicture()
                fxManager.playSound(true)
            } else {
                fxManager.stopPlay(true)
            }
            return true
        }

        override fun onGpioError(gpio: Gpio, error: Int) {
            throw RuntimeException("Btn error $error")
        }

    }

    private val cameraListener = object : CameraManager.CameraListener {

        override fun onPhotoMade(image: ImageData) {
            photoLiveData.postValue(image)
        }

    }

    private val btnHandler = BtnManager("GPIO2_IO03", btnCallback, this)

    private val ledHandler = LedManager("GPIO2_IO05")

    private val cameraHandler = CameraManager(lifecycle, this, this, this, cameraListener)

    private val fxManager: FxManager = FxManager(this, "doorbell-7.mp3")

    init {
        lifecycle.addObserver(this)
        lifecycle.addObserver(ledHandler)
        lifecycle.addObserver(fxManager)
        lifecycle.addObserver(cameraHandler)
        lifecycle.addObserver(btnHandler)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        callbackThread = HandlerThread("DoorbellModelThread_${hashCode()}")
        callbackThread!!.start()
        callbackHandler = Handler(callbackThread!!.looper)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        callbackThread?.quit()
        callbackThread = null
        callbackHandler = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        return cameraHandler.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun getCallbackHandler(): Handler = callbackHandler!!

}
