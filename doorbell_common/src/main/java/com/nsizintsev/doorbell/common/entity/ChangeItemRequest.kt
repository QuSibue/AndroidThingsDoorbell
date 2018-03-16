package com.nsizintsev.doorbell.common.entity

/**
 * Created by nsizintsev on 3/16/2018.
 */

open class ChangeItemRequest<T>(val item: T, val action: Int) {

    companion object {
        val ADDED = 0
        val MODIFIED = 1
        val REMOVED = 2
    }

}
