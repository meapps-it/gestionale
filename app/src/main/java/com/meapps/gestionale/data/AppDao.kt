package com.meapps.gestionale.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM categories ORDER BY builtIn DESC, name") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories ORDER BY builtIn DESC, name") suspend fun categorySnapshot(): List<CategoryEntity>
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int
    @Insert suspend fun insertCategories(items: List<CategoryEntity>): List<Long>
    @Insert suspend fun insertCategory(item: CategoryEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreCategories(items: List<CategoryEntity>)

    @Query("""SELECT e.*, c.name categoryName, c.icon categoryIcon FROM entries e
        JOIN categories c ON c.id=e.categoryId ORDER BY e.nextDate, e.name""")
    fun entries(): Flow<List<EntryWithCategory>>
    @Query("SELECT * FROM entries") suspend fun entrySnapshot(): List<EntryEntity>
    @Insert suspend fun insertEntry(item: EntryEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreEntries(items: List<EntryEntity>)
    @Update suspend fun updateEntry(item: EntryEntity)
    @Delete suspend fun deleteEntry(item: EntryEntity)
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM categories") suspend fun clearCategories()

    @Query("SELECT * FROM reminders WHERE entryId=:entryId ORDER BY daysBefore DESC") fun reminders(entryId: Long): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders") suspend fun reminderSnapshot(): List<ReminderEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReminders(items: List<ReminderEntity>)
    @Query("DELETE FROM reminders WHERE entryId=:entryId") suspend fun clearReminders(entryId: Long)

    @Query("SELECT * FROM history WHERE entryId=:entryId ORDER BY dueDate DESC") fun history(entryId: Long): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history") suspend fun historySnapshot(): List<HistoryEntity>
    @Insert suspend fun insertHistory(item: HistoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHistories(items: List<HistoryEntity>)

    @Transaction suspend fun replaceReminders(entryId: Long, days: List<Int>) {
        clearReminders(entryId)
        insertReminders(days.distinct().map { ReminderEntity(entryId = entryId, daysBefore = it) })
    }
}
