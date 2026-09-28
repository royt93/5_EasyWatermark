package com.mckimquyen.watermark.testutil

import com.mckimquyen.watermark.data.db.dao.WatermarkStyleHistoryDao
import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity
import com.mckimquyen.watermark.data.repo.WatermarkStyleHistoryRepository

/**
 * IDEA-12: [WatermarkStyleHistoryRepository] no-op cho MỌI `MainViewModel*RoboTest` không tự test
 * tính năng style suggestion (logic thật đã test riêng ở `WatermarkStyleCoachTest`/
 * `WatermarkStyleHistoryRepositoryRoboTest`/`MainViewModelStyleSuggestionRoboTest`) — tránh lặp lại
 * fake DAO ở ~25 file test không liên quan.
 */
fun noopWatermarkStyleHistoryRepository(): WatermarkStyleHistoryRepository =
    WatermarkStyleHistoryRepository(object : WatermarkStyleHistoryDao {
        override suspend fun insert(entity: WatermarkStyleHistoryEntity): Long = 0
        override suspend fun recent(n: Int): List<WatermarkStyleHistoryEntity> = emptyList()
        override suspend fun pruneKeepLatest(keep: Int) = Unit
    })
