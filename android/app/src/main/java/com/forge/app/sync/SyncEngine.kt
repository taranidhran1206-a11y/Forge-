package com.forge.app.sync

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.Settings
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.fuel.FuelDatabase
import com.forge.app.gym.GymDatabase
import com.forge.app.util.WidgetHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Two-way sync of Forge's tables with Firestore, under users/{uid}/{table}/{doc}.
 *
 * Each local row maps to one document. A row changed here since the last sync is pushed; a
 * document changed elsewhere is pulled, unless this device also changed that row, in which
 * case this device's edit wins and goes up next. Deleted rows leave a tombstone so the
 * delete reaches the other devices. Rows that every device creates for itself (today's
 * labors, a day's stats, a day's body weight, starter routines) use the same document id on
 * every device, so they merge instead of doubling.
 *
 * Labor XP isn't synced: each device works it out from the synced labors, so it can never
 * be counted twice.
 */
object SyncEngine {

    private const val PREFS = "forge_sync"
    private const val OVERLAP_MS = 10 * 60_000L
    private val mutex = Mutex()

    private fun truthy(v: Any?) = when (v) {
        is Boolean -> v
        is Number -> v.toLong() != 0L
        is String -> v == "1" || v.equals("true", true)
        else -> false
    }

    class Table(
        val name: String,
        val db: (Context) -> RoomDatabase,
        val key: String = "id",
        val autoKey: Boolean = true,
        /** Column → parent table, for ids that point at another synced row. */
        val fks: Map<String, String> = emptyMap(),
        val where: String? = null,
        /** Columns that make a row the "same" on every device. */
        val natural: List<String>? = null,
        /** How to combine a local row with the same row from another device the first time they meet. */
        val merge: ((local: Map<String, Any?>, remote: Map<String, Any?>) -> Map<String, Any?>)? = null
    )

    private val forge: (Context) -> RoomDatabase = { ForgeDatabase.getDatabase(it) }
    private val gym: (Context) -> RoomDatabase = { GymDatabase.get(it) }
    private val fuel: (Context) -> RoomDatabase = { FuelDatabase.get(it) }

    /** Parents before children. */
    val TABLES = listOf(
        Table("labors", forge, natural = listOf("date", "name"),
            merge = { l, r -> r + ("done" to if (truthy(l["done"]) || truthy(r["done"])) 1L else 0L) }),
        Table("xp_events", forge, where = "source != 'labor'"),
        Table("reflections", forge),
        // Only finished time entries: a running one belongs to the device it runs on.
        Table("time_tracks", forge, where = "endTime IS NOT NULL"),
        Table("coach_messages", forge),
        Table("plan_entries", forge),
        Table("daily_stats", forge, key = "date", autoKey = false, natural = listOf("date"),
            merge = { l, r ->
                r + mapOf(
                    "reflectionDone" to if (truthy(l["reflectionDone"]) || truthy(r["reflectionDone"])) 1L else 0L,
                    "auditDone" to if (truthy(l["auditDone"]) || truthy(r["auditDone"])) 1L else 0L,
                    "briefDone" to if (truthy(l["briefDone"]) || truthy(r["briefDone"])) 1L else 0L,
                    "moodScore" to (if (((r["moodScore"] as? Number)?.toLong() ?: -1L) >= 0L) r["moodScore"] else l["moodScore"])
                )
            }),
        Table("routines", gym, natural = listOf("name")),
        Table("routine_items", gym, fks = mapOf("routineId" to "routines"), natural = listOf("routineId", "exercise", "sortOrder")),
        // Only finished workouts: one in progress stays on the device it's being logged on.
        Table("workouts", gym, fks = mapOf("routineId" to "routines"), where = "endedAt IS NOT NULL"),
        Table("gym_sets", gym, fks = mapOf("workoutId" to "workouts"),
            where = "workoutId IN (SELECT id FROM workouts WHERE endedAt IS NOT NULL)"),
        Table("body_weights", gym, natural = listOf("date")),
        Table("meals", fuel)
    )

    // ── the local map of row ↔ document ──

    private class MapDb(c: Context) : SQLiteOpenHelper(c.applicationContext, "forge_sync.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE map (tbl TEXT NOT NULL, local TEXT NOT NULL, remote TEXT NOT NULL, hash TEXT NOT NULL, PRIMARY KEY (tbl, local))")
            db.execSQL("CREATE INDEX map_remote ON map (tbl, remote)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    @Volatile private var mapDb: MapDb? = null
    private fun maps(c: Context): SQLiteDatabase = (mapDb ?: synchronized(this) { mapDb ?: MapDb(c).also { mapDb = it } }).writableDatabase

    private data class Entry(val remote: String, val hash: String)

    private fun loadMap(c: Context, tbl: String): MutableMap<String, Entry> {
        val out = HashMap<String, Entry>()
        maps(c).rawQuery("SELECT local, remote, hash FROM map WHERE tbl = ?", arrayOf(tbl)).use { cur ->
            while (cur.moveToNext()) out[cur.getString(0)] = Entry(cur.getString(1), cur.getString(2))
        }
        return out
    }

    private fun putMap(c: Context, tbl: String, local: String, e: Entry) {
        maps(c).insertWithOnConflict("map", null, ContentValues().apply {
            put("tbl", tbl); put("local", local); put("remote", e.remote); put("hash", e.hash)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun dropMap(c: Context, tbl: String, local: String) {
        maps(c).delete("map", "tbl = ? AND local = ?", arrayOf(tbl, local))
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastSync(c: Context): Long = prefs(c).getLong("last_sync", 0)
    fun lastError(c: Context): String? = prefs(c).getString("last_error", null)

    /** Forget which rows were synced (after signing out), so another account starts clean. */
    fun forget(c: Context) {
        maps(c).delete("map", null, null)
        prefs(c).edit().clear().apply()
    }

    private fun deviceId(c: Context): String {
        val p = prefs(c)
        p.getString("device", null)?.let { return it }
        val id = (Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()).take(16)
        p.edit().putString("device", id).apply()
        return id
    }

    // ── reading and writing rows ──

    private fun value(cur: Cursor, i: Int): Any? = when (cur.getType(i)) {
        Cursor.FIELD_TYPE_INTEGER -> cur.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> cur.getDouble(i)
        Cursor.FIELD_TYPE_STRING -> cur.getString(i)
        else -> null
    }

    private fun readAll(db: SupportSQLiteDatabase, t: Table): LinkedHashMap<String, Map<String, Any?>> {
        val out = LinkedHashMap<String, Map<String, Any?>>()
        db.query("SELECT * FROM ${t.name}" + (t.where?.let { " WHERE $it" } ?: "")).use { cur ->
            val names = cur.columnNames
            while (cur.moveToNext()) {
                val row = LinkedHashMap<String, Any?>()
                names.forEachIndexed { i, n -> row[n] = value(cur, i) }
                out[row[t.key].toString()] = row
            }
        }
        return out
    }

    private fun readOne(db: SupportSQLiteDatabase, t: Table, key: String): Map<String, Any?>? {
        db.query("SELECT * FROM ${t.name} WHERE ${t.key} = ?", arrayOf<Any?>(key)).use { cur ->
            if (!cur.moveToFirst()) return null
            val row = LinkedHashMap<String, Any?>()
            cur.columnNames.forEachIndexed { i, n -> row[n] = value(cur, i) }
            return row
        }
    }

    private fun hash(row: Map<String, Any?>, t: Table): String {
        val s = row.entries.filter { !(t.autoKey && it.key == t.key) }.sortedBy { it.key }.joinToString("\u0001") { "${it.key}=${it.value}" }
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }.take(20)
    }

    private fun contentValues(values: Map<String, Any?>, columns: Set<String>): ContentValues = ContentValues().apply {
        values.forEach { (k, v) ->
            if (k !in columns) return@forEach
            when (v) {
                null -> putNull(k)
                is Boolean -> put(k, if (v) 1L else 0L)
                is Long -> put(k, v)
                is Int -> put(k, v.toLong())
                is Double -> put(k, v)
                is Float -> put(k, v.toDouble())
                is Number -> put(k, v.toDouble())
                else -> put(k, v.toString())
            }
        }
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val out = HashSet<String>()
        db.query("PRAGMA table_info($table)").use { cur ->
            val i = cur.getColumnIndex("name")
            while (cur.moveToNext()) out += cur.getString(i)
        }
        return out
    }

    /** The shared document id for a row every device makes for itself (tests use it too). */
    internal fun naturalId(table: String, remoteForm: Map<String, Any?>): String? =
        TABLES.firstOrNull { it.name == table }?.let { naturalId(it, remoteForm) }

    private fun naturalId(t: Table, remoteForm: Map<String, Any?>): String? {
        val cols = t.natural ?: return null
        val s = cols.joinToString("|") { (remoteForm[it] ?: "").toString().trim().lowercase() }
        val d = MessageDigest.getInstance("SHA-1").digest((t.name + "|" + s).toByteArray())
        return "n_" + d.joinToString("") { "%02x".format(it) }.take(24)
    }

    // ── sync ──

    data class Report(val pushed: Int, val pulled: Int, val deleted: Int)

    /** Runs one full sync. Safe to call often; only one runs at a time. */
    suspend fun sync(c: Context): Report = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = c.applicationContext
            val user = Account.user(app) ?: throw IllegalStateException("Not signed in")
            val fs = Account.firestore(app) ?: throw IllegalStateException("Sync isn't set up")
            // Another account on this device: what was synced before belongs to the old one.
            val lastUid = prefs(app).getString("uid", null)
            if (lastUid != null && lastUid != user.uid) forget(app)
            prefs(app).edit().putString("uid", user.uid).apply()
            val root = fs.collection("users").document(user.uid)
            val device = deviceId(app)
            var pushed = 0
            var pulled = 0
            var deleted = 0
            val touched = HashSet<RoomDatabase>()
            /** remote id → local key, per table, for translating parent ids. */
            val reverse = HashMap<String, MutableMap<String, String>>()
            val forward = HashMap<String, MutableMap<String, String>>()

            try {
                for (t in TABLES) {
                    val room = t.db(app)
                    val db = room.openHelper.writableDatabase
                    val cols = columns(db, t.name)
                    val map = loadMap(app, t.name)
                    val rev = HashMap<String, String>().also { r -> map.forEach { (l, e) -> r[e.remote] = l } }
                    reverse[t.name] = rev
                    forward[t.name] = HashMap<String, String>().also { f -> map.forEach { (l, e) -> f[l] = e.remote } }
                    var rows = readAll(db, t)

                    fun pending(local: String): Boolean {
                        val row = rows[local] ?: return false
                        val e = map[local] ?: return true
                        return e.hash != hash(row, t)
                    }

                    fun toRemote(row: Map<String, Any?>): Map<String, Any?> {
                        val v = LinkedHashMap<String, Any?>()
                        row.forEach { (k, x) ->
                            if (t.autoKey && k == t.key) return@forEach
                            val parent = t.fks[k]
                            v[k] = if (parent != null && x != null) forward[parent]?.get(x.toString()) else x
                        }
                        return v
                    }

                    // Unmapped local rows by natural id, for meeting the same row from another device.
                    val naturalIndex = HashMap<String, String>()
                    if (t.natural != null) rows.forEach { (k, row) -> if (map[k] == null) naturalId(t, toRemote(row))?.let { naturalIndex.putIfAbsent(it, k) } }

                    // ── pull ──
                    val since = prefs(app).getLong("pull_${t.name}", 0L)
                    val q = root.collection(t.name).whereGreaterThanOrEqualTo("u", maxOf(0L, since - OVERLAP_MS))
                    val docs = q.get(Source.SERVER).await().documents
                    var maxU = since
                    var deferred = false
                    var wrote = false
                    for (doc in docs) {
                        val u = doc.getLong("u") ?: 0L
                        val local = rev[doc.id]
                        if (doc.getBoolean("d") == true) {
                            if (local != null && !pending(local)) {
                                db.delete(t.name, "${t.key} = ?", arrayOf(local))
                                dropMap(app, t.name, local)
                                map.remove(local); rev.remove(doc.id); forward[t.name]?.remove(local)
                                deleted++; wrote = true
                            }
                            maxU = maxOf(maxU, u)
                            continue
                        }
                        @Suppress("UNCHECKED_CAST")
                        val v = (doc.get("v") as? Map<String, Any?>) ?: continue
                        // Parent ids → local ids
                        val values = LinkedHashMap<String, Any?>(v)
                        var missingParent = false
                        t.fks.forEach { (col, parent) ->
                            val rid = v[col] ?: return@forEach
                            val lid = reverse[parent]?.get(rid.toString())
                            if (lid == null) missingParent = true else values[col] = lid.toLong()
                        }
                        if (missingParent) { deferred = true; continue }

                        if (local != null) {
                            if (!pending(local)) {
                                val cur = rows[local]
                                val wanted = LinkedHashMap(values).apply { if (t.autoKey) put(t.key, local.toLong()) }
                                if (cur == null || hash(cur, t) != hash(wanted.filterKeys { it in cols }, t)) {
                                    db.update(t.name, SQLiteDatabase.CONFLICT_REPLACE, contentValues(values, cols - t.key), "${t.key} = ?", arrayOf(local))
                                    pulled++; wrote = true
                                }
                                readOne(db, t, local)?.let { putMap(app, t.name, local, Entry(doc.id, hash(it, t))); map[local] = Entry(doc.id, hash(it, t)) }
                            }
                        } else {
                            val match = naturalIndex.remove(doc.id)
                            if (match != null && rows[match] != null) {
                                val mine = rows[match]!!
                                val merged = t.merge?.invoke(mine, values) ?: values
                                db.update(t.name, SQLiteDatabase.CONFLICT_REPLACE, contentValues(merged, cols - t.key), "${t.key} = ?", arrayOf(match))
                                val after = readOne(db, t, match)
                                // If the merge kept something of ours, leave it pending so it goes up.
                                val h = if (after != null && merged == values) hash(after, t) else ""
                                putMap(app, t.name, match, Entry(doc.id, h)); map[match] = Entry(doc.id, h)
                                rev[doc.id] = match; forward[t.name]?.set(match, doc.id)
                                pulled++; wrote = true
                            } else {
                                val cv = contentValues(values, if (t.autoKey) cols - t.key else cols)
                                val rowId = db.insert(t.name, SQLiteDatabase.CONFLICT_REPLACE, cv)
                                val key = if (t.autoKey) rowId.toString() else values[t.key]?.toString()
                                if (key != null && rowId != -1L) {
                                    readOne(db, t, key)?.let { putMap(app, t.name, key, Entry(doc.id, hash(it, t))); map[key] = Entry(doc.id, hash(it, t)) }
                                    rev[doc.id] = key; forward[t.name]?.set(key, doc.id)
                                    pulled++; wrote = true
                                }
                            }
                        }
                        maxU = maxOf(maxU, u)
                    }
                    if (wrote) { touched += room; rows = readAll(db, t) }

                    // ── push ──
                    val now = System.currentTimeMillis()
                    val writes = ArrayList<Pair<String, Map<String, Any?>>>()
                    val takenRemote = HashSet(map.values.map { it.remote })
                    for ((k, row) in rows) {
                        val e = map[k]
                        val h = hash(row, t)
                        if (e != null && e.hash == h) continue
                        val remoteForm = toRemote(row)
                        // A child whose parent hasn't got a document yet waits for the next round.
                        if (t.fks.any { (col, _) -> row[col] != null && remoteForm[col] == null }) continue
                        val remote = e?.remote ?: (naturalId(t, remoteForm)?.takeIf { it !in takenRemote } ?: UUID.randomUUID().toString())
                        takenRemote += remote
                        writes += remote to mapOf("v" to remoteForm, "u" to now, "d" to false, "dev" to device)
                        putMap(app, t.name, k, Entry(remote, h)); map[k] = Entry(remote, h)
                        forward[t.name]?.set(k, remote); rev[remote] = k
                        pushed++
                    }
                    // Rows deleted here since the last sync.
                    val gone = map.keys.filter { it !in rows }
                    for (k in gone) {
                        val e = map[k] ?: continue
                        writes += e.remote to mapOf("v" to emptyMap<String, Any?>(), "u" to now, "d" to true, "dev" to device)
                        dropMap(app, t.name, k); map.remove(k); rev.remove(e.remote); forward[t.name]?.remove(k)
                        pushed++
                    }
                    commit(fs, root.collection(t.name).path, writes)

                    if (!deferred) prefs(app).edit().putLong("pull_${t.name}", maxU).apply()
                }

                // Things each device works out for itself from the synced rows.
                if (pulled > 0 || deleted > 0) {
                    derive(app)
                    touched += ForgeDatabase.getDatabase(app)
                }
                touched.forEach { try { it.invalidationTracker.refreshVersionsAsync() } catch (_: Exception) {} }

                KvSync.sync(app, root, device)

                prefs(app).edit().putLong("last_sync", System.currentTimeMillis()).remove("last_error").apply()
                if (pulled > 0 || deleted > 0) {
                    try { WidgetHelper.updateAllWidgets(app, ForgeRepository(ForgeDatabase.getDatabase(app))) } catch (_: Exception) {}
                }
                Report(pushed, pulled, deleted)
            } catch (e: Exception) {
                prefs(app).edit().putString("last_error", e.message ?: e.javaClass.simpleName).apply()
                throw e
            }
        }
    }

    private suspend fun commit(fs: FirebaseFirestore, path: String, writes: List<Pair<String, Map<String, Any?>>>) {
        writes.chunked(400).forEach { chunk ->
            val batch: WriteBatch = fs.batch()
            chunk.forEach { (id, data) -> batch.set(fs.collection(path).document(id), data) }
            batch.commit().await()
        }
    }

    /**
     * Labor XP and each day's totals come from the synced rows. Labor XP is topped up or
     * taken back per day so it always matches the ticked labors, capped like in the app.
     */
    private fun derive(c: Context) {
        val db = ForgeDatabase.getDatabase(c).openHelper.writableDatabase
        val dates = HashSet<String>()
        db.query("SELECT DISTINCT date FROM labors").use { while (it.moveToNext()) dates += it.getString(0) }
        db.query("SELECT DISTINCT date FROM xp_events").use { while (it.moveToNext()) dates += it.getString(0) }
        db.beginTransaction()
        try {
            for (d in dates) {
                var earned = 0L
                db.query("SELECT COALESCE(SUM(xpReward), 0) FROM labors WHERE date = ? AND done = 1", arrayOf<Any?>(d)).use { if (it.moveToFirst()) earned = it.getLong(0) }
                val target = minOf(earned, ForgeRepository.DAILY_LABOR_XP_CAP.toLong())
                var current = 0L
                db.query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE date = ? AND source = 'labor'", arrayOf<Any?>(d)).use { if (it.moveToFirst()) current = it.getLong(0) }
                if (target != current) {
                    db.insert("xp_events", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
                        put("amount", target - current); put("source", "labor")
                        put("description", if (target > current) "Labors completed" else "Labor unticked")
                        put("timestamp", System.currentTimeMillis()); put("date", d)
                    })
                }
                var done = 0L; var total = 0L; var xp = 0L
                db.query("SELECT COALESCE(SUM(done), 0), COUNT(*) FROM labors WHERE date = ?", arrayOf<Any?>(d)).use { if (it.moveToFirst()) { done = it.getLong(0); total = it.getLong(1) } }
                db.query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE date = ?", arrayOf<Any?>(d)).use { if (it.moveToFirst()) xp = it.getLong(0) }
                val cv = ContentValues().apply { put("laborsCompleted", done); put("laborsTotal", total); put("totalXp", xp) }
                if (db.update("daily_stats", SQLiteDatabase.CONFLICT_NONE, cv, "date = ?", arrayOf(d)) == 0) {
                    cv.put("date", d)
                    db.insert("daily_stats", SQLiteDatabase.CONFLICT_IGNORE, cv)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Small settings kept in prefs rather than tables: verdicts and Fuel goals. */
    private object KvSync {
        private const val TBL = "kv"

        private fun local(c: Context): Map<String, String> {
            val out = HashMap<String, String>()
            c.getSharedPreferences("forge_verdicts", Context.MODE_PRIVATE).all.forEach { (k, v) -> if (v is String) out["verdict_$k"] = v }
            val fuel = JSONObject()
            c.getSharedPreferences("forge_prefs", Context.MODE_PRIVATE).all.forEach { (k, v) ->
                if (k.startsWith("fuel_") && v != null) fuel.put(k, JSONObject().put("t", v.javaClass.simpleName).put("v", v.toString()))
            }
            if (fuel.length() > 0) out["fuel_goals"] = fuel.toString()
            return out
        }

        private fun apply(c: Context, id: String, s: String) {
            when {
                id.startsWith("verdict_") -> c.getSharedPreferences("forge_verdicts", Context.MODE_PRIVATE).edit().putString(id.removePrefix("verdict_"), s).apply()
                id == "fuel_goals" -> {
                    val o = JSONObject(s)
                    val e = c.getSharedPreferences("forge_prefs", Context.MODE_PRIVATE).edit()
                    o.keys().forEach { k ->
                        val x = o.getJSONObject(k)
                        val v = x.getString("v")
                        when (x.getString("t")) {
                            "Boolean" -> e.putBoolean(k, v.toBoolean())
                            "Integer" -> e.putInt(k, v.toInt())
                            "Long" -> e.putLong(k, v.toLong())
                            "Float" -> e.putFloat(k, v.toFloat())
                            else -> e.putString(k, v)
                        }
                    }
                    e.apply()
                }
            }
        }

        private fun h(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

        suspend fun sync(c: Context, root: com.google.firebase.firestore.DocumentReference, device: String) {
            val fs = root.firestore
            val map = loadMap(c, TBL)
            var mine = local(c)
            val since = prefs(c).getLong("pull_kv", 0L)
            val docs = root.collection(TBL).whereGreaterThanOrEqualTo("u", maxOf(0L, since - OVERLAP_MS)).get(Source.SERVER).await().documents
            var maxU = since
            for (doc in docs) {
                val s = doc.getString("s") ?: continue
                val pending = mine[doc.id]?.let { cur -> map[doc.id]?.hash != h(cur) } ?: false
                if (!pending && mine[doc.id] != s) apply(c, doc.id, s)
                if (!pending) { putMap(c, TBL, doc.id, Entry(doc.id, h(s))); map[doc.id] = Entry(doc.id, h(s)) }
                maxU = maxOf(maxU, doc.getLong("u") ?: 0L)
            }
            mine = local(c)
            val now = System.currentTimeMillis()
            val writes = ArrayList<Pair<String, Map<String, Any?>>>()
            mine.forEach { (id, s) ->
                if (map[id]?.hash == h(s)) return@forEach
                writes += id to mapOf("s" to s, "u" to now, "dev" to device)
                putMap(c, TBL, id, Entry(id, h(s)))
            }
            commit(fs, root.collection(TBL).path, writes)
            prefs(c).edit().putLong("pull_kv", maxU).apply()
        }
    }
}
