package com.nsizintsev.doorbell.iot.peripheral

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.os.Handler
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.GpioCallback
import com.google.android.things.pio.PeripheralManager
import com.nsizintsev.doorbell.iot.base.IHandlerProvider
import timber.log.Timber
import java.io.IOException

/**
 * Created by nsizintsev on 3/9/2018.
 */

class BtnManager(private val pin: String,
                 private val callback: GpioCallback,
                 private val handlerProvider: IHandlerProvider) : LifecycleObserver {

    private var registered = false

    private var gpio: Gpio? = null

    @Throws(IOException::class)
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (registered) {
            return
        }

        val manager = PeripheralManager.getInstance()

        val btn = manager.openGpio(pin)
        btn.setDirection(Gpio.DIRECTION_IN)
        btn.setActiveType(Gpio.ACTIVE_HIGH)
        btn.setEdgeTriggerType(Gpio.EDGE_BOTH)

        gpio = btn

        registered = true

        registerGpioCallback(callback, handlerProvider.getCallbackHandler())
    }

    @Throws(IOException::class)
    fun registerGpioCallback(var1: GpioCallback, handler: Handler): Boolean? {
        val sGpio = gpio
        if (sGpio != null) {
            sGpio.registerGpioCallback(var1, handler)
            Timber.d("Gpio is not null, callback registered")
            return true
        } else {
            Timber.d("Gpio is null, callback not registered")
            return false
        }
    }

    fun unregisterGpioCallback(var1: GpioCallback) {
        val sGpio = gpio
        if (sGpio != null) {
            sGpio.unregisterGpioCallback(var1)
            Timber.d("Gpio is not null, callback removed")
        } else {
            Timber.d("Gpio is null, callback not removed")
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        if (!registered) {
            return
        }

        registered = false

        gpio?.close()
        gpio = null
    }

}
