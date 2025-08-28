package com.example.appderecetas.repository

import android.util.Log
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
        private const val FIRESTORE_TIMEOUT = 20000L // 20 segundos
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

            // Verificar conectividad básica con Firestore
            Log.d(TAG, "Verificando conectividad con Firestore...")

            val currentTime = System.currentTimeMillis()
            val recipeToSave = recipe.copy(
                userId = userId,
                createdAt = if (recipe.id.isEmpty()) currentTime else recipe.createdAt,
                updatedAt = currentTime
            )

            Log.d(TAG, "Receta preparada para guardado:")
            Log.d(TAG, "- ID: ${recipeToSave.id}")
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

                Log.d(TAG, "Iniciando operación set() en Firestore...")
                documentRef.set(finalRecipe).await()
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
                    "Sin permisos para guardar. Verifica las reglas de Firestore."
                e.message?.contains("UNAVAILABLE", ignoreCase = true) == true ->
                    "Servicio no disponible. Intenta más tarde."
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Error de red. Verifica tu conexión a internet."
                else -> e.message ?: "Error desconocido al guardar la receta"
            }

            Result.failure(Exception(errorMessage, e))
        }
    }

    suspend fun getRecipeById(recipeId: String): Result<Recipe?> {
        return try {
            Log.d(TAG, "Obteniendo receta por ID: $recipeId")

            val recipe = withFirestoreTimeout {
                val document = recipesCollection.document(recipeId).get().await()
                document.toObject(Recipe::class.java)
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
                        document.toObject(Recipe::class.java)
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
                        document.toObject(Recipe::class.java)
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
                val recipe = document.toObject(Recipe::class.java)

                if (recipe != null && recipe.userId == userId) {
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
                    throw Exception("Receta no encontrada o sin permisos")
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
                val recipe = document.toObject(Recipe::class.java)

                if (recipe != null && recipe.userId == userId) {
                    recipesCollection.document(recipeId).delete().await()
                    Log.d(TAG, "Receta eliminada exitosamente")
                } else {
                    throw Exception("Receta no encontrada o sin permisos")
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
                val querySnapshot = recipesCollection
                    .whereEqualTo("userId", userId)
                    .orderBy("name")
                    .startAt(query)
                    .endAt(query + "\uf8ff")
                    .get()
                    .await()

                querySnapshot.documents.mapNotNull { document ->
                    try {
                        document.toObject(Recipe::class.java)
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
}