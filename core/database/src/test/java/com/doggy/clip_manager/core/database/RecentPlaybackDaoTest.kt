package com.doggy.clip_manager.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.database.model.RecentPlaybackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric 4.16 cannot run compileSdk 37; pin the newest SDK it supports.
@Config(sdk = [35])
class RecentPlaybackDaoTest {
    private lateinit var db: ClipDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ClipDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeRecent_ordersByPlayedAtDescendingAndLimits() = runTest {
        val dao = db.recentPlaybackDao()
        dao.upsert(RecentPlaybackEntity("/a.ts", 0, 100))
        dao.upsert(RecentPlaybackEntity("/b.ts", 0, 300))
        dao.upsert(RecentPlaybackEntity("/c.ts", 0, 200))

        val recent = dao.observeRecent(limit = 2).first()

        assertEquals(listOf("/b.ts", "/c.ts"), recent.map { it.path })
    }

    @Test
    fun upsert_replacesExistingPath() = runTest {
        val dao = db.recentPlaybackDao()
        dao.upsert(RecentPlaybackEntity("/a.ts", 0, 100))
        dao.upsert(RecentPlaybackEntity("/a.ts", 5000, 400))

        val recent = dao.observeRecent(limit = 10).first()

        assertEquals(listOf(RecentPlaybackEntity("/a.ts", 5000, 400)), recent)
    }
}
