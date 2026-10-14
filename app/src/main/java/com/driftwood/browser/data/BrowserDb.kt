package com.driftwood.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bookmarks, history, downloads and homepage shortcuts. */
class BrowserDb(context: Context) : SQLiteOpenHelper(context, "browser.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY, title TEXT NOT NULL, url TEXT NOT NULL, folder TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY, url TEXT NOT NULL, title TEXT NOT NULL, visited INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX history_visited ON history(visited)")
        db.execSQL("CREATE TABLE downloads (id INTEGER PRIMARY KEY, name TEXT NOT NULL, uri TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE shortcuts (id INTEGER PRIMARY KEY, title TEXT NOT NULL, url TEXT NOT NULL, position INTEGER NOT NULL)")
        listOf(
            "Wikipedia" to "https://www.wikipedia.org",
            "YouTube" to "https://m.youtube.com",
            "Reddit" to "https://www.reddit.com",
            "GitHub" to "https://github.com",
        ).forEachIndexed { i, (title, url) ->
            db.insert("shortcuts", null, ContentValues().apply {
                put("title", title); put("url", url); put("position", i)
            })
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

data class Bookmark(val id: Long, val title: String, val url: String, val folder: String, val created: Long)
data class HistoryEntry(val id: Long, val url: String, val title: String, val visited: Long)
data class DownloadEntry(val id: Long, val name: String, val uri: String, val mime: String, val size: Long, val created: Long)
data class Shortcut(val id: Long, val title: String, val url: String, val position: Int)

/** Shared plumbing: every repo keeps its rows in a StateFlow the UI observes. */
abstract class Repo<T>(protected val db: BrowserDb, protected val scope: CoroutineScope) {
    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> get() = _items

    init {
        refresh()
    }

    protected abstract fun query(db: SQLiteDatabase): List<T>

    fun refresh() {
        scope.launch { _items.value = withContext(Dispatchers.IO) { query(db.readableDatabase) } }
    }

    protected fun write(block: (SQLiteDatabase) -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { block(db.writableDatabase) }
            refresh()
        }
    }

    protected suspend fun writeNow(block: (SQLiteDatabase) -> Unit) {
        withContext(Dispatchers.IO) { block(db.writableDatabase) }
        refresh()
    }
}

private inline fun <T> Cursor.mapRows(read: Cursor.() -> T): List<T> = use {
    val out = ArrayList<T>(count)
    while (moveToNext()) out += read()
    out
}

class BookmarkRepo(db: BrowserDb, scope: CoroutineScope) : Repo<Bookmark>(db, scope) {
    override fun query(db: SQLiteDatabase) =
        db.rawQuery("SELECT id, title, url, folder, created FROM bookmarks ORDER BY folder, created DESC", null)
            .mapRows { Bookmark(getLong(0), getString(1), getString(2), getString(3), getLong(4)) }

    fun isBookmarked(url: String) = items.value.any { it.url == url }

    fun add(title: String, url: String, folder: String = "") = write {
        it.insert("bookmarks", null, values(title, url, folder))
    }

    fun update(id: Long, title: String, url: String) = write {
        it.update("bookmarks", ContentValues().apply { put("title", title); put("url", url) }, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) = write { it.delete("bookmarks", "id = ?", arrayOf(id.toString())) }

    fun removeUrl(url: String) = write { it.delete("bookmarks", "url = ?", arrayOf(url)) }

    /** Adds imported bookmarks, skipping URLs that are already bookmarked. Returns how many were added. */
    suspend fun importAll(entries: List<Triple<String, String, String>>): Int {
        var added = 0
        writeNow { db ->
            val existing = HashSet<String>()
            db.rawQuery("SELECT url FROM bookmarks", null).mapRows { getString(0) }.forEach { existing += it }
            db.beginTransaction()
            try {
                for ((title, url, folder) in entries) {
                    if (existing.add(url)) {
                        db.insert("bookmarks", null, values(title, url, folder))
                        added++
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return added
    }

    private fun values(title: String, url: String, folder: String) = ContentValues().apply {
        put("title", title); put("url", url); put("folder", folder); put("created", System.currentTimeMillis())
    }
}

class HistoryRepo(db: BrowserDb, scope: CoroutineScope) : Repo<HistoryEntry>(db, scope) {
    override fun query(db: SQLiteDatabase) =
        db.rawQuery("SELECT id, url, title, visited FROM history ORDER BY visited DESC LIMIT 2000", null)
            .mapRows { HistoryEntry(getLong(0), getString(1), getString(2), getLong(3)) }

    fun record(url: String, title: String) = write { db ->
        // A reload or in-page change of the same address within a minute is one visit.
        val recent = db.rawQuery(
            "SELECT id FROM history WHERE url = ? AND visited > ? ORDER BY visited DESC LIMIT 1",
            arrayOf(url, (System.currentTimeMillis() - 60_000).toString()),
        ).mapRows { getLong(0) }.firstOrNull()
        if (recent == null) {
            db.insert("history", null, ContentValues().apply {
                put("url", url); put("title", title); put("visited", System.currentTimeMillis())
            })
        }
    }

    fun updateTitle(url: String, title: String) = write { db ->
        db.execSQL(
            "UPDATE history SET title = ? WHERE id = (SELECT id FROM history WHERE url = ? ORDER BY visited DESC LIMIT 1)",
            arrayOf(title, url),
        )
    }

    fun delete(id: Long) = write { it.delete("history", "id = ?", arrayOf(id.toString())) }

    fun clear() = write { it.delete("history", null, null) }
}

class DownloadRepo(db: BrowserDb, scope: CoroutineScope) : Repo<DownloadEntry>(db, scope) {
    override fun query(db: SQLiteDatabase) =
        db.rawQuery("SELECT id, name, uri, mime, size, created FROM downloads ORDER BY created DESC", null)
            .mapRows { DownloadEntry(getLong(0), getString(1), getString(2), getString(3), getLong(4), getLong(5)) }

    fun add(name: String, uri: String, mime: String, size: Long) = write {
        it.insert("downloads", null, ContentValues().apply {
            put("name", name); put("uri", uri); put("mime", mime); put("size", size)
            put("created", System.currentTimeMillis())
        })
    }

    fun delete(id: Long) = write { it.delete("downloads", "id = ?", arrayOf(id.toString())) }
}

class ShortcutRepo(db: BrowserDb, scope: CoroutineScope) : Repo<Shortcut>(db, scope) {
    override fun query(db: SQLiteDatabase) =
        db.rawQuery("SELECT id, title, url, position FROM shortcuts ORDER BY position", null)
            .mapRows { Shortcut(getLong(0), getString(1), getString(2), getInt(3)) }

    fun add(title: String, url: String) = write {
        it.insert("shortcuts", null, ContentValues().apply {
            put("title", title); put("url", url); put("position", (items.value.maxOfOrNull { s -> s.position } ?: -1) + 1)
        })
    }

    fun update(id: Long, title: String, url: String) = write {
        it.update("shortcuts", ContentValues().apply { put("title", title); put("url", url) }, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) = write { it.delete("shortcuts", "id = ?", arrayOf(id.toString())) }
}
