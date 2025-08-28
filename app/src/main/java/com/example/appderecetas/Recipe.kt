package com.example.appderecetas.model

data class Recipe(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val ingredients: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val cookingTimeMinutes: Int = 0,
    val servings: Int = 1,
    val difficulty: Difficulty = Difficulty.FACIL,
    val imageUrl: String = "",
    val isFavorite: Boolean = false,
    val userId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Constructor sin parámetros para Firebase
    constructor() : this(
        id = "",
        name = "",
        description = "",
        ingredients = emptyList(),
        instructions = emptyList(),
        cookingTimeMinutes = 0,
        servings = 1,
        difficulty = Difficulty.FACIL,
        imageUrl = "",
        isFavorite = false,
        userId = "",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}

enum class Difficulty(val displayName: String) {
    FACIL("Fácil"),
    INTERMEDIO("Medio"),
    DIFICIL("Difícil")
}
