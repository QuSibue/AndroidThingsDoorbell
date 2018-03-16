package com.nsizintsev.doorbell.common.model

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.storage.UploadTask
import com.nsizintsev.doorbell.common.entity.ChangeItemRequest
import com.nsizintsev.doorbell.common.entity.firestore.DoorbellCallDbEntity
import io.reactivex.*
import java.util.concurrent.Semaphore

/**
 * Created by nsizintsev on 3/14/2018.
 */

class DoorbellCallDbModel {

    companion object {
        private const val COLLECTION_DOORBELL = "doorbells"
    }

    fun addDoorbellCallToDb(result: UploadTask.TaskSnapshot): Single<DoorbellCallDbEntity> {
        return Single.just(result)
                .map {
                    val firestore = FirebaseFirestore.getInstance()
                    val user = FirebaseAuth.getInstance().currentUser!!

                    val reference = result.metadata!!.reference!!
                    val model = DoorbellCallDbEntity(user.uid, result.metadata!!.updatedTimeMillis, reference.path)

                    val task = firestore.collection(COLLECTION_DOORBELL).document(reference.name).set(model)
                    Tasks.await(task)

                    return@map model
                }
    }

    fun getDoorbellCalls(startAfter: DoorbellCallDbEntity? = null, pageSize: Long? = null): Single<MutableList<DoorbellCallDbEntity>> {
        return Single.fromCallable({
            val firestore = FirebaseFirestore.getInstance()
            val currentUser = FirebaseAuth.getInstance().currentUser!!

            val query = firestore.collection(COLLECTION_DOORBELL)
                    .whereEqualTo(DoorbellCallDbEntity.FIELD_UID, currentUser.uid)
                    .orderBy(DoorbellCallDbEntity.FIELD_DATE, Query.Direction.ASCENDING)
            if (startAfter != null) {
                val startAfterDoc = firestore.collection(COLLECTION_DOORBELL).document(startAfter.file).get()
                query.startAfter(startAfterDoc)
            }
            if (pageSize != null) {
                query.limit(pageSize)
            }

            val task = query.get()
            val result = Tasks.await(task)

            result.documents
        }).map { ArrayList(it.map { DoorbellCallDbEntity.fromMap(it.data) }) }
    }

    fun subscribeForCallUpdates(): Observable<ChangeItemRequest<DoorbellCallDbEntity>> {
        return Observable.create(object : ObservableOnSubscribe<ChangeItemRequest<DoorbellCallDbEntity>> {

            private val semaphore = Semaphore(1)

            private var handler: Handler? = null
                set(value) {
                    field = value
                    semaphore.release()
                }

            override fun subscribe(emitter: ObservableEmitter<ChangeItemRequest<DoorbellCallDbEntity>>) {
                semaphore.acquire()

                val firestore = FirebaseFirestore.getInstance()
                val currentUser = FirebaseAuth.getInstance().currentUser!!

                val query = firestore.collection(COLLECTION_DOORBELL)
                        .whereEqualTo(DoorbellCallDbEntity.FIELD_UID, currentUser.uid)
                        .orderBy(DoorbellCallDbEntity.FIELD_DATE, Query.Direction.ASCENDING)

                val registration = query.addSnapshotListener({ snapshot, ex ->
                    if (ex != null) {
                        return@addSnapshotListener
                    }

                    semaphore.acquire()
                    handler?.post({
                        snapshot.documentChanges
                                .map {
                                    val dbEntity = DoorbellCallDbEntity.fromMap(it.document.data)
                                    when (it.type) {
                                        DocumentChange.Type.ADDED -> ChangeItemRequest(dbEntity, ChangeItemRequest.ADDED)
                                        DocumentChange.Type.MODIFIED -> ChangeItemRequest(dbEntity, ChangeItemRequest.MODIFIED)
                                        DocumentChange.Type.REMOVED -> ChangeItemRequest(dbEntity, ChangeItemRequest.REMOVED)
                                    }
                                }
                                .forEach { emitter.onNext(it) }
                    })
                    semaphore.release()
                })

                emitter.setCancellable({
                    handler!!.post({
                        registration.remove()
                        Looper.myLooper().quit()
                    })
                })

                Looper.prepare()
                handler = object : Handler() {
                    override fun handleMessage(msg: Message?) {
                        msg?.callback?.run()
                    }
                }
                Looper.loop()
            }
        })
    }

}
