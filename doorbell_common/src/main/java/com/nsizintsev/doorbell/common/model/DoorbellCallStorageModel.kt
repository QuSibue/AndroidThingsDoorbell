package com.nsizintsev.doorbell.common.model

import android.graphics.ImageFormat
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import com.nsizintsev.doorbell.common.entity.ImageData
import com.nsizintsev.doorbell.common.entity.firestore.DoorbellCallDbEntity
import io.reactivex.Single

/**
 * Created by nsizintsev on 3/13/2018.
 */

class DoorbellCallStorageModel {

    fun uploadImage(image: ImageData): Single<UploadTask.TaskSnapshot> {
        return Single.just(image)
                .map({
                    val storage = FirebaseStorage.getInstance()
                    val currentUser = FirebaseAuth.getInstance().currentUser!!

                    val imageName = getImageName(image.format)
                    val imageRef = storage.getReference("/${currentUser.uid}/doorbell/$imageName")

                    val task = imageRef.putBytes(image.byteArray)
                    Tasks.await(task)
                })
    }

    fun getDownloadUri(doorbellCallDbEntity: DoorbellCallDbEntity): Single<Uri> {
        return Single.just(doorbellCallDbEntity)
                .map {
                    val storage = FirebaseStorage.getInstance()
                    val reference = storage.getReference(it.file)
                    Tasks.await(reference.downloadUrl)
                }
    }

    private fun getImageName(format: Int) = "${System.currentTimeMillis()}_${System.nanoTime()}.${resolveImageFormat(format)}"

    private fun resolveImageFormat(format: Int): String {
        when (format) {
            ImageFormat.JPEG -> return "jpeg"
        }
        throw IllegalArgumentException("Unsupported format")
    }

}
