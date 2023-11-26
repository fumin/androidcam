package com.topunion.camera

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import java.io.File
import java.time.Instant


class DBHelper(context: Context) : SQLiteOpenHelper(SDCardDBContext(context), DATABASE_NAME, null, DATABASE_VERSION)  {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE "+TableConfig+" ("+
                ColConfigKey+" TEXT PRIMARY KEY, "+
                ColConfigValue+" TEXT"+
                ")")
        db.execSQL("CREATE TABLE "+TableUploadLog+" ("+
                "name TEXT PRIMARY KEY,"+
                "t INTEGER,"+
                "err TEXT"+
                ")")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL("DROP TABLE IF EXISTS $TableConfig")
        db.execSQL("DROP TABLE IF EXISTS $TableUploadLog")
        onCreate(db)
    }

    companion object {
        // If you change the database schema, you must increment the database version.
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "app.db"

        const val TableConfig = "config"
        const val ColConfigKey = "key"
        const val ColConfigValue = "value"
        const val ConfigCameraID = "cameraID"
        const val ConfigUploadPath = "uploadPath"

        const val TableUploadLog = "uploadLog"

        fun readConfig(db: SQLiteDatabase): Config {
            val cursor = db.query(
                TableConfig,
                arrayOf(ColConfigKey, ColConfigValue),
                "",
                emptyArray(),
                null, null, null)
            val cfg = Config()
            while (cursor.moveToNext()) {
                val k = cursor.getString(0)
                val v = cursor.getString(1)
                if (k == ConfigCameraID) {
                    cfg.cameraID = v
                } else if (k == ConfigUploadPath) {
                    cfg.uploadPath = v
                }
            }
            cursor.close()
            return cfg
        }

        fun logUpload(db: SQLiteDatabase, name: String, errStr: String) {
            val values = ContentValues().apply {
                put("name", name)
                put("t", Instant.now().epochSecond)
                put("err", errStr)
            }
            db.insertWithOnConflict(TableUploadLog, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}

class Config {
    var cameraID = ""
    var uploadPath = ""
}

class SDCardDBContext(base: Context) : ContextWrapper(base) {
    override fun getDatabasePath(name: String): File {
        val sdcard = this.getExternalFilesDir(null)?.absolutePath
        val dbFile = File(sdcard, name)
        dbFile.parentFile.mkdirs()
        return dbFile
    }
}

