package com.forge.app.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

/** Taking and reading photos for Fuel and the physique check. Works from dialogs too. */
object Photos {

    private val seq = AtomicInteger()

    private fun <I, O> once(activity: ComponentActivity, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, done: (O) -> Unit) {
        var launcher: ActivityResultLauncher<I>? = null
        launcher = activity.activityResultRegistry.register("forge_photo_${seq.incrementAndGet()}", contract) { out ->
            launcher?.unregister()
            done(out)
        }
        launcher.launch(input)
    }

    /** The activity behind a context (a dialog's context is usually a wrapper). */
    fun activityOf(c: Context): Activity? {
        var x: Context? = c
        while (x is android.content.ContextWrapper) { if (x is Activity) return x; x = x.baseContext }
        return null
    }

    /** Asks for the camera if Forge doesn't have it yet. */
    fun askCamera(activity: Activity, done: (Boolean) -> Unit) {
        val a = activity as? ComponentActivity ?: return done(false)
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) done(true)
        else once(a, ActivityResultContracts.RequestPermission(), Manifest.permission.CAMERA) { done(it) }
    }

    /** Camera → a JPEG in [into]. Asks for the camera first if Forge doesn't have it yet. */
    fun take(activity: Activity, into: File, done: (Boolean) -> Unit) {
        val a = activity as? ComponentActivity ?: return done(false)
        val go = {
            into.parentFile?.mkdirs()
            val uri = FileProvider.getUriForFile(a, "${a.packageName}.files", into)
            try {
                once(a, ActivityResultContracts.TakePicture(), uri) { ok -> done(ok && into.exists() && into.length() > 0) }
            } catch (_: Exception) {
                Toast.makeText(a, "No camera app found", Toast.LENGTH_SHORT).show()
                done(false)
            }
        }
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) go()
        else once(a, ActivityResultContracts.RequestPermission(), Manifest.permission.CAMERA) { granted ->
            if (granted) go() else { Toast.makeText(a, "Forge needs the camera for this", Toast.LENGTH_SHORT).show(); done(false) }
        }
    }

    /** Photo picker (no permission needed) → copied into [into]. */
    fun pick(activity: Activity, into: File, done: (Boolean) -> Unit) {
        val a = activity as? ComponentActivity ?: return done(false)
        once(a, ActivityResultContracts.PickVisualMedia(), PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) { uri: Uri? ->
            if (uri == null) return@once done(false)
            try {
                into.parentFile?.mkdirs()
                a.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(into).use { input.copyTo(it) } }
                done(into.length() > 0)
            } catch (_: Exception) {
                done(false)
            }
        }
    }

    /** Decodes a photo no bigger than [maxPx] on its long side, the right way up. */
    fun load(file: File, maxPx: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = try {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) { 0f }
        val scale = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        if (rotation != 0f) m.postRotate(rotation)
        return if (m.isIdentity) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** JPEG bytes small enough to send to Gemini. */
    fun jpeg(file: File, maxPx: Int = 1024, quality: Int = 82): ByteArray? {
        val bmp = load(file, maxPx) ?: return null
        return ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray() }
    }

    fun dir(context: Context, name: String) = File(context.filesDir, name).apply { mkdirs() }
}
