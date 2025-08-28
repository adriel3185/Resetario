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
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    // Constructor vacío para Firebase (SIN llamadas a System.currentTimeMillis())
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
        createdAt = 0,
        updatedAt = 0
    )

    // Método para convertir a Map (útil para Firebase)
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "description" to description,
            "ingredients" to ingredients,
            "instructions" to instructions,
            "cookingTimeMinutes" to cookingTimeMinutes,
            "servings" to servings,
            "difficulty" to difficulty.name, // Guardar como String
            "imageUrl" to imageUrl,
            "isFavorite" to isFavorite,
            "userId" to userId,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }

    companion object {
        // Método para crear desde Map (útil para Firebase)
        fun fromMap(map: Map<String, Any>): Recipe {
            return Recipe(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                description = map["description"] as? String ?: "",
                ingredients = map["ingredients"] as? List<String> ?: emptyList(),
                instructions = map["instructions"] as? List<String> ?: emptyList(),
                cookingTimeMinutes = (map["cookingTimeMinutes"] as? Long)?.toInt() ?: 0,
                servings = (map["servings"] as? Long)?.toInt() ?: 1,
                difficulty = try {
                    Difficulty.valueOf(map["difficulty"] as? String ?: "FACIL")
                } catch (e: Exception) {
                    Difficulty.FACIL
                },
                imageUrl = map["imageUrl"] as? String ?: "",
                isFavorite = map["isFavorite"] as? Boolean ?: false,
                userId = map["userId"] as? String ?: "",
                createdAt = map["createdAt"] as? Long ?: 0,
                updatedAt = map["updatedAt"] as? Long ?: 0
            )
        }
    }
}

enum class Difficulty(val displayName: String) {
    FACIL("Fácil"),
    INTERMEDIO("Medio"),
    DIFICIL("Difícil")
}