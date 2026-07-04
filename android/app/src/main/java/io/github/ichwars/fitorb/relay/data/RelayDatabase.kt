package io.github.ichwars.fitorb.relay.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RelaySampleEntity::class], version = 1, exportSchema = true)
abstract class RelayDatabase : RoomDatabase() {
    abstract fun relaySampleDao(): RelaySampleDao
}
