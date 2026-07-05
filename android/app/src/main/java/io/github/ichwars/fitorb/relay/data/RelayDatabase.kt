package io.github.ichwars.fitorb.relay.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RelaySampleEntity::class], version = 1, exportSchema = true)
abstract class RelayDatabase : RoomDatabase() {
    abstract fun relaySampleDao(): RelaySampleDao

    companion object {
        @Volatile
        private var instance: RelayDatabase? = null

        fun open(context: Context): RelayDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RelayDatabase::class.java,
                    "fitorb_relay.db",
                ).build().also { instance = it }
            }
    }
}
