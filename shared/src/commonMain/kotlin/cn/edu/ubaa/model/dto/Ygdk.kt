package cn.edu.ubaa.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class YgdkTermSummaryDto(
    val termId: Int? = null,
    val termName: String? = null,
    val termCount: Int = 0,
    val termTarget: Int? = null,
    val weekCount: Int? = null,
    val weekTarget: Int? = null,
    val monthCount: Int? = null,
    val monthTarget: Int? = null,
    val dayCount: Int? = null,
    val goodCount: Int? = null,
)

@Serializable
data class YgdkItemDto(
    val itemId: Int,
    val name: String,
    val type: Int? = null,
    val sort: Int? = null,
)

@Serializable
data class YgdkOverviewResponse(
    val summary: YgdkTermSummaryDto = YgdkTermSummaryDto(),
    val classifyId: Int,
    val classifyName: String,
    val defaultItemId: Int? = null,
    val defaultItemName: String? = null,
    val items: List<YgdkItemDto> = emptyList(),
)

@Serializable
data class YgdkRecordDto(
    val recordId: Int,
    val itemId: Int? = null,
    val itemName: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val place: String? = null,
    val images: List<String> = emptyList(),
    val isOpen: Boolean = false,
    val state: Int? = null,
    val createdAt: String? = null,
    val createdAtLabel: String? = null,
)

@Serializable
data class YgdkRecordsPageResponse(
    val content: List<YgdkRecordDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
    val hasMore: Boolean = false,
)

data class YgdkPhotoUpload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)

data class YgdkClockinSubmitRequest(
    val itemId: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val place: String? = null,
    val shareToSquare: Boolean? = null,
    val photo: YgdkPhotoUpload? = null,
)

@Serializable
data class YgdkClockinSubmitResponse(
    val success: Boolean,
    val message: String,
    val recordId: Int? = null,
    val summary: YgdkTermSummaryDto? = null,
)
