package com.spaco.app.core.data.db

import androidx.room.TypeConverter
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.SessionStatus
import java.time.Instant

/**
 * Room cannot store `java.time` or enums directly. Timestamps are stored as UTC
 * epoch milliseconds; the "practice day" boundary used by streaks is computed
 * in the device's local zone at read time, not here.
 */
class Converters {

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun learningStateToString(value: LearningState): String = value.name

    @TypeConverter
    fun stringToLearningState(value: String): LearningState = LearningState.valueOf(value)

    @TypeConverter
    fun gradedByToString(value: GradedBy): String = value.name

    @TypeConverter
    fun stringToGradedBy(value: String): GradedBy = GradedBy.valueOf(value)

    @TypeConverter
    fun sessionStatusToString(value: SessionStatus): String = value.name

    @TypeConverter
    fun stringToSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)

    // Identifiers are generated, so a newline is a safe separator and keeps the
    // stored value readable in a database browser.
    @TypeConverter
    fun stringListToString(value: List<String>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun stringToStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\n"
    }
}
