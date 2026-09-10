package com.meapps.scadenzespese.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EntryType { DEADLINE, EXPENSE, BOTH }
enum class RenewalMode { AUTOMATIC, MANUAL }
enum class EntryStatus { ACTIVE, COMPLETED }
enum class FrequencyUnit { DAYS, WEEKS, MONTHS, YEARS }

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String,
    val icon: String, val builtIn: Boolean = false)

@Entity(tableName = "entries", foreignKeys = [ForeignKey(entity = CategoryEntity::class,
    parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("categoryId"), Index("nextDate")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val categoryId: Long, val type: EntryType, val amountCents: Long?,
    val nextDate: Long, val frequencyCount: Int, val frequencyUnit: FrequencyUnit,
    val renewalMode: RenewalMode, val status: EntryStatus = EntryStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history", foreignKeys = [ForeignKey(entity = EntryEntity::class,
    parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")])
data class HistoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val entryId: Long,
    val dueDate: Long, val amountCents: Long?, val completedAt: Long = System.currentTimeMillis())

@Entity(tableName = "reminders", foreignKeys = [ForeignKey(entity = EntryEntity::class,
    parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")])
data class ReminderEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long, val daysBefore: Int)

data class EntryWithCategory(
    val id: Long, val name: String, val categoryId: Long, val categoryName: String,
    val categoryIcon: String, val type: EntryType, val amountCents: Long?, val nextDate: Long,
    val frequencyCount: Int, val frequencyUnit: FrequencyUnit, val renewalMode: RenewalMode,
    val status: EntryStatus, val createdAt: Long
) {
    fun asEntity() = EntryEntity(id, name, categoryId, type, amountCents, nextDate,
        frequencyCount, frequencyUnit, renewalMode, status, createdAt)
}

data class EntryDraft(
    val id: Long = 0, val name: String, val categoryId: Long, val type: EntryType,
    val amountCents: Long?, val nextDate: Long, val frequencyCount: Int,
    val frequencyUnit: FrequencyUnit, val renewalMode: RenewalMode,
    val reminderDays: List<Int>
)

data class BackupSnapshot(val categories: List<CategoryEntity>, val entries: List<EntryEntity>,
    val reminders: List<ReminderEntity>, val history: List<HistoryEntity>)
