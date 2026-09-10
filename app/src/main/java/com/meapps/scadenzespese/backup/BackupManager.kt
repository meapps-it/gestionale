package com.meapps.scadenzespese.backup

import android.content.Context
import android.net.Uri
import com.meapps.scadenzespese.data.*
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(private val context: Context, private val repository: AppRepository) {
    suspend fun exportBackup(uri: Uri) {
        val s = repository.snapshots()
        val root = JSONObject().put("format", "com.meapps.scadenzespese.backup").put("version", 1)
        root.put("categories", JSONArray(s.categories.map { JSONObject()
            .put("id",it.id).put("name",it.name).put("icon",it.icon).put("builtIn",it.builtIn) }))
        root.put("entries", JSONArray(s.entries.map { JSONObject()
            .put("id",it.id).put("name",it.name).put("categoryId",it.categoryId).put("type",it.type.name)
            .put("amountCents",it.amountCents ?: JSONObject.NULL).put("nextDate",it.nextDate)
            .put("frequencyCount",it.frequencyCount).put("frequencyUnit",it.frequencyUnit.name)
            .put("renewalMode",it.renewalMode.name).put("status",it.status.name).put("createdAt",it.createdAt) }))
        root.put("reminders", JSONArray(s.reminders.map { JSONObject().put("id",it.id)
            .put("entryId",it.entryId).put("daysBefore",it.daysBefore) }))
        root.put("history", JSONArray(s.history.map { JSONObject().put("id",it.id)
            .put("entryId",it.entryId).put("dueDate",it.dueDate)
            .put("amountCents",it.amountCents ?: JSONObject.NULL).put("completedAt",it.completedAt) }))
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(root.toString(2)) }
    }
    suspend fun importBackup(uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        require(root.getString("format") == "com.meapps.scadenzespese.backup") { "File di backup non valido" }
        require(root.getInt("version") == 1) { "Versione backup non supportata" }
        fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        val cats = root.getJSONArray("categories").objects().map { CategoryEntity(it.getLong("id"),
            it.getString("name"),it.getString("icon"),it.getBoolean("builtIn")) }
        val entries = root.getJSONArray("entries").objects().map { EntryEntity(it.getLong("id"),
            it.getString("name"),it.getLong("categoryId"),EntryType.valueOf(it.getString("type")),
            if(it.isNull("amountCents")) null else it.getLong("amountCents"),it.getLong("nextDate"),
            it.getInt("frequencyCount"),FrequencyUnit.valueOf(it.getString("frequencyUnit")),
            RenewalMode.valueOf(it.getString("renewalMode")),EntryStatus.valueOf(it.getString("status")),
            it.getLong("createdAt")) }
        val reminders = root.getJSONArray("reminders").objects().map { ReminderEntity(it.getLong("id"),it.getLong("entryId"),it.getInt("daysBefore")) }
        val history = root.getJSONArray("history").objects().map { HistoryEntity(it.getLong("id"),
            it.getLong("entryId"),it.getLong("dueDate"),if(it.isNull("amountCents")) null else it.getLong("amountCents"),it.getLong("completedAt")) }
        require(entries.all { e -> cats.any { it.id == e.categoryId } }) { "Categorie mancanti nel backup" }
        repository.restore(BackupSnapshot(cats,entries,reminders,history))
    }
}
