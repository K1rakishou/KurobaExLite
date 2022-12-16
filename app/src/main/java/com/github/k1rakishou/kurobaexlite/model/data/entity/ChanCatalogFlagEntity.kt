package com.github.k1rakishou.kurobaexlite.model.data.entity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Entity(
  tableName = ChanCatalogFlagEntity.TABLE_NAME,
  primaryKeys = [
    "catalog_site_key",
    "catalog_board_code",
    "flag_key"
  ],
  indices = [
    Index(value = ["sort_order"])
  ]
)
data class ChanCatalogFlagEntity(
  @Embedded(prefix = "catalog_") val catalogKey: CatalogKey,
  @ColumnInfo(name = "flag_key") val flagKey: String,
  @ColumnInfo(name = "flag_name") val flagName: String,
  @ColumnInfo(name = "flag_id") val flagId: Int?,
  @ColumnInfo(name = "sort_order") val sortOrder: Int,
) {

  companion object {
    const val TABLE_NAME = "chan_catalog_flags"
  }
}

@Dao
abstract class ChanCatalogFlagDao {

  @Transaction
  @Query("""
    SELECT * FROM chan_catalog_flags
    WHERE
        chan_catalog_flags.catalog_site_key = :siteKey
    AND
        chan_catalog_flags.catalog_board_code = :boardCode
    ORDER BY 
        chan_catalog_flags.sort_order
  """)
  abstract suspend fun selectCatalogFlags(siteKey: String, boardCode: String): List<ChanCatalogFlagEntity>

  @Transaction
  open suspend fun replaceCatalogFlags(siteKey: String, boardCode: String, newChanCatalogFlagEntities: List<ChanCatalogFlagEntity>) {
    deleteCatalogFlags(siteKey, boardCode)
    insertCatalogFlags(newChanCatalogFlagEntities)
  }

  @Insert
  protected abstract suspend fun insertCatalogFlags(chanCatalogFlagEntities: List<ChanCatalogFlagEntity>)

  @Query("""
    DELETE FROM chan_catalog_flags
    WHERE
        chan_catalog_flags.catalog_site_key = :siteKey
    AND
        chan_catalog_flags.catalog_board_code = :boardCode
  """)
  protected abstract suspend fun deleteCatalogFlags(siteKey: String, boardCode: String)

}