package com.forge.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forge.app.data.ForgeDatabase
import com.forge.app.gym.GymDatabase
import com.forge.app.gym.GymSet
import com.forge.app.gym.Workout
import com.forge.app.sync.Account
import com.forge.app.sync.SyncEngine
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Sync against the Firebase emulators running on the computer that runs the Android
 * emulator (10.0.2.2). Skipped when they aren't running.
 */
@RunWith(AndroidJUnit4::class)
class SyncTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val day = "2030-01-15"
    private lateinit var fs: FirebaseFirestore
    private lateinit var uid: String

    private fun reachable(port: Int) = try {
        Socket().use { it.connect(InetSocketAddress("10.0.2.2", port), 1500) }; true
    } catch (_: Exception) { false }

    @Before
    fun setUp() = runBlocking {
        assumeTrue("Firebase emulators not running", reachable(8080) && reachable(9099))
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "forge-test" } ?: FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder().setProjectId("demo-forge").setApiKey("fake-key").setApplicationId("1:1:android:1").build(),
            "forge-test"
        )
        fs = FirebaseFirestore.getInstance(app)
        try { fs.useEmulator("10.0.2.2", 8080) } catch (_: IllegalStateException) {}
        val auth = FirebaseAuth.getInstance(app)
        try { auth.useEmulator("10.0.2.2", 9099) } catch (_: IllegalStateException) {}
        Account.testApp = app
        // The Auth emulator accepts an unsigned Google token
        val token = """{"sub":"sync-tester","email":"tester@example.com","email_verified":true}"""
        uid = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user!!.uid
        clearLocal()
    }

    @After
    fun tearDown() {
        if (!::fs.isInitialized) return
        clearLocal()
        Account.testApp = null
    }

    private fun forge() = ForgeDatabase.getDatabase(context).openHelper.writableDatabase
    private fun gym() = GymDatabase.get(context).openHelper.writableDatabase

    private fun clearLocal() {
        forge().delete("labors", "date = ?", arrayOf(day))
        forge().delete("xp_events", "date = ?", arrayOf(day))
        forge().delete("daily_stats", "date = ?", arrayOf(day))
        gym().delete("gym_sets", "exercise LIKE ?", arrayOf("Sync test%"))
        gym().delete("workouts", "name LIKE ?", arrayOf("Sync test%"))
    }

    private fun addLabor(name: String, done: Boolean, xp: Int): Long =
        forge().insert("labors", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
            put("name", name); put("emoji", "x"); put("description", ""); put("time", "09:00"); put("timeOfDay", "MORNING")
            put("xpReward", xp); put("done", if (done) 1 else 0); put("date", day); put("sortOrder", 0)
        })

    private fun labors(): Map<String, Boolean> {
        val out = HashMap<String, Boolean>()
        forge().query("SELECT name, done FROM labors WHERE date = ?", arrayOf<Any?>(day)).use { c ->
            while (c.moveToNext()) {
                assertNull("labor ${c.getString(0)} doubled", out.put(c.getString(0), c.getInt(1) == 1))
            }
        }
        return out
    }

    private fun long(sql: String, vararg args: Any?): Long =
        forge().query(sql, args).use { if (it.moveToFirst()) it.getLong(0) else -1 }

    private fun col(name: String) = fs.collection("users").document(uid).collection(name)

    @Test
    fun roundTrip() = runBlocking {
        // ── this device makes some things and syncs ──
        addLabor("Sync run", done = false, xp = 20)
        val dao = GymDatabase.get(context).gymDao()
        val w = dao.insertWorkout(Workout(name = "Sync test workout", date = day, startedAt = 1000, endedAt = 9000))
        dao.insertSet(GymSet(workoutId = w, exercise = "Sync test press", weightKg = 50.0, reps = 5))
        dao.insertSet(GymSet(workoutId = w, exercise = "Sync test press", weightKg = 52.5, reps = 5))
        val first = SyncEngine.sync(context)
        assertTrue("nothing was sent", first.pushed >= 4)

        val runDoc = col("labors").whereEqualTo("v.name", "Sync run").get(Source.SERVER).await().documents.single()
        assertEquals(SyncEngine.naturalId("labors", mapOf("date" to day, "name" to "Sync run")), runDoc.id)
        val wDoc = col("workouts").whereEqualTo("v.name", "Sync test workout").get(Source.SERVER).await().documents.single()
        assertEquals(2, col("gym_sets").whereEqualTo("v.workoutId", wDoc.id).get(Source.SERVER).await().size())

        // ── another device: ticks "Sync run", adds a labor, a reflection XP event and a workout ──
        val later = System.currentTimeMillis() + 1000
        fun doc(v: Map<String, Any?>) = mapOf("v" to v, "u" to later, "d" to false, "dev" to "other")
        @Suppress("UNCHECKED_CAST")
        val runV = runDoc.get("v") as Map<String, Any?>
        col("labors").document(runDoc.id).set(doc(runV + ("done" to 1L))).await()
        col("labors").document("other-read").set(doc(mapOf("name" to "Sync read", "emoji" to "x", "description" to "", "time" to "10:00",
            "timeOfDay" to "MORNING", "xpReward" to 10L, "done" to 1L, "date" to day, "sortOrder" to 1L))).await()
        col("xp_events").document("other-xp").set(doc(mapOf("amount" to 5L, "source" to "reflection", "description" to "Daily reflection",
            "timestamp" to later, "date" to day))).await()
        col("workouts").document("other-workout").set(doc(mapOf("name" to "Sync test other", "date" to day, "startedAt" to 2000L,
            "endedAt" to 8000L, "routineId" to null))).await()
        col("gym_sets").document("other-set").set(doc(mapOf("workoutId" to "other-workout", "exercise" to "Sync test row", "bodyPart" to "",
            "weightKg" to 40.0, "reps" to 10L, "timestamp" to 3000L, "setType" to "normal", "durationSec" to 0L, "done" to 1L, "position" to 0L))).await()
        // Both devices made "Sync stretch" for the day on their own; the other one ticked it
        val stretchId = SyncEngine.naturalId("labors", mapOf("date" to day, "name" to "Sync stretch"))!!
        col("labors").document(stretchId).set(doc(mapOf("name" to "Sync stretch", "emoji" to "x", "description" to "", "time" to "11:00",
            "timeOfDay" to "MORNING", "xpReward" to 15L, "done" to 1L, "date" to day, "sortOrder" to 2L))).await()
        addLabor("Sync stretch", done = false, xp = 15)

        SyncEngine.sync(context)

        val l = labors()
        assertEquals(mapOf("Sync run" to true, "Sync read" to true, "Sync stretch" to true), l)
        // Labor XP worked out here from the ticked labors: 20 + 10 + 15; plus the synced reflection XP
        assertEquals(45L, long("SELECT COALESCE(SUM(amount),0) FROM xp_events WHERE date = ? AND source = 'labor'", day))
        assertEquals(50L, long("SELECT COALESCE(SUM(amount),0) FROM xp_events WHERE date = ?", day))
        assertEquals(50L, long("SELECT totalXp FROM daily_stats WHERE date = ?", day))
        assertEquals(3L, long("SELECT laborsCompleted FROM daily_stats WHERE date = ?", day))
        // The other device's workout arrived with its set attached to it
        val otherId = gym().query("SELECT id FROM workouts WHERE name = ?", arrayOf<Any?>("Sync test other")).use { if (it.moveToFirst()) it.getLong(0) else null }
        assertNotNull(otherId)
        val sets = dao.setsForNow(otherId!!)
        assertEquals(listOf("Sync test row"), sets.map { it.exercise })

        // A second sync changes nothing
        val again = SyncEngine.sync(context)
        assertEquals(0, again.pulled)

        // ── deletes travel both ways ──
        forge().delete("labors", "date = ? AND name = ?", arrayOf(day, "Sync read"))
        SyncEngine.sync(context)
        assertEquals(true, col("labors").document("other-read").get(Source.SERVER).await().getBoolean("d"))

        col("labors").document(runDoc.id).set(mapOf("v" to emptyMap<String, Any?>(), "u" to System.currentTimeMillis() + 2000, "d" to true, "dev" to "other")).await()
        SyncEngine.sync(context)
        assertEquals(setOf("Sync stretch"), labors().keys)
    }
}
