package com.forge.app.gym

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Start and end photos for Free Exercise DB entries, loaded from its GitHub repository
 * and kept on the phone after the first look. Shown as a two-frame animation.
 */
object ExercisePhotos {

    private const val BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    @Volatile
    private var client: OkHttpClient? = null

    private fun client(context: Context): OkHttpClient = client ?: synchronized(this) {
        client ?: OkHttpClient.Builder()
            .cache(Cache(File(context.applicationContext.cacheDir, "exercise_photos"), 120L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build().also { client = it }
    }

    fun url(imageId: String, frame: Int) = "$BASE${imageId}/$frame.jpg"

    /** Loads one frame (from memory, disk or the web) and hands it back on the main thread. */
    fun get(context: Context, imageId: String, frame: Int, done: (Bitmap?) -> Unit) {
        val key = "$imageId/$frame"
        memory.get(key)?.let { done(it); return }
        val app = context.applicationContext
        pool.execute {
            val bmp = try {
                client(app).newCall(Request.Builder().url(url(imageId, frame)).build()).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.bytes()?.let { bytes ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (bmp != null) memory.put(key, bmp)
            main.post { done(bmp) }
        }
    }

    /** Shows frame 0, then flips between the two frames while the view is on screen. */
    fun animate(view: ImageView, imageId: String, onMissing: () -> Unit = {}) {
        if (imageId.isEmpty()) { onMissing(); return }
        val frames = arrayOfNulls<Bitmap>(2)
        var shown = 0
        val flip = object : Runnable {
            override fun run() {
                if (!view.isAttachedToWindow) return
                val next = (shown + 1) % 2
                frames[next]?.let { view.setImageBitmap(it); shown = next }
                main.postDelayed(this, 900)
            }
        }
        view.tag = imageId
        for (f in 0..1) {
            get(view.context, imageId, f) { bmp ->
                if (view.tag != imageId) return@get
                if (bmp == null) { if (f == 0) onMissing(); return@get }
                frames[f] = bmp
                if (f == 0) view.setImageBitmap(bmp)
                if (frames[0] != null && frames[1] != null) {
                    main.removeCallbacks(flip)
                    main.postDelayed(flip, 900)
                }
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { main.removeCallbacks(flip) }
        })
    }
}
