package com.meapps.scadenzespese.data

import androidx.room.withTransaction
import com.meapps.scadenzespese.notifications.NotificationScheduler
import java.time.LocalDate

class AppRepository(private val db: AppDatabase, private val scheduler: NotificationScheduler) {
    private val dao = db.dao()
    val entries = dao.entries()
    val categories = dao.categories()

    suspend fun ensureSeeded() = db.withTransaction {
        if (dao.categoryCount() != 0) return@withTransaction
        val names = listOf("🚗" to "Auto e moto", "⌂" to "Casa", "⚡" to "Utenze",
            "▶" to "Abbonamenti", "▣" to "Documenti", "◆" to "Assicurazioni",
            "+" to "Salute", "€" to "Tasse e tributi", "▤" to "Finanziamenti",
            "●" to "Servizi", "…" to "Altro")
        val ids = dao.insertCategories(names.map { CategoryEntity(name=it.second, icon=it.first, builtIn=true) })
        val today = LocalDate.now()
        val demos = listOf(
            EntryEntity(name="Assicurazione auto", categoryId=ids[5], type=EntryType.BOTH, amountCents=62000,
                nextDate=today.plusDays(8).toEpochDay(), frequencyCount=1, frequencyUnit=FrequencyUnit.YEARS, renewalMode=RenewalMode.MANUAL),
            EntryEntity(name="Netflix", categoryId=ids[3], type=EntryType.EXPENSE, amountCents=1399,
                nextDate=today.plusDays(1).toEpochDay(), frequencyCount=1, frequencyUnit=FrequencyUnit.MONTHS, renewalMode=RenewalMode.AUTOMATIC),
            EntryEntity(name="Bollo auto", categoryId=ids[0], type=EntryType.BOTH, amountCents=19840,
                nextDate=today.plusDays(25).toEpochDay(), frequencyCount=1, frequencyUnit=FrequencyUnit.YEARS, renewalMode=RenewalMode.MANUAL),
            EntryEntity(name="Carta d'identità", categoryId=ids[4], type=EntryType.DEADLINE, amountCents=null,
                nextDate=today.plusMonths(7).toEpochDay(), frequencyCount=10, frequencyUnit=FrequencyUnit.YEARS, renewalMode=RenewalMode.MANUAL))
        demos.forEach { item ->
            val id = dao.insertEntry(item)
            dao.insertReminders(listOf(30,15,7,1,0).map { ReminderEntity(entryId=id, daysBefore=it) })
            scheduler.schedule(id, item.copy(id=id), listOf(30,15,7,1,0))
        }
    }

    fun reminders(id: Long) = dao.reminders(id)
    fun history(id: Long) = dao.history(id)
    suspend fun save(draft: EntryDraft): Long = db.withTransaction {
        val old = dao.entrySnapshot().firstOrNull { it.id == draft.id }
        val entity = EntryEntity(draft.id, draft.name.trim(), draft.categoryId, draft.type,
            draft.amountCents, draft.nextDate, draft.frequencyCount, draft.frequencyUnit,
            draft.renewalMode, old?.status ?: EntryStatus.ACTIVE, old?.createdAt ?: System.currentTimeMillis())
        val id = if (draft.id == 0L) dao.insertEntry(entity) else { dao.updateEntry(entity); draft.id }
        dao.replaceReminders(id, draft.reminderDays)
        scheduler.schedule(id, entity.copy(id=id), draft.reminderDays)
        id
    }
    suspend fun complete(item: EntryWithCategory) = db.withTransaction {
        dao.insertHistory(HistoryEntity(entryId=item.id, dueDate=item.nextDate, amountCents=item.amountCents))
        val updated = if (item.renewalMode == RenewalMode.AUTOMATIC)
            item.asEntity().copy(nextDate=nextDate(item.asEntity()), status=EntryStatus.ACTIVE)
        else item.asEntity().copy(status=EntryStatus.COMPLETED)
        dao.updateEntry(updated)
        scheduler.schedule(item.id, updated, dao.reminderSnapshot().filter { it.entryId==item.id }.map { it.daysBefore })
    }
    suspend fun renew(item: EntryWithCategory, date: Long, amount: Long?) = db.withTransaction {
        dao.insertHistory(HistoryEntity(entryId=item.id, dueDate=item.nextDate, amountCents=item.amountCents))
        val updated = item.asEntity().copy(nextDate=date, amountCents=amount, status=EntryStatus.ACTIVE)
        dao.updateEntry(updated)
        scheduler.schedule(item.id, updated, dao.reminderSnapshot().filter { it.entryId==item.id }.map { it.daysBefore })
    }
    suspend fun delete(item: EntryWithCategory) { dao.deleteEntry(item.asEntity()); scheduler.cancel(item.id) }
    suspend fun addCategory(name: String, icon: String) = dao.insertCategory(CategoryEntity(name=name.trim(), icon=icon))
    private fun nextDate(item: EntryEntity): Long {
        val date = LocalDate.ofEpochDay(item.nextDate)
        return when(item.frequencyUnit) {
            FrequencyUnit.DAYS -> date.plusDays(item.frequencyCount.toLong())
            FrequencyUnit.WEEKS -> date.plusWeeks(item.frequencyCount.toLong())
            FrequencyUnit.MONTHS -> date.plusMonths(item.frequencyCount.toLong())
            FrequencyUnit.YEARS -> date.plusYears(item.frequencyCount.toLong())
        }.toEpochDay()
    }
    suspend fun snapshots() = BackupSnapshot(dao.categorySnapshot(), dao.entrySnapshot(),
        dao.reminderSnapshot(), dao.historySnapshot())
    suspend fun restore(snapshot: BackupSnapshot) = db.withTransaction {
        snapshot.categories.forEach { require(it.name.isNotBlank()) }
        dao.clearEntries(); dao.clearCategories()
        dao.restoreCategories(snapshot.categories); dao.restoreEntries(snapshot.entries)
        dao.insertReminders(snapshot.reminders); dao.insertHistories(snapshot.history)
        snapshot.entries.forEach { item -> scheduler.schedule(item.id, item,
            snapshot.reminders.filter { it.entryId==item.id }.map { it.daysBefore }) }
    }
}
