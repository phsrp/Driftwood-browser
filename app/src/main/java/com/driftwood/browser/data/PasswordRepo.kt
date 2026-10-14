package com.driftwood.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.driftwood.browser.autofill.DomainMatcher
import com.driftwood.browser.security.KeystoreCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class SavedLogin(
    val id: Long,
    val guid: String,
    val origin: String,
    val host: String,
    val username: String,
    val password: String,
)

data class NewLogin(val url: String, val username: String, val password: String)

/**
 * Saved logins, in their own database file ("passwords.db") so backup rules can
 * exclude it. Usernames and passwords are stored encrypted; only the website's
 * host is stored readable, because it's needed to find matching logins.
 */
class PasswordRepo(context: Context) {
    private val helper = object : SQLiteOpenHelper(context, "passwords.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE logins (id INTEGER PRIMARY KEY, guid TEXT NOT NULL UNIQUE, origin TEXT NOT NULL, " +
                    "host TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, created INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX logins_host ON logins(host)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    suspend fun all(): List<SavedLogin> = withContext(Dispatchers.IO) { read(null, emptyArray()) }

    /**
     * Logins Gecko may use for [domain]. Gecko asks per site domain, so this only
     * returns logins whose host is exactly a host currently open in a tab. The
     * final exact check against the page happens again when a login is picked.
     */
    suspend fun candidates(domain: String, openHosts: Set<String>): List<SavedLogin> = withContext(Dispatchers.IO) {
        val d = domain.lowercase()
        read("host = ? OR host LIKE ?", arrayOf(d, "%.$d")).filter { it.host in openHosts }
    }

    /** Saves a login Gecko offered to save (after the user agreed). Updates it if it exists. */
    suspend fun saveFromBrowser(origin: String, username: String, password: String, guid: String?) = withContext(Dispatchers.IO) {
        val normalized = DomainMatcher.origin(origin) ?: return@withContext
        val existing = read("host = ?", arrayOf(DomainMatcher.host(normalized)!!))
            .firstOrNull { (guid != null && it.guid == guid) || (it.origin == normalized && it.username == username) }
        if (existing != null) {
            helper.writableDatabase.update(
                "logins", encrypted(username, password), "id = ?", arrayOf(existing.id.toString()),
            )
        } else {
            insert(helper.writableDatabase, normalized, username, password)
        }
    }

    data class ImportResult(val added: Int, val updated: Int, val skipped: Int)

    /** Imports logins (from a Chrome CSV). Same site + same username replaces the old password. */
    suspend fun importAll(logins: List<NewLogin>): ImportResult = withContext(Dispatchers.IO) {
        val existing = read(null, emptyArray()).associateBy { it.origin + "\n" + it.username }.toMutableMap()
        var added = 0
        var updated = 0
        var skipped = 0
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (login in logins) {
                val origin = DomainMatcher.origin(login.url)
                if (origin == null || login.password.isEmpty()) {
                    skipped++
                    continue
                }
                val match = existing[origin + "\n" + login.username]
                if (match != null) {
                    if (match.password != login.password) {
                        db.update("logins", encrypted(login.username, login.password), "id = ?", arrayOf(match.id.toString()))
                        updated++
                    } else {
                        skipped++
                    }
                } else {
                    val id = insert(db, origin, login.username, login.password)
                    existing[origin + "\n" + login.username] =
                        SavedLogin(id, "", origin, DomainMatcher.host(origin)!!, login.username, login.password)
                    added++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        ImportResult(added, updated, skipped)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("logins", "id = ?", arrayOf(id.toString()))
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("logins", null, null)
    }

    private fun insert(db: SQLiteDatabase, origin: String, username: String, password: String): Long =
        db.insert("logins", null, encrypted(username, password).apply {
            put("guid", UUID.randomUUID().toString())
            put("origin", origin)
            put("host", DomainMatcher.host(origin))
            put("created", System.currentTimeMillis())
        })

    private fun encrypted(username: String, password: String) = ContentValues().apply {
        put("username", KeystoreCipher.encrypt(username))
        put("password", KeystoreCipher.encrypt(password))
    }

    private fun read(where: String?, args: Array<String>): List<SavedLogin> =
        helper.readableDatabase.query(
            "logins", arrayOf("id", "guid", "origin", "host", "username", "password"),
            where, args, null, null, "host",
        ).use { c ->
            val out = ArrayList<SavedLogin>(c.count)
            while (c.moveToNext()) {
                val login = runCatching {
                    SavedLogin(
                        id = c.getLong(0),
                        guid = c.getString(1),
                        origin = c.getString(2),
                        host = c.getString(3),
                        username = KeystoreCipher.decrypt(c.getString(4)),
                        password = KeystoreCipher.decrypt(c.getString(5)),
                    )
                }.getOrNull()
                if (login != null) out += login
            }
            out
        }
}
