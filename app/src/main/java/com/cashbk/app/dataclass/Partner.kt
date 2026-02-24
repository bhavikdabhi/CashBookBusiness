package com.cashbk.app.dataclass

data class Partner(
    var id: String = "",
    val name: String = "",
    val phone: String = "", // The phone number of the partner, used for inviting
    val uid: String? = null, // The Firebase UID of the partner, if they have joined
    val businessId: String = "",
    val role: String = "partner", // e.g., "partner", "viewer"
    val addedAt: Long = 0
)
