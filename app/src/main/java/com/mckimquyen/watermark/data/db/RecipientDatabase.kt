package com.mckimquyen.watermark.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mckimquyen.watermark.data.db.dao.RecipientDao
import com.mckimquyen.watermark.data.model.entity.Recipient

/**
 * IDEA-10: DB riêng cho Người nhận (không seed từ asset, không đụng [AppDatabase]).
 */
@Database(
    entities = [Recipient::class],
    version = 1,
    exportSchema = false
)
abstract class RecipientDatabase : RoomDatabase() {
    abstract fun recipientDao(): RecipientDao
}
