package com.nsizintsev.doorbell.iot.peripheral

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import timber.log.Timber
import java.io.IOException

/**
 * Created by nsizintsev on 3/9/2018.
 */

class LedManager(val pin: String) : LifecycleObserver {

    private var registered = false

    private var gpio: Gpio? = null

    @Throws(IOException::class)
    fun setValue(value: Boolean) {
        val sGpio = gpio
        if (sGpio != null) {
            sGpio.value = value
            Timber.d("Gpio is not null, value set")
        } else {
            Timber.d("Gpio is null, value not set")
        }
    }

    @Throws(IOException::class)
    fun getValue(): Boolean? {
        val sGpio = gpio
        if (sGpio != null) {
            Timber.d("Gpio is not null, value returned")
            return sGpio.value
        } else {
            Timber.d("Gpio is null, returned null")
            return null
        }
    }

    @Throws(IOException::class)
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (registered) {
            return
        }

        val manager = PeripheralManager.getInstance()

        val led = manager.openGpio(pin)
        led.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        led.setActiveType(Gpio.ACTIVE_HIGH)

        gpio = led

        registered = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        if (!registered) {
            return
        }

        setValue(false)

        registered = false

        gpio?.close()
        gpio = null
    }

}
