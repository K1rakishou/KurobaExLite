package com.github.k1rakishou.kurobaexlite.model.database.migations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v3_to_v4 : Migration(3, 4) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.execSQL("ALTER TABLE chan_catalog_flags ADD COLUMN flag_id INTEGER DEFAULT NULL")
  }

}