package com.nsizintsev.doorbell.common.entity.firestore

import com.google.firebase.firestore.ServerTimestamp
import java.util.*

data class DoorbellCallDbEntity(val uid: String,
                                @ServerTimestamp val date: Date,
                                val file: String) {

    companion object {
        val FIELD_UID = "uid"
        val FIELD_DATE = "date"
        val FIELD_FILE = "file"

        fun fromMap(map: Map<String, Any>): DoorbellCallDbEntity {
            return DoorbellCallDbEntity(
                    map[FIELD_UID]!! as String,
                    map[FIELD_DATE]!! as Date,
                    map[FIELD_FILE]!! as String
            )
        }
    }

    constructor(uid: String, date: Long, file: String) : this(uid, Date(date), file)

    fun toMap(): HashMap<String, Any> {
        val data = HashMap<String, Any>()
        data[FIELD_UID] = uid
        data[FIELD_DATE] = date
        data[FIELD_FILE] = file
        return data
    }

}