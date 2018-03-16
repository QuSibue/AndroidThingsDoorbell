package com.nsizintsev.doorbell.iot.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import com.nsizintsev.doorbell.common.entity.ImageData
import com.nsizintsev.doorbell.common.model.DoorbellCallModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by nsizintsev on 3/9/2018.
 */

class UploadPictureViewModel(application: Application) : AndroidViewModel(application) {

    val uploadImageLiveData = MutableLiveData<Boolean>()

    private val uploadModel = DoorbellCallModel()

    fun uploadImage(image: ImageData) {
        uploadModel.addDoorbellCall(image)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            uploadImageLiveData.postValue(true)
                        },
                        {
                            uploadImageLiveData.postValue(false)
                        })
    }

}
