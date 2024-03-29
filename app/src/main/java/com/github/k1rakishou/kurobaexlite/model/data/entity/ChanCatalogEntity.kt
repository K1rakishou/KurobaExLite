package com.github.k1rakishou.kurobaexlite.model.data.entity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.BoardFlag
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanCatalog
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor

@Entity(
  tableName = ChanCatalogEntity.TABLE_NAME,
  indices = [
    Index(value = ["database_id"], unique = true)
  ]
)
data class ChanCatalogEntity(
  @PrimaryKey @Embedded(prefix = "catalog_") val catalogKey: CatalogKey,
  @ColumnInfo(name = "database_id") val databaseId: Long = -1L,
  @ColumnInfo(name = "board_title") val boardTitle: String?,
  @ColumnInfo(name = "board_description") val boardDescription: String?,
  @ColumnInfo(name = "work_safe") val workSafe: Boolean,
  @ColumnInfo(name = "bump_limit") val bumpLimit: Int?,
) {

  fun toChanCatalog(flags: List<BoardFlag>): ChanCatalog {
    return ChanCatalog(
      catalogDescriptor = catalogKey.catalogDescriptor,
      boardTitle = boardTitle,
      boardDescription = boardDescription,
      workSafe = workSafe,
      maxAttachFilesPerPost = 1,
      flags = flags,
      bumpLimit = bumpLimit
    )
  }

  companion object {
    const val TABLE_NAME = "chan_catalogs"
  }
}

@Entity(
  tableName = ChanCatalogSortOrderEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanCatalogEntity::class,
      parentColumns = ["database_id"],
      childColumns = ["owner_database_id"],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ]
)
data class ChanCatalogSortOrderEntity(
  @PrimaryKey(autoGenerate = false) @ColumnInfo(name = "owner_database_id") val ownerDatabaseId: Long,
  @ColumnInfo(name = "sort_order") val sortOrder: Int
) {

  companion object {
    const val TABLE_NAME = "chan_catalog_sort_order"
  }
}

data class ChanCatalogEntitySorted(
  @Embedded
  val chanCatalogEntity: ChanCatalogEntity,
  @Relation(
    entity = ChanCatalogSortOrderEntity::class,
    parentColumn = "database_id",
    entityColumn = "owner_database_id"
  )
  val chanCatalogSortOrderEntity: ChanCatalogSortOrderEntity
) {

  fun toChanCatalog(flags: List<BoardFlag>): ChanCatalog {
    return chanCatalogEntity.toChanCatalog(flags)
  }

}

@Dao
abstract class ChanCatalogDao {

  @Transaction
  @RewriteQueriesToDropUnusedColumns
  @Query("""
    SELECT * FROM chan_catalogs
    INNER JOIN chan_catalog_sort_order
        ON chan_catalogs.database_id = chan_catalog_sort_order.owner_database_id
    WHERE
        chan_catalogs.catalog_site_key = :siteKey
    ORDER BY 
        chan_catalog_sort_order.sort_order
  """)
  abstract suspend fun selectAllForSiteOrdered(siteKey: String): List<ChanCatalogEntitySorted>

  @Query("""
    SELECT * FROM chan_catalogs
    WHERE 
        chan_catalogs.catalog_site_key = :siteKey
    AND
        chan_catalogs.catalog_board_code = :boardCode
    LIMIT 1
  """)
  abstract suspend fun selectCatalog(siteKey: String, boardCode: String): ChanCatalogEntity?

  @Transaction
  open suspend fun insertChanCatalogEntityList(chanCatalogEntityList: List<ChanCatalogEntity>): Map<CatalogDescriptor, Long> {
    val resultDatabaseIds = mutableMapWithCap<CatalogDescriptor, Long>(chanCatalogEntityList.size)

    for (chanCatalogEntity in chanCatalogEntityList) {
      val catalogKey = chanCatalogEntity.catalogKey

      insertChanCatalogEntity(
        siteKey = catalogKey.siteKey,
        boardCode = catalogKey.boardCode,
        boardTitle = chanCatalogEntity.boardTitle,
        boardDescription = chanCatalogEntity.boardDescription,
        workSafe = chanCatalogEntity.workSafe
      )

      resultDatabaseIds[catalogKey.catalogDescriptor] = selectChanCatalogDatabaseId(
        siteKey = catalogKey.siteKey,
        boardCode = catalogKey.boardCode
      )
    }

    return resultDatabaseIds
  }

  @Update(onConflict = OnConflictStrategy.IGNORE)
  abstract fun updateChanCatalogEntityList(chanCatalogEntityList: List<ChanCatalogEntity>)

  @Transaction
  open suspend fun replaceChanCatalogSortOrderEntityList(
    chanCatalogSortOrderEntityList: List<ChanCatalogSortOrderEntity>
  ) {
    val databaseIdsToDelete = chanCatalogSortOrderEntityList.map { it.ownerDatabaseId }
    clearChanCatalogSortOrderEntities(databaseIdsToDelete)

    insertManyChanCatalogSortOrderEntity(chanCatalogSortOrderEntityList)
  }

  @Query("""
    INSERT INTO chan_catalogs(
        catalog_site_key,
        catalog_board_code,
        database_id,
        board_title,
        board_description,
        work_safe
    )
    VALUES(
        :siteKey,
        :boardCode,
        (SELECT IFNULL(MAX(database_id), 0) + 1 FROM chan_catalogs),
        :boardTitle,
        :boardDescription,
        :workSafe
    )
  """)
  protected abstract suspend fun insertChanCatalogEntity(
    siteKey: String,
    boardCode: String,
    boardTitle: String?,
    boardDescription: String?,
    workSafe: Boolean
  )

  @Query("""
    SELECT database_id
    FROM chan_catalogs
    WHERE
        chan_catalogs.catalog_site_key = :siteKey
    AND
        chan_catalogs.catalog_board_code = :boardCode
    LIMIT 1
  """)
  protected abstract suspend fun selectChanCatalogDatabaseId(
    siteKey: String,
    boardCode: String
  ): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertManyChanCatalogSortOrderEntity(chanCatalogSortOrderEntityList: List<ChanCatalogSortOrderEntity>)

  @Query("DELETE FROM chan_catalog_sort_order WHERE owner_database_id IN (:databaseIdsToDelete)")
  protected abstract suspend fun clearChanCatalogSortOrderEntities(databaseIdsToDelete: List<Long>)

}