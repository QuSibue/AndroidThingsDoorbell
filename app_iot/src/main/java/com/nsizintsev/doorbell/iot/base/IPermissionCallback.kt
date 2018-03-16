package com.nsizintsev.doorbell.iot.base

/**
 * Created by nsizintsev on 3/12/2018.
 */

interface IPermissionCallback {

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean

}
