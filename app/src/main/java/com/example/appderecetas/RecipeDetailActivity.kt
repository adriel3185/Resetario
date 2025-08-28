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
                onFailure = { e ->
                    Toast.makeText(this@RecipeDetailActivity, "Error al cargar: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            )
        }
    }

    private fun displayRecipe(recipe: Recipe) {
        binding.apply {
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
        }
    }

    private fun toggleFavorite(recipe: Recipe) {
        val updatedRecipe = recipe.copy(isFavorite = !recipe.isFavorite)
        lifecycleScope.launch {
            repository.updateRecipe(updatedRecipe).fold(
                onSuccess = {
                    currentRecipe = updatedRecipe
                    displayRecipe(updatedRecipe)
                    val msg = if (updatedRecipe.isFavorite) "Añadido a favoritos" else "Eliminado de favoritos"
                    Toast.makeText(this@RecipeDetailActivity, msg, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(this@RecipeDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun deleteRecipe(recipe: Recipe) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar receta")
            .setMessage("¿Seguro que deseas eliminar esta receta?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteRecipe(recipe.id).fold(
                        onSuccess = {
                            Toast.makeText(this@RecipeDetailActivity, "Receta eliminada", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        },
                        onFailure = { e ->
                            Toast.makeText(this@RecipeDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Cancelar", null)
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
