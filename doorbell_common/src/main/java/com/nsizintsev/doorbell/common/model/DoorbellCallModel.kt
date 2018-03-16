package com.nsizintsev.doorbell.common.model

import com.nsizintsev.doorbell.common.entity.ChangeItemRequest
import com.nsizintsev.doorbell.common.entity.DoorbellCallViewEntity
import com.nsizintsev.doorbell.common.entity.ImageData
import com.nsizintsev.doorbell.common.entity.firestore.DoorbellCallDbEntity
import io.reactivex.Observable
import io.reactivex.Single

/**
 * Created by nsizintsev on 3/15/2018.
 */

class DoorbellCallModel {

    private val dbModel = DoorbellCallDbModel()

    private val storageModel = DoorbellCallStorageModel()

    fun addDoorbellCall(imageData: ImageData): Single<DoorbellCallViewEntity> {
        return storageModel
                .uploadImage(imageData)
                .map { uploadResult ->
                    dbModel.addDoorbellCallToDb(uploadResult)
                            .map { dbEntity: DoorbellCallDbEntity -> DoorbellCallViewEntity(dbEntity.uid, dbEntity.date, dbEntity.file, uploadResult.downloadUrl!!) }
                            .blockingGet()
                }
    }

    fun getDoorbellCalls(startAfter: DoorbellCallViewEntity? = null, pageSize: Long? = null): Single<out MutableList<DoorbellCallViewEntity>> {
        val startAfterDb = if (startAfter != null) DoorbellCallDbEntity(startAfter.uid, startAfter.date, startAfter.file) else null
        return getDoorbellCalls(startAfterDb, pageSize)
    }

    fun getDoorbellCalls(startAfter: DoorbellCallDbEntity? = null, pageSize: Long? = null): Single<out MutableList<DoorbellCallViewEntity>> {
        return dbModel.getDoorbellCalls(startAfter, pageSize)
                .toObservable()
                .flatMapIterable { it }
                .map { convertDbToViewEntity(it).blockingGet() }
                .collectInto(ArrayList(), { t1: ArrayList<DoorbellCallViewEntity>, t2: DoorbellCallViewEntity -> t1.add(t2) })
    }

    fun subscribeForCallUpdates(): Observable<ChangeItemRequest<DoorbellCallViewEntity>> {
        return dbModel.subscribeForCallUpdates()
                .map { dbChange ->
                    if (dbChange.action != ChangeItemRequest.REMOVED) {
                        convertDbToViewEntity(dbChange.item)
                                .map { ChangeItemRequest(it, dbChange.action) }
                                .blockingGet()
                    } else {
                        ChangeItemRequest(
                                DoorbellCallViewEntity(dbChange.item.uid, dbChange.item.date, dbChange.item.file, null),
                                dbChange.action)
                    }
                }
    }

    private fun convertDbToViewEntity(dbEntity: DoorbellCallDbEntity): Single<DoorbellCallViewEntity> {
        return storageModel.getDownloadUri(dbEntity)
                .map { DoorbellCallViewEntity(dbEntity.uid, dbEntity.date, dbEntity.file, it) }
    }

}
