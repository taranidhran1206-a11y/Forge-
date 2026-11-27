package com.forge.app.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.forge.app.R
import com.forge.app.ui.kit.Kit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

/**
 * The screen an alarm opens: shows over the lock screen and only lets the alarm stop once the
 * push-ups or squats are counted by the camera, or the sums are solved.
 */
class AlarmActivity : AppCompatActivity() {

    private var alarm: AlarmStore.Alarm? = null
    private var preview = false
    private var finished = false

    private lateinit var content: LinearLayout
    private var counter: RepCounter? = null
    private var detector: PoseDetector? = null
    private var analysisExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var countView: TextView? = null
    private var statusView: TextView? = null
    private var target = 15

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = 0xFF0A0A0A.toInt()
        window.navigationBarColor = 0xFF0A0A0A.toInt()

        preview = intent.getBooleanExtra(AlarmStore.EXTRA_PREVIEW, false)
        val id = intent.getIntExtra(AlarmStore.EXTRA_ID, -1)
        alarm = AlarmStore.get(this, id)
        if (alarm == null && preview) {
            alarm = AlarmStore.Alarm(-1, 0, 0, challenge = intent.getStringExtra("challenge") ?: "pushups", reps = intent.getIntExtra("reps", 10))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (preview || finished) finish()
            }
        })

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A0A.toInt())
            val pad = Kit.dimen(this@AlarmActivity, R.dimen.page_pad)
            setPadding(pad, pad + Kit.dp(this@AlarmActivity, 16), pad, pad)
        }
        setContentView(content)

        val a = alarm
        if (a == null) {
            done("Alarm stopped")
        } else if (a.challenge == "math") {
            showMath(3)
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            showMath(5, note = "Forge can't use the camera, so it's sums today. Allow the camera in the alarm settings to count reps.")
        } else {
            showReps(RepCounter.Exercise.of(a.challenge), a.reps)
        }
    }

    private fun header() {
        content.removeAllViews()
        content.addView(Kit.label(this, if (preview) "Practice run" else "Wake-up challenge"))
        content.addView(TextView(this).apply {
            text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            setTextColor(Kit.FG)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        })
        alarm?.label?.takeIf { it.isNotBlank() }?.let { content.addView(Kit.sub(this, it)) }
        content.addView(Kit.spacer(this, 16))
    }

    // ── reps ──

    private fun showReps(exercise: RepCounter.Exercise, reps: Int) {
        header()
        target = reps
        counter = RepCounter(exercise)

        val card = Kit.card(this, background = R.drawable.bg_card_next)
        countView = Kit.num(this, "0 / $reps").apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f) }
        card.addView(countView)
        card.addView(Kit.label(this, exercise.label, bottom = 6))
        statusView = Kit.body(this, exercise.tip, Kit.SOFT)
        card.addView(statusView)
        content.addView(card)

        val box = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Kit.CARD); cornerRadius = Kit.dpf(this@AlarmActivity, 16f); setStroke(Kit.dp(this@AlarmActivity, 1), Kit.LINE)
            }
            clipToOutline = true
        }
        val previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        box.addView(previewView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        content.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = Kit.dp(this, 12) })

        content.addView(Kit.quiet(this, "Camera not working? Do 5 sums instead") { stopCamera(); showMath(5) })
        if (preview) content.addView(Kit.quiet(this, "Close practice") { finish() })

        startCamera(previewView)
    }

    private fun startCamera(previewView: PreviewView) {
        detector = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
        analysisExecutor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val p = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor!!) { proxy -> analyse(proxy) }
                val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, p, analysis)
            } catch (_: Exception) {
                showMath(5, note = "The camera didn't start, so it's sums instead.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyse(proxy: ImageProxy) {
        val media = proxy.image
        val d = detector
        if (media == null || d == null || finished) { proxy.close(); return }
        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        d.process(input)
            .addOnSuccessListener { pose -> onPose(pose) }
            .addOnCompleteListener { proxy.close() }
    }

    private fun onPose(pose: Pose) {
        val c = counter ?: return
        if (finished) return
        val pushups = c.exercise == RepCounter.Exercise.PUSHUPS
        val (a, b, cc) = if (pushups) Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
        else Triple(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
        val (ra, rb, rc) = if (pushups) Triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        else Triple(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)

        fun side(x: Int, y: Int, z: Int): Pair<Double, Float>? {
            val p1 = pose.getPoseLandmark(x) ?: return null
            val p2 = pose.getPoseLandmark(y) ?: return null
            val p3 = pose.getPoseLandmark(z) ?: return null
            val seen = minOf(p1.inFrameLikelihood, p2.inFrameLikelihood, p3.inFrameLikelihood)
            val ang = RepCounter.angleAt(
                p1.position.x.toDouble(), p1.position.y.toDouble(),
                p2.position.x.toDouble(), p2.position.y.toDouble(),
                p3.position.x.toDouble(), p3.position.y.toDouble()
            )
            return ang to seen
        }
        val best = listOfNotNull(side(a, b, cc), side(ra, rb, rc)).maxByOrNull { it.second }
        if (best == null || best.second < 0.5f) {
            c.lost()
            status(if (pushups) "Can't see your arms. Move the phone back so your whole body fits." else "Can't see your legs. Move the phone back so you fit head to feet.")
            return
        }
        if (pushups && !lyingDown(pose)) {
            status("Get into push-up position, side-on to the phone.")
            return
        }
        val counted = c.feed(best.first)
        if (counted) buzz()
        countView?.text = "${c.reps} / $target"
        status(
            when {
                c.reps >= target -> "Done."
                c.phase == RepCounter.Phase.DOWN -> "Good. Now push back up."
                c.phase == RepCounter.Phase.UP -> if (pushups) "Go down until your elbows bend past 90°." else "Squat down until your thighs are about level."
                else -> if (pushups) "Arms straight to start." else "Stand tall to start."
            }
        )
        if (c.reps >= target) done("${target} ${c.exercise.label}. Good morning.")
    }

    /** Push-ups only count with the body roughly flat, so bending your arms while standing doesn't. */
    private fun lyingDown(pose: Pose): Boolean {
        val s = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return true
        val h = pose.getPoseLandmark(PoseLandmark.LEFT_HIP) ?: pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) ?: return true
        if (h.inFrameLikelihood < 0.5f) return true
        val dx = abs(h.position.x - s.position.x).toDouble()
        val dy = abs(h.position.y - s.position.y).toDouble()
        return Math.toDegrees(atan2(dy, dx)) < 50.0
    }

    private fun status(text: String) {
        statusView?.let { if (it.text != text) it.text = text }
    }

    private fun stopCamera() {
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        cameraProvider = null
        detector?.close()
        detector = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
        counter = null
    }

    // ── sums ──

    private fun showMath(needed: Int, note: String? = null) {
        stopCamera()
        header()
        var solved = 0
        var answer = 0

        val card = Kit.card(this, background = R.drawable.bg_card_next)
        val count = Kit.num(this, "0 / $needed").apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f) }
        card.addView(count)
        card.addView(Kit.label(this, "sums", bottom = 6))
        note?.let { card.addView(Kit.sub(this, it).apply { setPadding(0, 0, 0, Kit.dp(this@AlarmActivity, 6)) }) }
        val question = Kit.title(this, "", bottom = 8)
        card.addView(question)
        val input = Kit.input(this, "Answer", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED).apply {
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        card.addView(input)
        val feedback = Kit.sub(this, "")
        card.addView(feedback)
        content.addView(card)

        fun next() {
            val r = Random(System.nanoTime())
            if (r.nextBoolean()) {
                val x = r.nextInt(13, 50); val y = r.nextInt(13, 50)
                question.text = "$x + $y"; answer = x + y
            } else {
                val x = r.nextInt(4, 13); val y = r.nextInt(6, 16)
                question.text = "$x × $y"; answer = x * y
            }
            input.setText("")
        }

        fun check() {
            val given = input.text.toString().trim().toIntOrNull() ?: return
            if (given == answer) {
                solved++
                count.text = "$solved / $needed"
                feedback.text = ""
                if (solved >= needed) { done("Sums done. Good morning."); return }
            } else {
                feedback.text = "Not quite. Here's another."
                feedback.setTextColor(Kit.RED_TEXT)
            }
            next()
        }

        input.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { check(); true } else false }
        content.addView(Kit.primary(this, "Check") { check() })
        if (preview) content.addView(Kit.quiet(this, "Close practice") { finish() })
        next()
        input.requestFocus()
    }

    // ── finish ──

    private fun done(message: String) {
        if (finished) return
        finished = true
        stopCamera()
        AlarmService.stop(this)
        if (!preview && alarm != null) {
            getSharedPreferences("forge_alarms", Context.MODE_PRIVATE).edit()
                .putString("done_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    SimpleDateFormat("h:mm a", Locale.US).format(Date()) + "|" + (alarm?.challengeText ?: ""))
                .apply()
        }
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        content.addView(Kit.title(this, message, bottom = 8).apply { gravity = Gravity.CENTER })
        content.addView(Kit.sub(this, if (preview) "That's how it works when the alarm rings." else "Alarm off.").apply { gravity = Gravity.CENTER })
        content.postDelayed({ if (!isFinishing) finish() }, 1800)
    }

    private fun buzz() {
        try {
            @Suppress("DEPRECATION")
            content.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopCamera()
        super.onDestroy()
    }
}
