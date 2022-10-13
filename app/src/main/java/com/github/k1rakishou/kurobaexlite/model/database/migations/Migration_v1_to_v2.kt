package com.github.k1rakishou.kurobaexlite.model.database.migations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v1_to_v2 : Migration(1, 2) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `chan_catalog_flags` 
      (
          `catalog_site_key` TEXT NOT NULL, 
          `catalog_board_code` TEXT NOT NULL, 
          `flag_key` TEXT NOT NULL, 
          `flag_name` TEXT NOT NULL, 
          `sort_order` INTEGER NOT NULL, 
          PRIMARY KEY(`catalog_site_key`, `catalog_board_code`, `flag_key`)
        )
    """.trimIndent()
    )

    database.execSQL("""
      CREATE INDEX IF NOT EXISTS `index_chan_catalog_flags_sort_order` ON `chan_catalog_flags` (`sort_order`)
    """.trimIndent())
  }

}