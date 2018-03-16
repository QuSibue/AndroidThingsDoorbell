package com.nsizintsev.doorbell.iot.base

/**
 * Created by nsizintsev on 3/9/2018.
 */
interface IPermissionManager {

    fun checkPermission(permission: String): Boolean

    fun requestPermissions(permissions: Array<String>, code: Int)

}