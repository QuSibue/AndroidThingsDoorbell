package com.nsizintsev.doorbell.iot.peripheral

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.nsizintsev.doorbell.iot.base.IPermissionManager
import timber.log.Timber
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import android.hardware.camera2.CameraCharacteristics
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.camera2.CameraManager
import android.os.Looper
import android.view.SurfaceHolder
import com.nsizintsev.doorbell.common.entity.ImageData
import com.nsizintsev.doorbell.iot.base.IActivityProvider
import com.nsizintsev.doorbell.iot.base.IPermissionCallback
import com.nsizintsev.doorbell.iot.util.CameraUtil
import com.nsizintsev.doorbell.iot.view.AutoFitSurfaceView
import com.nsizintsev.doorbell.iot.view.AutoFitTextureView
import kotlinx.android.synthetic.main.activity_main.view.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Created by nsizintsev on 3/9/2018.
 */

class CameraManager(private val lifecycle: Lifecycle,
                    private val activityProvider: IActivityProvider,
                    private val permissionManager: IPermissionManager,
                    private val uiProvider: UiProvider,
                    private val cameraListener: CameraListener) : LifecycleObserver, IPermissionCallback {

    companion object {
        const val PR_CAMERA = 1

        const val MAX_PREVIEW_WIDTH = 1280

        const val MAX_PREVIEW_HEIGHT = 720

        const val STATE_PREVIEW = 0

        const val STATE_WAITING_LOCK = 1

        const val STATE_WAITING_PRECAPTURE = 2

        const val STATE_WAITING_NON_PRECAPTURE = 3

        const val STATE_PICTURE_TAKEN = 4

    }

    private val cameraLock = Semaphore(1)

    private var surfaceViewHolder: SurfaceViewHolder? = null

    private val imageAvailableCallback = ImageAvailableCallback()

    private val cameraCaptureCallback = CameraCaptureCallback()

    private val cameraOpened = AtomicBoolean(false)

    private var cameraThread: HandlerThread? = null

    private var cameraHandler: Handler? = null

    private var camera: CameraDevice? = null

    private var imageReader: ImageReader? = null

    private var cameraSession: CameraCaptureSession? = null

    private var autoFocusSupported: Boolean = false

    private var autoAeSupported: Boolean = false

    private var cameraSensorRotation: Int? = null

    private var cameraState: Int? = null

    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var previewRequest: CaptureRequest? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        val surfaceView = uiProvider.getSurfaceView()
        surfaceViewHolder = SurfaceViewHolder(surfaceView)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        startCameraThread()
        tryOpenCamera()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        closeCamera()
        stopCameraThread()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        surfaceViewHolder?.release()
        surfaceViewHolder = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        if (requestCode == PR_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    tryOpenCamera()
                }
            }
            return true
        }
        return false
    }

    private fun startCameraThread() {
        if (cameraThread != null) {
            return
        }

        cameraThread = HandlerThread("CameraThread_${this.hashCode()}")
        cameraThread!!.start()
        cameraHandler = Handler(cameraThread!!.looper)
    }

    private fun stopCameraThread() {
        cameraThread?.quit()
        cameraThread = null
        cameraHandler = null
    }

    private fun tryOpenCamera() {
        if (permissionManager.checkPermission(Manifest.permission.CAMERA)) {
            val surfaceViewHolder = this.surfaceViewHolder
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    && surfaceViewHolder != null
                    && surfaceViewHolder.isAvailable) {
                cameraHandler!!.post({
                    openCamera()
                })
            }
        } else {
            permissionManager.requestPermissions(arrayOf(Manifest.permission.CAMERA), PR_CAMERA)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        if (cameraOpened.get()) {
            return
        }
        cameraOpened.set(true)

        if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
            if (!Thread.interrupted()) {
                throw RuntimeException("Failed to open, lock failed")
            }
        }

        if (Thread.interrupted()) {
            cameraLock.release()
            return
        }

        if (camera != null) {
            cameraLock.release()
            return
        }

        val activity = activityProvider.getActivity()
        val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList

        if (cameraIds.isEmpty()) {
            Timber.d("Failed to open, no cameras")
            return
        }

        val cameraId = setupCameraOutputs(activity, cameraManager)
//        surfaceViewHolder!!.configureTransform()

        cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        this@CameraManager.camera = camera
                        cameraLock.release()
                        tryOpenSession()
                    }

                    override fun onDisconnected(camera: CameraDevice?) {
                        this@CameraManager.camera = null
                        cameraLock.release()
                    }

                    override fun onError(camera: CameraDevice?, error: Int) {
                        cameraLock.release()
                        Timber.d("Camera error occurred $error")
                        onPause()
                    }
                },
                cameraHandler)
    }

    private fun setupCameraOutputs(activity: Activity, cameraManager: CameraManager): String {
        val cameraId = cameraManager.cameraIdList[0]

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val largest = map.getOutputSizes(ImageFormat.JPEG).maxWith(CameraUtil.CompareSizesByArea())!!

        val imageReader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.JPEG, 2)
        imageReader.setOnImageAvailableListener(imageAvailableCallback, cameraHandler)
        this.imageReader = imageReader

        val cameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
        this.cameraSensorRotation = cameraSensorOrientation

        val displayRotation = activity.windowManager.defaultDisplay.rotation
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (cameraSensorOrientation == 90 || cameraSensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (cameraSensorOrientation == 0 || cameraSensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
        }

        val surfaceView = surfaceViewHolder!!.surfaceView

        val rotatedPreviewWidth: Int
        val rotatedPreviewHeight: Int
        val maxPreviewWidth: Int
        val maxPreviewHeight: Int

        if (!swappedDimensions) {
            rotatedPreviewWidth = surfaceView.width
            rotatedPreviewHeight = surfaceView.height
            maxPreviewWidth = MAX_PREVIEW_WIDTH
            maxPreviewHeight = MAX_PREVIEW_HEIGHT
        } else {
            rotatedPreviewWidth = surfaceView.height
            rotatedPreviewHeight = surfaceView.width
            maxPreviewWidth = MAX_PREVIEW_HEIGHT
            maxPreviewHeight = MAX_PREVIEW_WIDTH
        }

        val mPreviewSize = CameraUtil.chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                rotatedPreviewWidth,
                rotatedPreviewHeight,
                maxPreviewWidth,
                maxPreviewHeight,
                largest)

        val wait = Object()

        Handler(Looper.getMainLooper()).post({
            surfaceView.setAspectRatio(mPreviewSize.width, mPreviewSize.height)
            synchronized(wait, { wait.notify() })
        })

        synchronized(wait, { wait.wait() })

        val availableAf = characteristics[CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES]
        autoFocusSupported = availableAf.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

        val availableAe = characteristics[CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES]
        autoAeSupported = availableAe.contains(CameraCharacteristics.CONTROL_AE_MODE_ON)

        return cameraId
    }

    private fun closeCamera() {
        if (!cameraOpened.get()) {
            return
        }

        if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
            throw RuntimeException("Failed to close, lock failed")
        }

        imageReader?.close()
        imageReader = null

        cameraSession?.close()
        cameraSession = null

        camera?.close()
        camera = null

        cameraState = null
        previewRequestBuilder = null
        previewRequest = null

        cameraLock.release()

        cameraOpened.set(false)
    }

    fun tryOpenSession() {
        cameraLock.acquire()

        val camera = camera

        if (camera == null) {
            cameraLock.release()
            return
        }

//        val textureSurface = textureViewHolder!!.textureView.surfaceTexture
//        val uiSurface = Surface(textureSurface)

        val uiSurface = surfaceViewHolder!!.surfaceView.holder.surface

        val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequestBuilder.addTarget(uiSurface)
        this.previewRequestBuilder = previewRequestBuilder

        val surfaces = ArrayList<Surface>()
        surfaces.add(uiSurface)
        surfaces.add(imageReader!!.surface)

        camera.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession?) {
                        this@CameraManager.cameraSession = session

                        if (autoFocusSupported) {
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }

                        previewRequest = previewRequestBuilder.build()

                        cameraLock.release()

                        session?.setRepeatingRequest(previewRequestBuilder.build(),
                                cameraCaptureCallback,
                                cameraHandler)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession?) {
                        cameraLock.release()
                    }
                },
                cameraHandler)
    }

    fun takePicture() {
        cameraHandler?.post({
            if (camera == null || cameraSession == null) {
                Timber.d("Failed to make photo, camera not initialized")
                return@post
            }

            if (cameraState != STATE_PREVIEW) {
                Timber.d("Failed to make photo, state is not preview")
                return@post
            }

            lockFocus()
        })
    }

    private fun lockFocus() {
        val previewRequestBuilder = this@CameraManager.previewRequestBuilder!!
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        cameraState = STATE_WAITING_LOCK
        cameraSession!!.capture(previewRequestBuilder.build(), cameraCaptureCallback, cameraHandler)
    }

    private fun unlockFocus() {
        previewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
        cameraSession!!.capture(previewRequestBuilder!!.build(), cameraCaptureCallback, cameraHandler)
        cameraState = STATE_PREVIEW
        cameraSession!!.setRepeatingRequest(previewRequest, cameraCaptureCallback, cameraHandler)
    }

    private fun captureStillPicture() {
        cameraState = STATE_PICTURE_TAKEN

        if (camera == null) {
            return
        }

        val activity = activityProvider.getActivity()

        val captureBuilder = camera!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureBuilder.addTarget(imageReader!!.surface)
        if (autoFocusSupported) {
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        val rotation = activity.windowManager.defaultDisplay.rotation
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, CameraUtil.getCaptureOrientation(rotation, cameraSensorRotation!!))

        val captureCallback = object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureCompleted(session: CameraCaptureSession?, request: CaptureRequest?, result: TotalCaptureResult?) {
                unlockFocus()
            }

            override fun onCaptureFailed(session: CameraCaptureSession?, request: CaptureRequest?, failure: CaptureFailure?) {
                unlockFocus()
            }

            override fun onCaptureSequenceAborted(session: CameraCaptureSession?, sequenceId: Int) {
                unlockFocus()
            }

        }

        cameraSession!!.stopRepeating()
        cameraSession!!.abortCaptures()
        cameraSession!!.capture(captureBuilder.build(), captureCallback, cameraHandler)
    }

    private fun runPrecaptureSequence() {
        previewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
        cameraState = STATE_WAITING_PRECAPTURE
        cameraSession!!.capture(previewRequestBuilder!!.build(), cameraCaptureCallback, cameraHandler)
    }

    interface CameraListener {

        fun onPhotoMade(image: ImageData)

    }

    interface UiProvider {

        fun getSurfaceView(): AutoFitSurfaceView

    }

    private inner class SurfaceViewHolder(val surfaceView: AutoFitSurfaceView) : SurfaceHolder.Callback {

        private var previewSize: Size? = null

        var isAvailable = false
            private set

        init {
            surfaceView.holder.addCallback(this)
        }

        override fun surfaceCreated(holder: SurfaceHolder?) {

        }

        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            val prevSize = previewSize
            previewSize = Size(width, height)
            isAvailable = true
            if (prevSize != previewSize) {
                tryOpenCamera()
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            previewSize = null
            isAvailable = false
            closeCamera()
        }

        fun release() {
            surfaceView.holder.removeCallback(this)
        }

    }

    private inner class TextureViewHolder(val textureView: AutoFitTextureView) : TextureView.SurfaceTextureListener {

        private var previewSize: Size? = null
            set(value) {
                field = value
                if (value != null) {
                    textureView.surfaceTexture.setDefaultBufferSize(value.width, value.height)
                }
            }

        init {
            textureView.surfaceTextureListener = this
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            previewSize = Size(width, height)
            tryOpenCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            previewSize = Size(width, height)
            configureTransform()
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {

        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            return true
        }

        fun configureTransform() {
            val previewSize = this.previewSize
            if (!textureView.isAvailable || previewSize == null) {
                return
            }

            val activity = activityProvider.getActivity()
            val rotation = activity.windowManager.defaultDisplay.rotation

            val matrix = Matrix()
            val viewRect = RectF(0f, 0f, textureView.width.toFloat(), textureView.height.toFloat())
            val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()

            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                val scale = Math.max(
                        textureView.height.toFloat() / previewSize.height.toFloat(),
                        textureView.width.toFloat() / previewSize.width.toFloat())
                matrix.postScale(scale, scale, centerX, centerY)
                matrix.postRotate(90f * (rotation.toFloat() - 2f), centerX, centerY)
            } else if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180f, centerX, centerY)
            }

            textureView.setTransform(matrix)
        }

        fun release() {
            textureView.surfaceTextureListener = null
        }

    }

    private inner class CameraCaptureCallback : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureProgressed(session: CameraCaptureSession?, request: CaptureRequest?, partialResult: CaptureResult) {
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession?, request: CaptureRequest?, result: TotalCaptureResult) {
            process(result)
        }

        override fun onCaptureFailed(session: CameraCaptureSession?, request: CaptureRequest?, failure: CaptureFailure?) {
            super.onCaptureFailed(session, request, failure)
        }

        override fun onCaptureBufferLost(session: CameraCaptureSession?, request: CaptureRequest?, target: Surface?, frameNumber: Long) {
            super.onCaptureBufferLost(session, request, target, frameNumber)
        }

        private fun process(result: CaptureResult) {
            if (cameraState == null) {
                cameraState = STATE_PREVIEW
            }

            when (cameraState) {
                STATE_PREVIEW -> {

                }
                STATE_WAITING_LOCK -> {
                    val afState = result[CaptureResult.CONTROL_AF_STATE]
                    if (afState == null) {
                        captureStillPicture()
                    } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                            || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                            || (afState == CaptureResult.CONTROL_AE_STATE_INACTIVE && !autoFocusSupported)) {
                        val aeState = result[CaptureResult.CONTROL_AE_STATE]
                        if (aeState == null
                                || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED
                                || !autoAeSupported) {
                            captureStillPicture()
                        } else {
                            runPrecaptureSequence()
                        }
                    }
                }
                STATE_WAITING_PRECAPTURE -> {
                    val aeState = result[CaptureResult.CONTROL_AE_STATE]
                    if (aeState == null
                            || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
                            || aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        cameraState = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    val aeState = result[CaptureResult.CONTROL_AE_STATE]
                    if (aeState == null
                            || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        captureStillPicture()
                    }
                }
            }

        }

    }

    private inner class ImageAvailableCallback : ImageReader.OnImageAvailableListener {

        override fun onImageAvailable(p0: ImageReader) {
            val image = p0.acquireLatestImage()

            val byteBuffer = image.planes[0].buffer
            val byteArray = ByteArray(byteBuffer.capacity())
            byteBuffer.get(byteArray)
            val imageData = ImageData(image.width, image.height, image.format, image.cropRect, byteArray)
            image.close()

            cameraListener.onPhotoMade(imageData)
        }

    }

}
