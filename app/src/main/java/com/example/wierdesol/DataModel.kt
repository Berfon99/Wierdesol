package com.example.wierdesol

import com.google.gson.annotations.SerializedName

data class ResolResponse(
    @SerializedName("headerset_stats") val headersetStats: HeaderSetStats,
    @SerializedName("headersets") val headersets: List<HeaderSet>
)

data class HeaderSetStats(
    @SerializedName("headerset_count") val headersetCount: Int,
    @SerializedName("min_timestamp") val minTimestamp: Double,
    @SerializedName("max_timestamp") val maxTimestamp: Double
)

data class HeaderSet(
    @SerializedName("timestamp") val timestamp: Double,
    @SerializedName("packets") val packets: List<Packet>
)

data class Packet(
    @SerializedName("header_index") val headerIndex: Int,
    @SerializedName("timestamp") val timestamp: Double,
    @SerializedName("field_values") val fieldValues: List<FieldValue>
)

data class FieldValue(
    @SerializedName("field_index") val fieldIndex: Int,
    @SerializedName("raw_value") val rawValue: Double,
    @SerializedName("value") val value: String
)
