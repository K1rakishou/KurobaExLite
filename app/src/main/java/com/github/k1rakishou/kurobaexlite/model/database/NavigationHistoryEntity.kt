package com.github.k1rakishou.kurobaexlite.model.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
  tableName = NavigationHistoryEntity.TABLE_NAME
)
class NavigationHistoryEntity(
  @PrimaryKey @Embedded(prefix = "catalog_or_thread_") val catalogOrThreadKey: CatalogOrThreadKey,
  @ColumnInfo(name = "title") val title: String?,
  @ColumnInfo(name = "icon_url") val iconUrl: String?,
  @ColumnInfo(name = "sort_order") val sortOrder: Int
) {

  companion object {
    const val TABLE_NAME = "navigation_history"
  }
}

@Dao
abstract class NavigationHistoryDao {

  @Query("""
    SELECT * FROM navigation_history
    ORDER BY sort_order
    LIMIT :maxCount
  """)
  abstract suspend fun selectAllOrdered(maxCount: Int): List<NavigationHistoryEntity>

  @Query("DELETE FROM navigation_history")
  abstract suspend fun deleteAll()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertOrUpdateMany(navigationHistoryEntityList: List<NavigationHistoryEntity>)

}