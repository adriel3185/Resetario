package com.example.appderecetas

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.appderecetas.databinding.ActivityRecipeDetailBinding
import com.example.appderecetas.model.Recipe
import com.example.appderecetas.repository.FirebaseRepository
import kotlinx.coroutines.launch

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailBinding
    private lateinit var repository: FirebaseRepository
    private var currentRecipe: Recipe? = null

    companion object {
        const val EXTRA_RECIPE_ID = "extra_recipe_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()

        setupToolbar()
        setupClickListeners()

        val recipeId = intent.getStringExtra(EXTRA_RECIPE_ID)
        if (recipeId != null) {
            loadRecipeDetails(recipeId)
        } else {
            Toast.makeText(this, "Error: Receta no encontrada", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Detalles de Receta"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupClickListeners() {
        binding.fabFavorite.setOnClickListener {
            currentRecipe?.let { recipe ->
                toggleFavorite(recipe)
            }
        }
    }

    private fun loadRecipeDetails(recipeId: String) {
        lifecycleScope.launch {
            try {
                repository.getRecipeById(recipeId).fold(
                    onSuccess = { recipe ->
                        if (recipe != null) {
                            currentRecipe = recipe
                            displayRecipe(recipe)
                        } else {
                            Toast.makeText(this@RecipeDetailActivity, "Receta no encontrada", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    },
                    onFailure = { exception ->
                        Toast.makeText(this@RecipeDetailActivity, "Error al cargar: ${exception.message}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@RecipeDetailActivity, "Error inesperado: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun displayRecipe(recipe: Recipe) {
        binding.apply {
            // Información básica
            tvRecipeName.text = recipe.name
            tvDescription.text = recipe.description
            tvDifficulty.text = recipe.difficulty.displayName
            tvCookingTime.text = "${recipe.cookingTimeMinutes} min"
            tvServings.text = "${recipe.servings} porciones"

            // Lista de ingredientes
            val ingredientsAdapter = ArrayAdapter(
                this@RecipeDetailActivity,
                android.R.layout.simple_list_item_1,
                recipe.ingredients
            )
            lvIngredients.adapter = ingredientsAdapter

            // Lista de instrucciones
            val instructionsAdapter = ArrayAdapter(
                this@RecipeDetailActivity,
                android.R.layout.simple_list_item_1,
                recipe.instructions
            )
            lvInstructions.adapter = instructionsAdapter

            // Icono del botón de favorito
            val favoriteIcon = if (recipe.isFavorite) {
                R.drawable.ic_favorite
            } else {
                R.drawable.ic_favorite_border
            }
            fabFavorite.setImageResource(favoriteIcon)

            // Actualizar título de la toolbar
            supportActionBar?.title = recipe.name
        }
    }

    private fun toggleFavorite(recipe: Recipe) {
        lifecycleScope.launch {
            try {
                repository.toggleFavorite(recipe.id).fold(
                    onSuccess = { isFavorite ->
                        // Actualizar la receta local
                        val updatedRecipe = recipe.copy(isFavorite = isFavorite)
                        currentRecipe = updatedRecipe

                        // Actualizar la UI
                        val favoriteIcon = if (isFavorite) {
                            R.drawable.ic_favorite
                        } else {
                            R.drawable.ic_favorite_border
                        }
                        binding.fabFavorite.setImageResource(favoriteIcon)

                        val message = if (isFavorite) "Agregado a favoritos" else "Removido de favoritos"
                        Toast.makeText(this@RecipeDetailActivity, message, Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { exception ->
                        Toast.makeText(
                            this@RecipeDetailActivity,
                            "Error: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@RecipeDetailActivity,
                    "Error inesperado: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun deleteRecipe(recipe: Recipe) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar receta")
            .setMessage("¿Estás seguro de que quieres eliminar \"${recipe.name}\"?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        repository.deleteRecipe(recipe.id).fold(
                            onSuccess = {
                                Toast.makeText(
                                    this@RecipeDetailActivity,
                                    "\"${recipe.name}\" eliminada correctamente",
                                    Toast.LENGTH_SHORT
                                ).show()
                                setResult(RESULT_OK)
                                finish()
                            },
                            onFailure = { exception ->
                                Toast.makeText(
                                    this@RecipeDetailActivity,
                                    "Error al eliminar: ${exception.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@RecipeDetailActivity,
                            "Error inesperado: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .setIcon(R.drawable.ic_delete)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_recipe_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_delete -> {
                currentRecipe?.let { deleteRecipe(it) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
