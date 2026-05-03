package de.adminforge.app

data class Category(
    val name: String,
    val services: List<Service>
)

data class Service(
    val name: String,
    val iconUrl: String,
    val description: String,
    val link: String
)
