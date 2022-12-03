package com.github.k1rakishou.kurobaexlite.model.data

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

/**
 * Don't forget to update PostDiffer.postsDiffer() when adding new fields here
 * */
@Immutable
interface IPostData {
  val originalPostOrder: Int
  val postDescriptor: PostDescriptor
  val postSubjectUnparsed: String
  val postCommentUnparsed: String
  val opMark: Boolean
  val sage: Boolean
  val name: String?
  val tripcode: String?
  val posterId: String?
  val countryFlag: PostIcon?
  val boardFlag: PostIcon?
  val timeMs: Long?
  val images: List<IPostImage>?
  val threadRepliesTotal: Int?
  val threadImagesTotal: Int?
  val threadPostersTotal: Int?
  val lastModified: Long?
  val archived: Boolean
  val deleted: Boolean
  val closed: Boolean
  val sticky: PostDataSticky?
  val bumpLimit: Boolean?
  val imageLimit: Boolean?

  val postNo: Long
    get() = postDescriptor.postNo
  val postSubNo: Long
    get() = postDescriptor.postSubNo
  val isOP: Boolean
    get() = postDescriptor.isOP

  fun copy(
    originalPostOrder: Int = this.originalPostOrder,
    postDescriptor: PostDescriptor = this.postDescriptor,
    postSubjectUnparsed: String = this.postSubjectUnparsed,
    postCommentUnparsed: String = this.postCommentUnparsed,
    timeMs: Long? = this.timeMs,
    opMark: Boolean = this.opMark,
    sage: Boolean = this.sage,
    name: String? = this.name,
    tripcode: String? = this.tripcode,
    posterId: String? = this.posterId,
    countryFlag: PostIcon? = this.countryFlag,
    boardFlag: PostIcon? = this.boardFlag,
    images: List<IPostImage>? = this.images,
    threadRepliesTotal: Int? = this.threadRepliesTotal,
    threadImagesTotal: Int? = this.threadImagesTotal,
    threadPostersTotal: Int? = this.threadPostersTotal,
    lastModified: Long? = this.lastModified,
    archived: Boolean = this.archived,
    deleted: Boolean = this.deleted,
    closed: Boolean = this.closed,
    sticky: PostDataSticky? = this.sticky,
    bumpLimit: Boolean? = this.bumpLimit,
    imageLimit: Boolean? = this.imageLimit,
  ): IPostData
}

data class PostDataSticky(val maxCapacity: Int?) {
  fun toPostCellDataSticky(): PostCellData.Sticky {
    return PostCellData.Sticky(maxCapacity = maxCapacity)
  }
}

@Immutable
sealed interface PostIcon {
  @Immutable
  data class CountryFlag(
    val flagId: String,
    val flagName: String?
  ) : PostIcon

  @Immutable
  data class BoardFlag(
    val flagId: String,
    val flagName: String?
  ) : PostIcon
}