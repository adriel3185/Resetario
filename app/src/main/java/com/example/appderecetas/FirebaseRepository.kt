package com.example.appderecetas.repository

import com.example.appderecetas.model.Recipe
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val recipesCollection = db.collection("recipes")

    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")
    }

    suspend fun saveRecipe(recipe: Recipe): Result<String> {
        return try {
            val userId = getCurrentUserId()
            val recipeToSave = recipe.copy(
                userId = userId,
                updatedAt = System.currentTimeMillis()
            )

            val documentRef = if (recipe.id.isEmpty()) {
                recipesCollection.document()
            } else {
                recipesCollection.document(recipe.id)
            }

            val finalRecipe = recipeToSave.copy(id = documentRef.id)
            documentRef.set(finalRecipe).await()

            Result.success(documentRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecipeById(recipeId: String): Result<Recipe?> {
        return try {
            val document = recipesCollection.document(recipeId).get().await()
            val recipe = document.toObject(Recipe::class.java)
            Result.success(recipe)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserRecipes(): Result<List<Recipe>> {
        return try {
            val userId = getCurrentUserId()
            val querySnapshot = recipesCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val recipes = querySnapshot.documents.mapNotNull { document ->
                document.toObject(Recipe::class.java)
            }

            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFavoriteRecipes(): Result<List<Recipe>> {
        return try {
            val userId = getCurrentUserId()
            val querySnapshot = recipesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isFavorite", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val recipes = querySnapshot.documents.mapNotNull { document ->
                document.toObject(Recipe::class.java)
            }

            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(recipeId: String): Result<Boolean> {
        return try {
            val document = recipesCollection.document(recipeId).get().await()
            val recipe = document.toObject(Recipe::class.java)

            if (recipe != null && recipe.userId == getCurrentUserId()) {
                val newFavoriteStatus = !recipe.isFavorite
                recipesCollection.document(recipeId)
                    .update(
                        mapOf(
                            "isFavorite" to newFavoriteStatus,
                            "updatedAt" to System.currentTimeMillis()
                        )
                    ).await()

                Result.success(newFavoriteStatus)
            } else {
                Result.failure(Exception("Receta no encontrada o sin permisos"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRecipe(recipeId: String): Result<Unit> {
        return try {
            val document = recipesCollection.document(recipeId).get().await()
            val recipe = document.toObject(Recipe::class.java)

            if (recipe != null && recipe.userId == getCurrentUserId()) {
                recipesCollection.document(recipeId).delete().await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Receta no encontrada o sin permisos"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchRecipes(query: String): Result<List<Recipe>> {
        return try {
            val userId = getCurrentUserId()
            val querySnapshot = recipesCollection
                .whereEqualTo("userId", userId)
                .orderBy("name")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .get()
                .await()

            val recipes = querySnapshot.documents.mapNotNull { document ->
                document.toObject(Recipe::class.java)
            }

            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}