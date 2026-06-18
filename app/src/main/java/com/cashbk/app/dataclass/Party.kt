package com.cashbk.app.dataclass

data class Party(
    var id: String = "",
    val name: String = "",
    val role: String = "VENDOR", // VENDOR, PARTNER, CUSTOMER, etc.
    val contact: String = "",
    val email: String = "",
    val website: String = "",
    val priorityTag: String = "", // e.g., INTERNAL, TECH, PRIMARY
    val colorHex: String? = null, // Dynamically loaded default in UI
    val iconResName: String = "ic_party_person" // Default icon
)
