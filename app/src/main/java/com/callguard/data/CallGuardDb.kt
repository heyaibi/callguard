package com.callguard.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CallGuardDb(context: Context) : SQLiteOpenHelper(context, "callguard.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cache (number TEXT PRIMARY KEY, name TEXT NOT NULL, origin TEXT NOT NULL, last_seen INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE reputation (number TEXT PRIMARY KEY, score INTEGER NOT NULL, is_spam INTEGER NOT NULL, source TEXT NOT NULL, updated INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE call_log (id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL, name TEXT, source TEXT NOT NULL, direction TEXT NOT NULL, outcome TEXT NOT NULL, app TEXT, timestamp INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX call_log_number_time ON call_log (number, timestamp)")
        db.execSQL("CREATE TABLE message_log (id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL, name TEXT, body TEXT NOT NULL, outcome TEXT NOT NULL, timestamp INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Runs [block]; on any failure returns [fallback] so identity and spam lookups never crash a call. */
    internal inline fun <T> safely(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            fallback
        }
}
