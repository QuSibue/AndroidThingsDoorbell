package com.nsizintsev.doorbell.iot.base

import android.os.Handler

/**
 * Created by nsizintsev on 3/12/2018.
 */

interface IHandlerProvider {

    fun getCallbackHandler(): Handler

}
