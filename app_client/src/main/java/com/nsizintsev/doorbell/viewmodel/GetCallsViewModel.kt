package com.nsizintsev.doorbell.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.nsizintsev.doorbell.common.entity.ChangeItemRequest
import com.nsizintsev.doorbell.common.entity.DoorbellCallViewEntity
import com.nsizintsev.doorbell.common.model.DoorbellCallModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.collections.ArrayList
import kotlin.math.sign

/**
 * Created by nsizintsev on 3/14/2018.
 */

class GetCallsViewModel : ViewModel() {

    private val lock = Semaphore(1)

    private val callsModel = DoorbellCallModel()

    private var updatesDisposable: Disposable? = null

    val calls = MutableLiveData<MutableList<DoorbellCallViewEntity>>()

    private val updateListeners = Collections.newSetFromMap<MutableLiveData<UpdateRequest>>(WeakHashMap())

    init {
        calls.value = ArrayList()
        initRealTimeUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        updatesDisposable?.dispose()
    }

    fun getCallUpdates(): LiveData<UpdateRequest> {
        val data = MutableLiveData<UpdateRequest>()
        updateListeners.add(data)
        return data
    }

    private fun initRealTimeUpdates() {
        if (updatesDisposable == null) {
            updatesDisposable = callsModel.subscribeForCallUpdates()
                    .doOnNext {
                        lock.acquire()
                    }
                    .map {
                        val collection = calls.value
                        if (collection != null) {
                            if (it.action != ChangeItemRequest.REMOVED) {
                                val position = getPositionById(collection, it.item.file)
                                if (position != -1) {
                                    return@map UpdateRequest(position, it.item, ChangeItemRequest.MODIFIED)
                                } else {
                                    val insertPosition = findInsertPosition(collection, it.item)
                                    return@map UpdateRequest(insertPosition, it.item, ChangeItemRequest.ADDED)
                                }
                            } else {
                                val position = getPositionById(collection, it.item.file)
                                if (position != -1) {
                                    return@map UpdateRequest(position, it.item, ChangeItemRequest.REMOVED)
                                }
                            }
                        }
                        return@map null
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { request ->
                                if (request != null) {
                                    val collection = calls.value!!
                                    when (request.action) {
                                        ChangeItemRequest.ADDED -> {
                                            if (request.position < collection.size) {
                                                collection.add(request.position, request.item)
                                            } else {
                                                collection.add(request.item)
                                            }
                                        }
                                        ChangeItemRequest.MODIFIED -> collection.set(request.position, request.item)
                                        ChangeItemRequest.REMOVED -> collection.removeAt(request.position)
                                    }
                                    updateListeners.forEach({ it.postValue(request) })
                                }
                                lock.release()
                            },
                            {
                                lock.release()
                            })
        }
    }

    private fun getPositionById(collection: Iterable<DoorbellCallViewEntity>, id: String): Int {
        for ((index, entity) in collection.withIndex()) {
            if (entity.file == id) {
                return index
            }
        }
        return -1
    }

    private fun findInsertPosition(collection: MutableList<DoorbellCallViewEntity>, item: DoorbellCallViewEntity): Int {
        return Math.max(0, Collections.binarySearch(collection, item, { o1, o2 -> (o1.date.time - o2.date.time).sign }) + 1)
    }

    class UpdateRequest(val position: Int, item: DoorbellCallViewEntity, action: Int) : ChangeItemRequest<DoorbellCallViewEntity>(item, action)

}
