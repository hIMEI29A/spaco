package com.spaco.app.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SkillEntity::class,
        DeckEntity::class,
        PracticeItemEntity::class,
        ReviewEventEntity::class,
        SessionStateEntity::class,
        ReminderStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SpaCoDatabase : RoomDatabase() {
    abstract fun skillDao(): SkillDao
    abstract fun deckDao(): DeckDao
    abstract fun itemDao(): ItemDao
    abstract fun reviewDao(): ReviewDao
    abstract fun sessionDao(): SessionDao
    abstract fun reminderDao(): ReminderDao
}
