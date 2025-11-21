package com.guc_proj.signaling_proj

data class AddressItem(
    val name: String? = null,
    val city: String? = null,
    val street: String? = null,
    val building: String? = null,
    val floor: String? = null,
    val apartment: String? = null,
    val instructions: String? = null
) {
    fun toFormattedString(): String {
        val sb = StringBuilder()
        // If street/building are missing, handle gracefully
        if (street.isNullOrEmpty()) return ""

        sb.append("$street, Bldg $building")
        if (!floor.isNullOrEmpty()) sb.append(", Floor $floor")
        if (!apartment.isNullOrEmpty()) sb.append(", Apt $apartment")
        sb.append(", $city")
        if (!instructions.isNullOrEmpty()) sb.append("\nNote: $instructions")

        return sb.toString()
    }
}