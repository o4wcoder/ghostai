package com.fourthwardai.ghostai.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

private const val MAX_IMAGE_DIMENSION = 512
private const val JPEG_QUALITY = 75
private const val CAPTURE_TIMEOUT_MS = 1500L

/**
 * Gives the ghost "sight" by grabbing a single front-camera frame on demand.
 * Sight is optional: every failure path returns null so the conversation continues without an image.
 */
class GhostCameraManager(context: Context) {
    private val appContext = context.applicationContext
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Binding completes asynchronously; this lets unbind() win if it's called before the bind lands.
    private var shouldBeBound = false

    /** Must be called on the main thread. */
    fun bindToLifecycle(lifecycleOwner: LifecycleOwner) {
        shouldBeBound = true
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener(
            {
                if (!shouldBeBound) return@addListener
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        Timber.w("CGH: No front camera available, ghost sight disabled")
                        return@addListener
                    }

                    // The image is downscaled to 512px anyway, so ask for a small sensor output to keep capture fast.
                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build()

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setResolutionSelector(resolutionSelector)
                        .build()

                    imageCapture?.let { provider.unbind(it) }
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, capture)
                    imageCapture = capture
                    Timber.d("CGH: Camera bound for ghost sight")
                } catch (e: Exception) {
                    imageCapture = null
                    Timber.e(e, "CGH: Failed to bind camera")
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    /** Captures a frame and returns it as a Base64 JPEG (no line wrapping), or null if unavailable. */
    suspend fun captureImage(): String? {
        val capture = imageCapture ?: return null
        val startTime = System.currentTimeMillis()

        val bitmap = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { takePicture(capture) }
        if (bitmap == null) {
            Timber.w("CGH: Image capture failed or timed out")
            return null
        }

        return withContext(Dispatchers.Default) {
            try {
                val scaled = scaleToMaxDimension(bitmap, MAX_IMAGE_DIMENSION)
                val bytes = ByteArrayOutputStream().use { stream ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                    stream.toByteArray()
                }
                Timber.d(
                    "CGH: Captured image ${scaled.width}x${scaled.height}, ${bytes.size / 1024}KB " +
                        "in ${System.currentTimeMillis() - startTime}ms",
                )
                if (scaled !== bitmap) scaled.recycle()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (e: Exception) {
                Timber.e(e, "CGH: Failed to encode captured image")
                null
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** Releases the camera so the OS camera indicator turns off. Must be called on the main thread. */
    fun unbind() {
        shouldBeBound = false
        val capture = imageCapture ?: return
        imageCapture = null
        cameraProvider?.unbind(capture)
        Timber.d("CGH: Camera unbound, ghost sight off")
    }

    fun release() {
        unbind()
        cameraExecutor.shutdown()
    }

    private suspend fun takePicture(capture: ImageCapture): Bitmap? = suspendCancellableCoroutine { cont ->
        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = try {
                        image.toUprightBitmap()
                    } catch (e: Exception) {
                        Timber.e(e, "CGH: Failed to convert captured image")
                        null
                    } finally {
                        image.close()
                    }
                    if (cont.isActive) {
                        cont.resume(bitmap)
                    } else {
                        bitmap?.recycle()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "CGH: Image capture error")
                    if (cont.isActive) cont.resume(null)
                }
            },
        )
    }

    private fun ImageProxy.toUprightBitmap(): Bitmap {
        val raw = toBitmap()
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { raw.recycle() }
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
    }
}
