package com.braiso22.mylauncher.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppRepository {
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    fun toggleFavorite(packageName: String) {
        _favorites.update { current ->
            if (packageName in current) current - packageName else current + packageName
        }
    }

    fun toggleBlocked(packageName: String) {
        _blocked.update { current ->
            if (packageName in current) current - packageName else current + packageName
        }
    }

    fun isFavorite(packageName: String): Boolean = packageName in _favorites.value
    fun isBlocked(packageName: String): Boolean = packageName in _blocked.value
}

