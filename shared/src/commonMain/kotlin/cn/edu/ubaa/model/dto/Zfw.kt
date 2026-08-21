package cn.edu.ubaa.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ZfwValidateResponse(
    val success: Boolean = false,
    @SerialName("inputSms") val inputSms: Boolean = false,
    val message: String? = null,
    val remain: Int? = null,
)
