package com.braiso22.mylauncher.domain

data class App(
    val name: Name,
    val packageName: PackageName,
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false,
)

@JvmInline
value class Name(val value: String) {
    init {
        require(value.isNotBlank()) { "Name cannot be blank" }
    }
}

@JvmInline
value class PackageName(val value: String) {
    init {
        require(value.isNotBlank()) { "Package name cannot be blank" }
    }
}