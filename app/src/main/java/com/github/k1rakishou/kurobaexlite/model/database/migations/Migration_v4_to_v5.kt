package com.github.k1rakishou.kurobaexlite.model.database.migations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v4_to_v5 : Migration(4, 5) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `chan_post_hides` (
          `apply_to_replies` INTEGER NOT NULL, 
          `state` INTEGER NOT NULL, 
          `inserted_on` INTEGER NOT NULL, 
          `post_hide_site_key` TEXT NOT NULL, 
          `post_hide_board_code` TEXT NOT NULL, 
          `post_hide_thread_no` INTEGER NOT NULL, 
          `post_hide_post_no` INTEGER NOT NULL, 
          `post_hide_post_sub_no` INTEGER NOT NULL, 
          PRIMARY KEY(`post_hide_site_key`, `post_hide_board_code`, `post_hide_thread_no`, `post_hide_post_no`, `post_hide_post_sub_no`)
      )
    """.trimIndent()
    )

    database.execSQL(
      """
        CREATE TABLE IF NOT EXISTS `chan_post_hide_replies` (
            `owner_post_hide_site_key` TEXT NOT NULL, 
            `owner_post_hide_board_code` TEXT NOT NULL, 
            `owner_post_hide_thread_no` INTEGER NOT NULL, 
            `owner_post_hide_post_no` INTEGER NOT NULL, 
            `owner_post_hide_post_sub_no` INTEGER NOT NULL, 
            `replies_to_hidden_post_site_key` TEXT NOT NULL, 
            `replies_to_hidden_post_board_code` TEXT NOT NULL, 
            `replies_to_hidden_post_thread_no` INTEGER NOT NULL, 
            `replies_to_hidden_post_post_no` INTEGER NOT NULL, 
            `replies_to_hidden_post_post_sub_no` INTEGER NOT NULL, 
            PRIMARY KEY(`owner_post_hide_site_key`, `owner_post_hide_board_code`, `owner_post_hide_thread_no`, `owner_post_hide_post_no`, `owner_post_hide_post_sub_no`, `replies_to_hidden_post_site_key`, `replies_to_hidden_post_board_code`, `replies_to_hidden_post_thread_no`, `replies_to_hidden_post_post_no`, `replies_to_hidden_post_post_sub_no`), 
            FOREIGN KEY(`owner_post_hide_site_key`, `owner_post_hide_board_code`, `owner_post_hide_thread_no`, `owner_post_hide_post_no`, `owner_post_hide_post_sub_no`) 
                REFERENCES `chan_post_hides`(`post_hide_site_key`, `post_hide_board_code`, `post_hide_thread_no`, `post_hide_post_no`, `post_hide_post_sub_no`) ON UPDATE CASCADE ON DELETE CASCADE 
        )
      """.trimIndent()
    )
  }

}