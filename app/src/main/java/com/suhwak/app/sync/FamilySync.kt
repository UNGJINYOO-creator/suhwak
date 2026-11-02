package com.suhwak.app.sync

import android.content.Context
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.DefaultDecks
import com.suhwak.app.data.Deck
import com.suhwak.app.data.Word
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 가족 코드 sync between the child's phone and tablet through Firestore.
 *
 * Words and decks are matched across devices by a key made from their text (local ids differ), and only
 * what changed since the last sync is sent. When both sides changed a word, the one answered more often wins.
 * Game and stats preferences are merged key by key (bigger numbers, either flag, both sets); in settings
 * each key goes to the device that changed it since the last sync.
 */
object FamilySync {
    private val lock = Mutex()
    private val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    /**
     * Preference files that follow the child; "study" is the settings (텔레그램 too), "alerts" and "digest"
     * keep the parents' messages from going out twice. Device-only files (deck sort, seeding) stay out.
     */
    private val PREF_FILES = listOf("game", "giant", "fishing", "daily", "history", "wrongNote", "masterCheck", "study", "alerts", "digest", "guides", "rewards")
    /** Files merged key by key against the last synced copy: whichever device changed a key wins it. */
    private val BY_CHANGE = setOf("study", "alerts", "guides", "rewards")

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun code(context: Context): String? = prefs(context).getString("code", null)
    fun lastSync(context: Context): Long = prefs(context).getLong("lastSync", 0)
    fun lastError(context: Context): String? = prefs(context).getString("error", null)

    fun newCode(): String = (1..10).map { ALPHABET.random() }.joinToString("")

    fun shown(code: String) = code.chunked(5).joinToString("-")

    /** Joins a family; everything is compared fresh, so this device's progress and the family's are merged. */
    fun connect(context: Context, code: String) {
        prefs(context).edit().clear().putString("code", code.uppercase().filter { it.isLetterOrDigit() }).apply()
        hashFile(context).delete()
    }

    fun disconnect(context: Context) {
        prefs(context).edit().clear().apply()
        hashFile(context).delete()
    }

    private fun hashFile(context: Context) = File(context.filesDir, "sync_hashes.json")

    private fun sha(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Runs one sync if the device is in a family; returns an error text, or null when it went fine (or is off). */
    suspend fun run(context: Context): String? {
        if (!FirebaseConfig.ready) return null
        val code = code(context) ?: return null
        android.util.Log.d("WBSync", "waiting")
        return lock.withLock {
            android.util.Log.d("WBSync", "start")
            val error = runCatching { syncOnce(context, code) }.exceptionOrNull()?.let { it.message ?: it.toString() }
            android.util.Log.d("WBSync", "end ${error ?: "ok"}")
            prefs(context).edit().apply {
                if (error == null) putLong("lastSync", System.currentTimeMillis()).remove("error") else putString("error", error)
            }.apply()
            error
        }
    }

    private suspend fun syncOnce(context: Context, code: String) {
        val store = Firestore(context)
        val dao = AppDatabase.get(context).wordDao()
        val p = prefs(context)
        val since = (p.getLong("lastPull", 0) - 10 * 60_000).coerceAtLeast(0)
        val startedAt = System.currentTimeMillis()
        val hashes = runCatching { JSONObject(hashFile(context).readText()) }.getOrDefault(JSONObject())
        fun stored(k: String): String? = hashes.optString(k, "").ifEmpty { null }

        val builtIn = DefaultDecks.wordOrder(context).toSet()
        var decks = dao.getAllDecks()
        var words = dao.getAllWords()
        fun deckKey(d: Deck) = "d" + sha("deck|" + d.name)
        fun wordKey(w: Word, byId: Map<Long, Deck>): String {
            val t = w.text.trim().lowercase()
            return "w" + sha(if (w.addedAt == 0L && t in builtIn) "builtin|$t" else "deck|${byId[w.deckId]?.name}|$t")
        }
        fun deckHash(d: Deck) = sha(listOf(d.name, d.emoji, d.color, d.isCollection, decks.firstOrNull { it.id == d.collectionId }?.name).joinToString("|"))
        fun wordFields(w: Word, deckName: String?) = mapOf(
            "deck" to deckName, "text" to w.text, "meaning" to w.meaning, "pos" to w.pos, "example" to w.example,
            "known" to w.known, "mastered" to w.mastered, "correctCount" to w.correctCount, "box" to w.box,
            "dueAt" to w.dueAt, "wrongCount" to w.wrongCount, "intervalMs" to w.intervalMs, "excluded" to w.excluded,
            "flagged" to w.flagged, "addedAt" to w.addedAt,
        ) + if (w.bookmarked) mapOf("bookmarked" to true) else emptyMap() // only when set, so older words keep their hash
        fun wordHash(w: Word, byId: Map<Long, Deck>) = sha(wordFields(w, byId[w.deckId]?.name).values.joinToString("|"))

        // 1. Pull what the other devices changed.
        val remoteDecks = store.changed(code, "decks", since)
        val remoteWords = store.changed(code, "words", since)
        val remotePrefs = store.changed(code, "prefs", since)
        var maxSeen = p.getLong("lastPull", 0)
        (remoteDecks.values + remoteWords.values + remotePrefs.values).forEach { maxSeen = maxOf(maxSeen, (it["updatedAt"] as? Long) ?: 0) }

        // Decks first, so incoming words have somewhere to go.
        remoteDecks.forEach { (key, r) ->
            val name = r["name"] as? String ?: return@forEach
            val local = decks.firstOrNull { deckKey(it) == key }
            if (r["deleted"] == true) {
                if (local != null && stored(key) == deckHash(local)) dao.deleteDeck(local.id)
                hashes.remove(key)
            } else if (local == null) {
                val id = dao.insertDeck(Deck(name = name, emoji = r["emoji"] as? String ?: "", color = ((r["color"] as? Long) ?: 0).toInt(), isCollection = r["isCollection"] == true))
                decks = decks + Deck(id = id, name = name)
            }
        }
        decks = dao.getAllDecks()
        // Link decks into their 종합단어장 by name.
        remoteDecks.values.forEach { r ->
            val parent = r["collection"] as? String ?: return@forEach
            val d = decks.firstOrNull { it.name == r["name"] } ?: return@forEach
            val pid = decks.firstOrNull { it.name == parent && it.isCollection }?.id ?: return@forEach
            if (d.collectionId != pid) dao.setCollection(d.id, pid)
        }
        decks = dao.getAllDecks()
        var byId = decks.associateBy { it.id }
        decks.forEach { d -> if (deckKey(d) in remoteDecks) hashes.put(deckKey(d), deckHash(d)) }

        val localByKey = words.associateBy { wordKey(it, byId) }
        remoteWords.forEach { (key, r) ->
            val local = localByKey[key]
            val localChanged = local != null && stored(key) != wordHash(local, byId)
            if (r["deleted"] == true) {
                if (local != null && !localChanged) dao.deleteWord(local)
                hashes.remove(key)
                return@forEach
            }
            fun long(k: String) = (r[k] as? Long) ?: 0L
            fun from(base: Word) = base.copy(
                text = r["text"] as? String ?: base.text, meaning = r["meaning"] as? String ?: base.meaning,
                pos = r["pos"] as? String ?: base.pos, example = r["example"] as? String ?: base.example,
                known = r["known"] == true, mastered = r["mastered"] == true, correctCount = long("correctCount").toInt(),
                box = long("box").toInt(), dueAt = long("dueAt"), wrongCount = long("wrongCount").toInt(),
                intervalMs = long("intervalMs"), excluded = r["excluded"] == true, flagged = r["flagged"] == true,
                addedAt = long("addedAt"), bookmarked = r["bookmarked"] == true,
            )
            if (local == null) {
                if (stored(key) != null) return@forEach // deleted here; the deletion goes up below
                val deck = decks.firstOrNull { it.name == r["deck"] } ?: return@forEach
                val id = dao.insertWord(from(Word(deckId = deck.id, text = "", meaning = "")))
                dao.getWord(id)?.let { hashes.put(key, wordHash(it, byId)) }
            } else {
                // Both changed: the side that has been answered more often knows more.
                val remoteAnswers = long("correctCount") + long("wrongCount")
                val localAnswers = local.correctCount + local.wrongCount
                if (!localChanged || remoteAnswers > localAnswers) {
                    val merged = from(local)
                    val h = wordHash(merged, byId)
                    if (h != wordHash(local, byId)) dao.updateWord(merged)
                    hashes.put(key, h)
                }
            }
        }

        // 2. Push what changed here.
        decks = dao.getAllDecks()
        byId = decks.associateBy { it.id }
        words = dao.getAllWords()
        val now = System.currentTimeMillis()
        val out = mutableListOf<Triple<String, String, Map<String, Any?>>>()
        val deckKeys = mutableSetOf<String>()
        decks.forEach { d ->
            val key = deckKey(d)
            deckKeys += key
            val h = deckHash(d)
            if (stored(key) != h) {
                out += Triple("decks", key, mapOf("name" to d.name, "emoji" to d.emoji, "color" to d.color, "isCollection" to d.isCollection,
                    "collection" to byId[d.collectionId]?.name, "deleted" to false, "updatedAt" to now))
                hashes.put(key, h)
            }
        }
        val wordKeys = mutableSetOf<String>()
        words.forEach { w ->
            val key = wordKey(w, byId)
            wordKeys += key
            val h = wordHash(w, byId)
            // Built-in words nobody has touched yet are the same on every device: not sent until studied,
            // so a new family or a newly joined device doesn't write all 1800 of them.
            val untouched = w.addedAt == 0L && w.text.trim().lowercase() in builtIn && w.box == 0 && !w.known && !w.mastered &&
                w.correctCount == 0 && w.wrongCount == 0 && !w.excluded && !w.flagged && !w.bookmarked
            if (untouched && stored(key) == null) {
                hashes.put(key, h)
            } else if (stored(key) != h) {
                out += Triple("words", key, wordFields(w, byId[w.deckId]?.name) + mapOf("deleted" to false, "updatedAt" to now))
                hashes.put(key, h)
            }
        }
        // Keys we had synced that are gone here were deleted on this device.
        hashes.keys().asSequence().toList().forEach { key ->
            if (key.startsWith("w") && key !in wordKeys) {
                out += Triple("words", key, mapOf("deleted" to true, "updatedAt" to now)); hashes.remove(key)
            } else if (key.startsWith("d") && key !in deckKeys) {
                out += Triple("decks", key, mapOf("deleted" to true, "updatedAt" to now)); hashes.remove(key)
            }
        }

        // Preferences, with word ids turned into keys so they mean the same word on every device.
        val keyOf = words.associate { it.id to wordKey(it, byId) }
        val idOf = keyOf.entries.associate { (id, k) -> k to id }
        PREF_FILES.forEach { file ->
            val local = PrefCodec.export(context, file, keyOf)
            val localHash = sha(local.toString())
            val hk = "p$file"
            val remote = remotePrefs[file]?.get("data")?.let { runCatching { JSONObject(it as String) }.getOrNull() }
            var result = local
            if (remote != null) {
                val localChanged = stored(hk) != localHash
                result = when {
                    !localChanged -> remote
                    file in BY_CHANGE -> PrefCodec.byChange(hashes.optJSONObject("b$file"), local, remote)
                    else -> PrefCodec.merge(local, remote)
                }
                if (result.toString() != local.toString()) PrefCodec.import(context, file, result, idOf)
            }
            val h = sha(result.toString())
            if (stored(hk) != h || result.toString() != local.toString()) {
                out += Triple("prefs", file, mapOf("data" to result.toString(), "updatedAt" to now))
            }
            hashes.put(hk, h)
            if (file in BY_CHANGE) hashes.put("b$file", JSONObject(result.toString()))
        }

        store.write(code, out)
        hashFile(context).writeText(hashes.toString())
        p.edit().putLong("lastPull", maxOf(maxSeen, if (out.isEmpty()) maxSeen else now).coerceAtMost(startedAt + 60_000)).apply()
    }
}

/** SharedPreferences ⇄ JSON for the sync, translating the word-id lists into word keys and back. */
object PrefCodec {
    private val DAY = Regex("\\d{4}-\\d{2}-\\d{2}")

    /** Keys whose value is a set of word ids. */
    private val ID_SETS = setOf("giant/confirmed", "masterCheck/ids", "fishing/seen", "history/ever", "history/proven")

    fun export(context: Context, file: String, keyOf: Map<Long, String>): JSONObject {
        val sp = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)
        val out = JSONObject()
        sp.all.toSortedMap().forEach { (k, v) ->
            val tv = when {
                "$file/$k" in ID_SETS && v is Set<*> ->
                    JSONArray().put("set").put(JSONArray(v.mapNotNull { (it as String).toLongOrNull()?.let(keyOf::get) }.sorted()))
                file == "daily" && k == "learned" && v is String ->
                    JSONArray().put("s").put(v.split(',').mapNotNull { it.toLongOrNull()?.let(keyOf::get) }.sorted().joinToString(","))
                file == "wrongNote" && k == "words" && v is String ->
                    JSONArray().put("s").put(v.split(',').filter { it.isNotBlank() }.mapNotNull { e ->
                        val parts = e.split(':')
                        parts[0].toLongOrNull()?.let(keyOf::get)?.let { (listOf(it) + parts.drop(1)).joinToString(":") }
                    }.sorted().joinToString(","))
                v is Boolean -> JSONArray().put("b").put(v)
                v is Int -> JSONArray().put("i").put(v)
                v is Long -> JSONArray().put("l").put(v)
                v is Float -> JSONArray().put("f").put(v.toDouble())
                v is String -> JSONArray().put("s").put(v)
                v is Set<*> -> JSONArray().put("set").put(JSONArray(v.map { it.toString() }.sorted()))
                else -> null
            }
            tv?.let { out.put(k, it) }
        }
        return out
    }

    fun import(context: Context, file: String, data: JSONObject, idOf: Map<String, Long>) {
        val e = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear()
        data.keys().forEach { k ->
            val tv = data.getJSONArray(k)
            when (tv.getString(0)) {
                "b" -> e.putBoolean(k, tv.getBoolean(1))
                "i" -> e.putInt(k, tv.getInt(1))
                "l" -> e.putLong(k, tv.getLong(1))
                "f" -> e.putFloat(k, tv.getDouble(1).toFloat())
                "s" -> {
                    var s = tv.getString(1)
                    if (file == "daily" && k == "learned") s = s.split(',').mapNotNull { idOf[it]?.toString() }.joinToString(",")
                    if (file == "wrongNote" && k == "words") s = s.split(',').filter { it.isNotBlank() }.mapNotNull { en ->
                        val parts = en.split(':')
                        idOf[parts[0]]?.let { (listOf(it.toString()) + parts.drop(1)).joinToString(":") }
                    }.joinToString(",")
                    e.putString(k, s)
                }
                "set" -> {
                    val a = tv.getJSONArray(1)
                    val items = (0 until a.length()).map { a.getString(it) }
                    e.putStringSet(k, if ("$file/$k" in ID_SETS) items.mapNotNull { idOf[it]?.toString() }.toSet() else items.toSet())
                }
            }
        }
        e.apply()
    }

    /**
     * Three-way: the other device's copy, with every key this device changed since [base] (the last synced
     * copy) put on top. Without a base, keys this device has and the other doesn't are kept, and an
     * empty text never replaces a filled one (a 텔레그램 token set on the other device survives).
     */
    fun byChange(base: JSONObject?, local: JSONObject, remote: JSONObject): JSONObject {
        val out = JSONObject(remote.toString())
        local.keys().forEach { k ->
            val l = local.getJSONArray(k)
            val r = remote.optJSONArray(k)
            val changedHere = if (base != null) base.optJSONArray(k)?.toString() != l.toString()
                else r == null || !(l.optString(0) == "s" && l.optString(1).isEmpty())
            if (changedHere) out.put(k, l)
        }
        // Keys this device removed (and the other left alone) stay removed.
        base?.keys()?.forEach { k ->
            if (!local.has(k) && remote.optJSONArray(k)?.toString() == base.getJSONArray(k).toString()) out.remove(k)
        }
        return out
    }

    /** Coupons from both devices, one per claim time; used on either device = used. */
    fun mergeCoupons(a: String, b: String): String {
        val all = (runCatching { JSONArray(a) }.getOrNull()?.let { x -> (0 until x.length()).map { x.getJSONObject(it) } }.orEmpty() +
            runCatching { JSONArray(b) }.getOrNull()?.let { x -> (0 until x.length()).map { x.getJSONObject(it) } }.orEmpty())
        val merged = all.groupBy { it.optLong("at") to it.optString("name") }.values.map { same ->
            JSONObject(same.first().toString()).put("used", same.any { it.optBoolean("used") })
        }.sortedByDescending { it.optLong("at") }
        return JSONArray(merged).toString()
    }

    /** Key by key: a newer "date" or "resetAt" wins the whole file; otherwise bigger numbers, either flag, both sets. */
    fun merge(local: JSONObject, remote: JSONObject): JSONObject {
        val ld = local.optJSONArray("date")?.optString(1)
        val rd = remote.optJSONArray("date")?.optString(1)
        if (ld != null && rd != null && ld != rd) return if (rd > ld) remote else local
        // A 진도 초기화 (or undoing one) on either device replaces the whole file, so it isn't merged back in.
        val lr = local.optJSONArray("resetAt")?.optLong(1) ?: 0L
        val rr = remote.optJSONArray("resetAt")?.optLong(1) ?: 0L
        if (lr != rr) return if (rr > lr) remote else local
        val out = JSONObject(local.toString())
        remote.keys().forEach { k ->
            val r = remote.getJSONArray(k)
            val l = local.optJSONArray(k)
            if (l == null || l.getString(0) != r.getString(0)) { out.put(k, r); return@forEach }
            val merged = when {
                // The reward list the parent edited last wins, so a deleted or changed reward doesn't come back.
                k == "rewards" -> {
                    val lt = local.optJSONArray("rewardsAt")?.optLong(1) ?: 0L
                    val rt = remote.optJSONArray("rewardsAt")?.optLong(1) ?: 0L
                    if (rt > lt) r else l
                }
                k == "coupons" -> JSONArray().put("s").put(mergeCoupons(l.getString(1), r.getString(1)))
                else -> null
            } ?: when (r.getString(0)) {
                "b" -> JSONArray().put("b").put(l.getBoolean(1) || r.getBoolean(1))
                "i" -> JSONArray().put("i").put(maxOf(l.getInt(1), r.getInt(1)))
                "l" -> JSONArray().put("l").put(maxOf(l.getLong(1), r.getLong(1)))
                "f" -> JSONArray().put("f").put(maxOf(l.getDouble(1), r.getDouble(1)))
                "set" -> {
                    val a = l.getJSONArray(1); val b = r.getJSONArray(1)
                    JSONArray().put("set").put(JSONArray(((0 until a.length()).map { a.getString(it) } + (0 until b.length()).map { b.getString(it) }).toSortedSet().toList()))
                }
                else -> {
                    // Comma lists (learned words, 오답노트) are joined; other text keeps the longer, else ours.
                    val a = l.getString(1); val b = r.getString(1)
                    if (k == "learned") JSONArray().put("s").put((a.split(',') + b.split(',')).filter { it.isNotBlank() }.toSortedSet().joinToString(","))
                    // The evening report's collected results: both devices' games and tests.
                    else if (k.startsWith("d_")) JSONArray().put("s").put((a.split("\n\n") + b.split("\n\n")).filter { it.isNotBlank() }.distinct().joinToString("\n\n"))
                    // Dates (the report's "sent" day): the later one.
                    else if (DAY.matches(a) && DAY.matches(b)) JSONArray().put("s").put(maxOf(a, b))
                    else JSONArray().put("s").put(if (b.length > a.length) b else a)
                }
            }
            out.put(k, merged)
        }
        return out
    }
}
