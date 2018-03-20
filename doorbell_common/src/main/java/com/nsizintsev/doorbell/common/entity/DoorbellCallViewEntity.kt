package com.nsizintsev.doorbell.common.entity

import android.net.Uri
import java.util.*

/**
 * Created by nsizintsev on 3/15/2018.
 */

data class DoorbellCallViewEntity(val uid: String,
                                  val date: Date,
                                  val file: String,
                                  val fileUri: Uri?)