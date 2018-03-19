package com.nsizintsev.doorbell

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Created by nsizintsev on 3/19/2018.
 */

class DoorbellApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val firestore = FirebaseFirestore.getInstance()
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setSslEnabled(true)
                .setPersistenceEnabled(true)
                .build()
    }

}
