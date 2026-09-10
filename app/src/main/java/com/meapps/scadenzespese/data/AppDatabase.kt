package com.meapps.scadenzespese.data

import android.content.Context
import androidx.room.*

class Converters {
    @TypeConverter fun toType(value: String) = EntryType.valueOf(value)
    @TypeConverter fun fromType(value: EntryType) = value.name
    @TypeConverter fun toRenewal(value: String) = RenewalMode.valueOf(value)
    @TypeConverter fun fromRenewal(value: RenewalMode) = value.name
    @TypeConverter fun toStatus(value: String) = EntryStatus.valueOf(value)
    @TypeConverter fun fromStatus(value: EntryStatus) = value.name
    @TypeConverter fun toUnit(value: String) = FrequencyUnit.valueOf(value)
    @TypeConverter fun fromUnit(value: FrequencyUnit) = value.name
}

@Database(entities = [CategoryEntity::class, EntryEntity::class, HistoryEntity::class,
    ReminderEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context.applicationContext,
            AppDatabase::class.java, "scadenze-spese.db").build()
    }
}
