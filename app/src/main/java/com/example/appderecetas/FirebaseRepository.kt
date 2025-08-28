package com.example.appderecetas.repository

import android.util.Log
import com.example.appderecetas.model.Difficulty
import com.example.appderecetas.model.Recipe
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeoutException

class FirebaseRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val recipesCollection = db.collection("recipes")

    companion object {
        private const val TAG = "FirebaseRepository"
        private const val FIRESTORE_TIMEOUT = 10000L // 10 segundos
    }

    init {
        // Configurar Firestore para mejor rendimiento
        try {
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            db.firestoreSettings = settings
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo configurar persistencia de Firestore", e)
        }
    }

    private fun getCurrentUserId(): String? {
        val userId = auth.currentUser?.uid
        Log.d(TAG, "Usuario actual ID: ${userId ?: "null"}")
        return userId
    }

    private suspend fun <T> withFirestoreTimeout(operation: suspend () -> T): T {
        return try {
            withTimeout(FIRESTORE_TIMEOUT) {
                operation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Timeout o error en operación Firestore", e)
            throw when {
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    TimeoutException("Operación de Firebase agotó el tiempo de espera")
                else -> e
            }
        }
    }

    // Método de test para verificar conectividad
    suspend fun testConnection(): Result<Boolean> {
        return try {
            Log.d(TAG, "=== TESTING FIREBASE CONNECTION ===")
            val userId = getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                return Result.failure(IllegalStateException("Usuario no autenticado para test"))
            }

            val testData = mapOf(
                "test" to "connection",
                "timestamp" to System.currentTimeMillis(),
                "userId" to userId
            )

            withFirestoreTimeout {
                val docRef = db.collection("test").add(testData).await()
                Log.d(TAG, "Test de conexión exitoso: ${docRef.id}")

                // Eliminar el documento de test
                docRef.delete().await()
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Test de conexión falló", e)
            Result.failure(e)
        }
    }

    suspend fun saveRecipe(recipe: Recipe): Result<String> {
        return try {
            Log.d(TAG, "=== INICIANDO GUARDADO DE RECETA ===")
            Log.d(TAG, "Nombre: ${recipe.name}")
            Log.d(TAG, "Ingredientes: ${recipe.ingredients.size}")
            Log.d(TAG, "Instrucciones: ${recipe.instructions.size}")

            val userId = getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "ERROR: Usuario no autenticado")
                return Result.failure(IllegalStateException("Usuario no autenticado. Por favor, inicia sesión nuevamente."))
            }

            Log.d(TAG, "Usuario autenticado: $userId")

            val currentTime = System.currentTimeMillis()
            val recipeToSave = recipe.copy(
                userId = userId,
                createdAt = if (recipe.id.isEmpty()) currentTime else recipe.createdAt,
                updatedAt = currentTime
            )

            Log.d(TAG, "Receta preparada para guardado:")
            Log.d(TAG, "- UserID: ${recipeToSave.userId}")
            Log.d(TAG, "- CreatedAt: ${recipeToSave.createdAt}")
            Log.d(TAG, "- UpdatedAt: ${recipeToSave.updatedAt}")

            val result = withFirestoreTimeout {
                val documentRef = if (recipe.id.isEmpty()) {
                    Log.d(TAG, "Creando nuevo documento...")
                    recipesCollection.document()
                } else {
                    Log.d(TAG, "Actualizando documento existente: ${recipe.id}")
                    recipesCollection.document(recipe.id)
                }

                val finalRecipe = recipeToSave.copy(id = documentRef.id)
                Log.d(TAG, "ID final del documento: ${documentRef.id}")

                // Usar toMap() para mejor compatibilidad con Firebase
                val recipeMap = finalRecipe.toMap()
                Log.d(TAG, "Mapa de datos preparado, iniciando set()...")

                documentRef.set(recipeMap).await()
                Log.d(TAG, "Operación set() completada exitosamente")

                documentRef.id
            }

            Log.d(TAG, "=== RECETA GUARDADA EXITOSAMENTE ===")
            Log.d(TAG, "ID final: $result")
            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "=== ERROR AL GUARDAR RECETA ===", e)
            Log.e(TAG, "Tipo de excepción: ${e.javaClass.simpleName}")
            Log.e(TAG, "Mensaje: ${e.message}")
            Log.e(TAG, "Causa: ${e.cause?.message}")

            val errorMessage = when {
                e is TimeoutException -> "Tiempo de espera agotado. Verifica tu conexión a internet."
                e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ->
                    "Sin permisos para guardar. Verifica las reglas de Firestore o contacta al administrador."
                e.message?.contains("UNAVAILABLE", ignoreCase = true) == true ->
                    "Servicio no disponible temporalmente. Intenta más tarde."
                e.message?.contains("network", ignoreCase = true) == true ||
                        e.message?.contains("connectivity", ignoreCase = true) == true ->
                    "Error de conexión. Verifica tu internet y vuelve a intentar."
                else -> "Error al guardar: ${e.message ?: "Error desconocido"}"
            }

            Result.failure(Exception(errorMessage, e))
        }
    }

    suspend fun getRecipeById(recipeId: String): Result<Recipe?> {
        return try {
            Log.d(TAG, "Obteniendo receta por ID: $recipeId")

            val userId = getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                return Result.failure(IllegalStateException("Usuario no autenticado"))
            }

            val recipe = withFirestoreTimeout {
                val document = recipesCollection.document(recipeId).get().await()
                if (document.exists()) {
                    val data = document.data
                    if (data != null) {
                        val recipe = Recipe.fromMap(data)
                        // Verificar que la receta pertenece al usuario actual
                        if (recipe.userId == userId) {
                            recipe
                        } else {
                            null // No tiene permisos
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

            Log.d(TAG, "Receta obtenida: ${recipe?.name ?: "null"}")
            Result.success(recipe)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener receta por ID: $recipeId", e)
            Result.failure(e)
        }
    }

    suspend fun getUserRecipes(): Result<List<Recipe>> {
        return try {
            val userId = getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "Usuario no autenticado para obtener recetas")
                return Result.failure(IllegalStateException("Usuario no autenticado"))
            }

            Log.d(TAG, "Obteniendo recetas del usuario: $userId")

            val recipes = withFirestoreTimeout {
                val querySnapshot = recipesCollection
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

                querySnapshot.documents.mapNotNull { document ->
                    try {
                        val data = document.data
                        if (data != null) {
                            Recipe.fromMap(data)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al convertir documento a Recipe: ${document.id}", e)
                        null
                    }
                }
            }

            Log.d(TAG, "Recetas obtenidas: ${recipes.size}")
            Result.success(recipes)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener recetas del usuario", e)
            Result.failure(e)
        }
    }

    suspend fun getFavoriteRecipes(): Result<List<Recipe>> {
        return try {
            val userId = getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                return Result.failure(IllegalStateException("Usuario no autenticado"))
            }

            Log.d(TAG, "Obteniendo recetas favoritas del usuario: $userId")

            val recipes = withFirestoreTimeout {
                val querySnapshot = recipesCollection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("isFavorite", true)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

                querySnapshot.documents.mapNotNull { document ->
                    try {
                        val data = document.data
                        if (data != null) {
                            Recipe.fromMap(data)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al convertir documento favorito a Recipe: ${document.id}", e)
                        null
                    }
                }
            }

            Log.d(TAG, "Recetas favoritas obtenidas: ${recipes.size}")
            Result.success(recipes)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener recetas favoritas", e)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(recipeId: String): Result<Boolean> {
        return try {
            val userId = getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                return Result.failure(IllegalStateException("Usuario no autenticado"))
            }

            Log.d(TAG, "Cambiando estado favorito de receta: $recipeId")

            val newFavoriteStatus = withFirestoreTimeout {
                val document = recipesCollection.document(recipeId).get().await()
                val data = document.data

                if (data != null) {
                    val recipe = Recipe.fromMap(data)
                    if (recipe.userId == userId) {
                        val newStatus = !recipe.isFavorite
                        recipesCollection.document(recipeId)
                            .update(
                                mapOf(
                                    "isFavorite" to newStatus,
                                    "updatedAt" to System.currentTimeMillis()
                                )
                            ).await()
                        newStatus
                    } else {
                        throw Exception("Sin permisos para modificar esta receta")
                    }
                } else {
                    throw Exception("Receta no encontrada")
                }
            }

            Log.d(TAG, "Estado favorito actualizado a: $newFavoriteStatus")
            Result.success(newFavoriteStatus)
        } catch (e: Exception) {
            Log.e(TAG, "Error al cambiar estado favorito", e)
            Result.failure(e)
        }
    }

    suspend fun deleteRecipe(recipeId: String): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                return Result.failure(IllegalStateException("Usuario no autenticado"))
            }

            Log.d(TAG, "Eliminando receta: $recipeId")

            withFirestoreTimeout {
                val document = recipesCollection.document(recipeId).get().await()
                val data = document.data

                if (data != null) {
                    val recipe = Recipe.fromMap(data)
                    if (recipe.userId == userId) {
                        recipesCollection.document(recipeId).delete().await()
                        Log.d(TAG, "Receta eliminada exitosamente")
                    } else {
                        throw Exception("Sin permisos para eliminar esta receta")
                    }
                } else {
                    throw Exception("Receta no encontrada")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar receta", e)
            Result.failure(e)
        }
    }

    suspend fun searchRecipes(query: String): Result<List<Recipe>> {
        return try {
            val userId = getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                return Result.failure(IllegalStateException("Usuario no autenticado"))
            }

            Log.d(TAG, "Buscando recetas con query: $query")

            val recipes = withFirestoreTimeout {
                // Obtener todas las recetas del usuario y filtrar localmente
                // (Firestore tiene limitaciones con búsqueda de texto)
                val querySnapshot = recipesCollection
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                querySnapshot.documents.mapNotNull { document ->
                    try {
                        val data = document.data
                        if (data != null) {
                            val recipe = Recipe.fromMap(data)
                            // Filtrar por nombre que contenga el query (case insensitive)
                            if (recipe.name.contains(query, ignoreCase = true) ||
                                recipe.description.contains(query, ignoreCase = true)) {
                                recipe
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al convertir documento de búsqueda a Recipe: ${document.id}", e)
                        null
                    }
                }
            }

            Log.d(TAG, "Recetas encontradas en búsqueda: ${recipes.size}")
            Result.success(recipes)
        } catch (e: Exception) {
            Log.e(TAG, "Error en búsqueda de recetas", e)
            Result.failure(e)
        }
    }

    suspend fun updateRecipe(recipe: Recipe): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            if (userId.isNullOrEmpty()) {
                return Result.failure(IllegalStateException("Usuario no autenticado"))
            }

            Log.d(TAG, "Actualizando receta: ${recipe.id}")

            withFirestoreTimeout {
                val document = recipesCollection.document(recipe.id).get().await()
                val data = document.data

                if (data != null) {
                    val existingRecipe = Recipe.fromMap(data)
                    if (existingRecipe.userId == userId) {
                        val updatedRecipe = recipe.copy(updatedAt = System.currentTimeMillis())
                        recipesCollection.document(recipe.id).set(updatedRecipe.toMap()).await()
                        Log.d(TAG, "Receta actualizada exitosamente")
                    } else {
                        throw Exception("Sin permisos para actualizar esta receta")
                    }
                } else {
                    throw Exception("Receta no encontrada")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar receta", e)
            Result.failure(e)
        }
    }
}