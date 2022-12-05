package com.github.k1rakishou.kurobaexlite.model.database.migations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v2_to_v3 : Migration(2, 3) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.execSQL("ALTER TABLE chan_catalogs ADD COLUMN bump_limit INTEGER DEFAULT NULL")
  }

}