package com.mckimquyen.watermark.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mckimquyen.watermark.data.db.dao.TemplateDao
import com.mckimquyen.watermark.data.model.entity.Template

@Database(entities = [Template::class], version = 1, exportSchema = false)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
}
