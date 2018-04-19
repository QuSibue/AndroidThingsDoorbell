package com.nsizintsev.doorbell.iot.ui.main

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.nsizintsev.doorbell.R
import com.nsizintsev.doorbell.common.entity.ImageData
import com.nsizintsev.doorbell.iot.base.IActivityProvider
import com.nsizintsev.doorbell.iot.base.IPermissionManager
import com.nsizintsev.doorbell.iot.peripheral.CameraManager
import com.nsizintsev.doorbell.iot.peripheral.controller.DoorbellController
import com.nsizintsev.doorbell.iot.ui.login.LoginActivity
import com.nsizintsev.doorbell.iot.view.AutoFitSurfaceView
import com.nsizintsev.doorbell.iot.viewmodel.AddDoorbellCallViewModel
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity(), CameraManager.UiProvider, IPermissionManager, IActivityProvider {

    private val doorbellModel = DoorbellController(lifecycle, this, this, this)

    private lateinit var uploadImageModel: AddDoorbellCallViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        uploadImageModel = ViewModelProviders.of(this).get(AddDoorbellCallViewModel::class.java)
        uploadImageModel.createRequestResult.observe(this, Observer<Boolean> { t ->
            if (t != null && t) {
                Timber.d("Image uploaded")
            } else {
                Timber.d("Image upload failed")
            }
        })

        doorbellModel.photoLiveData.observe(this, Observer<ImageData> { t ->
            if (t != null) {
                uploadImageModel.addDoorbellCall(t)
            }
        })

        navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.logout -> {
                    AuthUI.getInstance()
                            .signOut(this)
                            .addOnCompleteListener {
                                startLoginActivity()
                                finish()
                            }
                    return@setNavigationItemSelectedListener false
                }
                else -> {
                    return@setNavigationItemSelectedListener false
                }
            }
        }

    }

    override fun getActivity(): Activity = this

    override fun getSurfaceView(): AutoFitSurfaceView = surfaceView

    override fun checkPermission(permission: String): Boolean {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!doorbellModel.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun startLoginActivity() {
        startActivity(Intent(this, LoginActivity::class.java))
    }

}
