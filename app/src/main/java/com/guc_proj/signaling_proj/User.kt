package com.guc_proj.signaling_proj

data class User(
    val name: String? = null,
    val phone: String? = null,
    //val address: String? = null,
    val email: String? = null,
    val role: String? = null,
    val photoUrl: String? = null,
    val credit: Double = 0.0 // Wallet/Rewards Balance
)