package com.fourthwardai.ghostai.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.PowerManager
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import timber.log.Timber
import kotlin.math.abs

// Tunable on the porch. See greet_visitors_notes.md for the reasoning behind each value.
private const val MODEL_ASSET = "efficientdet_lite0.tflite"
private const val PERSON_SCORE_THRESHOLD = 0.5f
private const val MAX_PEOPLE = 5

/**
 * Only people whose box is at least this tall (fraction of frame height) count, so the street behind the door doesn't.
 * Starting guess: ~0.6 sitting close at a desk. Tune on the porch from the logged h= values.
 */
private const val MIN_PERSON_HEIGHT_FRACTION = 0.3f

/** The porch must be empty this long before the next group counts as a new arrival. */
private const val EMPTY_BEFORE_ARRIVAL_MS = 8000L

/** Someone must stay this long before it's an arrival, so kids running past don't count. */
private const val ARRIVAL_DWELL_MS = 1500L

/** A gap in detections shorter than this (a missed frame, or the 2s still re-check) doesn't break presence. */
private const val PRESENCE_GAP_TOLERANCE_MS = 2500L

/** ~2 fps is plenty for the 1.5s dwell rule and keeps CPU/heat low on the tablet. */
private const val FRAME_INTERVAL_MS = 500L

/** Even with no motion, re-run the model this often so someone standing still keeps counting as present. */
private const val MAX_STILL_INTERVAL_MS = 2000L

/** Mean per-pixel luma change (0-255) on a tiny thumbnail that counts as motion. */
private const val MOTION_THRESHOLD = 6
private const val MOTION_THUMB_WIDTH = 32
private const val MOTION_THUMB_HEIGHT = 24

private const val STATS_LOG_INTERVAL_MS = 30_000L
private const val THERMAL_FORECAST_SECONDS = 10

/**
 * Watches the front camera for people on the porch using on-device person detection (MediaPipe, CPU only so it never
 * competes with the shaders for the GPU). Whole-body detection is used rather than face detection because masks,
 * face paint and hoods defeat face detection.
 *
 * An arrival is a person (big enough to be at the door) present for [ARRIVAL_DWELL_MS] after the porch was empty for
 * [EMPTY_BEFORE_ARRIVAL_MS]; each group arrives once, until the porch clears again.
 *
 * For now this only logs what it sees (people, box sizes, arrivals, inference time, thermal state) so the thresholds
 * and tablet performance can be checked before any greeting is wired up.
 */
class VisitorDetector(context: Context) : ImageAnalysis.Analyzer {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(PowerManager::class.java)

    // Only touched on the analysis thread, or under the lock in reset()
    private var detector: ObjectDetector? = null
    private var previousThumb: IntArray? = null
    private var lastAnalyzedAt = 0L
    private var lastInferenceAt = 0L
    private var lastPersonCount = -1

    // Arrival state. Starts unarmed so whoever is in view at startup isn't treated as an arrival.
    private var isArmed = false
    private var presentSince = 0L
    private var lastSeenAt = 0L
    private var emptySince = 0L
    private var wasPaused = false

    private var statsWindowStart = 0L
    private var inferenceRuns = 0
    private var inferenceTotalMs = 0L
    private var inferenceMaxMs = 0L
    private var motionSkips = 0

    /** Set while the conversation isn't idle: no greeting can fire then, so don't spend CPU while the ghost animates. */
    @Volatile
    var isPaused = false

    override fun analyze(image: ImageProxy) {
        try {
            synchronized(this) { analyzeLocked(image) }
        } catch (e: Exception) {
            Timber.e(e, "CGH: Visitor detection failed")
        } finally {
            image.close()
        }
    }

    private fun analyzeLocked(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (statsWindowStart == 0L) statsWindowStart = now
        maybeLogStats(now)

        if (isPaused) {
            wasPaused = true
            return
        }
        if (wasPaused) {
            // Time spent paused wasn't observed, so it can't count as empty or as dwell; start both fresh
            wasPaused = false
            presentSince = 0L
            emptySince = 0L
        }
        if (now - lastAnalyzedAt < FRAME_INTERVAL_MS) return
        lastAnalyzedAt = now

        val bitmap = image.toUprightBitmap()
        try {
            // The motion check is nearly free; skipping the model on a still, empty porch is what keeps heat down
            val moved = detectMotion(bitmap)
            if (!moved && now - lastInferenceAt < MAX_STILL_INTERVAL_MS) {
                motionSkips++
                return
            }
            lastInferenceAt = now
            val visitors = detectPeople(bitmap) ?: return
            updateArrival(visitors, now)
        } finally {
            bitmap.recycle()
        }
    }

    /** Returns how many people are close enough to count as visitors, or null if detection isn't available. */
    private fun detectPeople(bitmap: Bitmap): Int? {
        val objectDetector = detector ?: createDetector()?.also { detector = it } ?: return null

        val start = SystemClock.elapsedRealtime()
        val result = objectDetector.detect(BitmapImageBuilder(bitmap).build())
        val elapsed = SystemClock.elapsedRealtime() - start
        inferenceRuns++
        inferenceTotalMs += elapsed
        inferenceMaxMs = maxOf(inferenceMaxMs, elapsed)

        val people = result.detections()
        val visitors = people.count { it.boundingBox().height() / bitmap.height >= MIN_PERSON_HEIGHT_FRACTION }
        if (people.isEmpty() && lastPersonCount == 0) return visitors
        lastPersonCount = people.size

        // Box height as a fraction of the frame is the "how close are they" signal used to tune the min size later
        val boxes = people.joinToString { person ->
            val box = person.boundingBox()
            val score = person.categories().firstOrNull()?.score() ?: 0f
            "h=%.2f w=%.2f s=%.2f".format(box.height() / bitmap.height, box.width() / bitmap.width, score)
        }
        Timber.d("CGH: Visitors: ${people.size} person(s), $visitors close enough, in ${elapsed}ms [$boxes]")
        return visitors
    }

    private fun updateArrival(visitors: Int, now: Long) {
        if (visitors > 0) {
            emptySince = 0L
            if (presentSince == 0L || now - lastSeenAt > PRESENCE_GAP_TOLERANCE_MS) presentSince = now
            lastSeenAt = now
            if (isArmed && now - presentSince >= ARRIVAL_DWELL_MS) {
                isArmed = false
                Timber.d("CGH: Visitors arrived: $visitors person(s) after ${now - presentSince}ms dwell")
            }
        } else {
            if (emptySince == 0L) emptySince = now
            if (!isArmed && now - emptySince >= EMPTY_BEFORE_ARRIVAL_MS) {
                isArmed = true
                Timber.d("CGH: Visitors: porch clear, ready for the next arrival")
            }
        }
    }

    private fun createDetector(): ObjectDetector? = try {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build(),
            )
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(PERSON_SCORE_THRESHOLD)
            .setMaxResults(MAX_PEOPLE)
            .setCategoryAllowlist(listOf("person"))
            .build()
        ObjectDetector.createFromOptions(appContext, options).also {
            Timber.d("CGH: Visitor detector created")
        }
    } catch (e: Exception) {
        Timber.e(e, "CGH: Failed to create visitor detector")
        null
    }

    private fun detectMotion(bitmap: Bitmap): Boolean {
        val thumb = Bitmap.createScaledBitmap(bitmap, MOTION_THUMB_WIDTH, MOTION_THUMB_HEIGHT, true)
        val pixels = IntArray(MOTION_THUMB_WIDTH * MOTION_THUMB_HEIGHT)
        thumb.getPixels(pixels, 0, MOTION_THUMB_WIDTH, 0, 0, MOTION_THUMB_WIDTH, MOTION_THUMB_HEIGHT)
        if (thumb !== bitmap) thumb.recycle()

        for (i in pixels.indices) pixels[i] = pixels[i].luma()
        val previous = previousThumb
        previousThumb = pixels
        if (previous == null) return true

        var totalDiff = 0L
        for (i in pixels.indices) totalDiff += abs(pixels[i] - previous[i])
        return totalDiff / pixels.size >= MOTION_THRESHOLD
    }

    private fun Int.luma(): Int {
        val r = (this shr 16) and 0xFF
        val g = (this shr 8) and 0xFF
        val b = this and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun maybeLogStats(now: Long) {
        if (now - statsWindowStart < STATS_LOG_INTERVAL_MS) return
        val avgMs = if (inferenceRuns > 0) inferenceTotalMs / inferenceRuns else 0
        Timber.d(
            "CGH: Visitor stats (30s): inference runs=$inferenceRuns avg=${avgMs}ms max=${inferenceMaxMs}ms, " +
                "motion skips=$motionSkips, paused=$isPaused, thermal status=${powerManager.currentThermalStatus} " +
                "headroom=%.2f".format(powerManager.getThermalHeadroom(THERMAL_FORECAST_SECONDS)),
        )
        statsWindowStart = now
        inferenceRuns = 0
        inferenceTotalMs = 0
        inferenceMaxMs = 0
        motionSkips = 0
    }

    /** Releases the model and clears state. Safe to keep using afterwards; the model is recreated on the next frame. */
    fun reset() {
        synchronized(this) {
            detector?.close()
            detector = null
            previousThumb = null
            lastPersonCount = -1
            statsWindowStart = 0L
            isArmed = false
            presentSince = 0L
            lastSeenAt = 0L
            emptySince = 0L
        }
    }

    private fun ImageProxy.toUprightBitmap(): Bitmap {
        val raw = toBitmap()
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { raw.recycle() }
    }
}
