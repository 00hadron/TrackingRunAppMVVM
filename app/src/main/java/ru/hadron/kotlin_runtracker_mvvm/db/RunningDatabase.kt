package ru.hadron.kotlin_runtracker_mvvm.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Run::class],
    version = 1
)
@TypeConverters(Converters::class)
abstract class RunningDatabase : RoomDatabase() {
    abstract fun getRunDao(): RunDao
}

/*вот это все не нужно из-за dagger
  companion object {
@Volatile
private var instance: ArticleDatabase? = null
private var LOCK = Any()

operator fun invoke(context: Context) = instance?: synchronized(Companion.LOCK) {
instance?:createDatabase(context).also{ instance = it}
}

private fun createDatabase(context: Context) =
Room.databaseBuilder(
context.applicationContext,
ArticleDatabase::class.java,
"article_db.db")
.build()
}*/