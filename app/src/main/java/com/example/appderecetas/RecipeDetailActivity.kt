package com.example.appderecetas

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
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
                onFailure = { exception ->
                    Toast.makeText(this@RecipeDetailActivity, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            )
        }
    }

    private fun displayRecipe(recipe: Recipe) {
        binding.apply {
            tvRecipeName.text = recipe.name
            tvRecipeDescription.text = recipe.description
            tvCookingTime.text = recipe.cookingTimeMinutes.toString()
            tvServings.text = recipe.servings.toString()
            tvDifficulty.text = recipe.difficulty.displayName

            // Configurar botón favorito
            updateFavoriteButton(recipe.isFavorite)

            // Configurar lista de ingredientes
            val ingredientsAdapter = ArrayAdapter(
                this@RecipeDetailActivity,
                android.R.layout.simple_list_item_1,
                recipe.ingredients
            )
            lvIngredients.adapter = ingredientsAdapter

            // Configurar lista de instrucciones
            val instructionsAdapter = ArrayAdapter(
                this@RecipeDetailActivity,
                android.R.layout.simple_list_item_1,
                recipe.instructions
            )
            lvInstructions.adapter = instructionsAdapter

            // Actualizar título del toolbar
            supportActionBar?.title = recipe.name
        }
    }

    private fun toggleFavorite(recipe: Recipe) {
        lifecycleScope.launch {
            repository.toggleFavorite(recipe.id).fold(
                onSuccess = { isFavorite ->
                    currentRecipe = recipe.copy(isFavorite = isFavorite)
                    updateFavoriteButton(isFavorite)

                    val message = if (isFavorite) "Agregado a favoritos" else "Removido de favoritos"
                    Toast.makeText(this@RecipeDetailActivity, message, Toast.LENGTH_SHORT).show()
                },
                onFailure = { exception ->
                    Toast.makeText(this@RecipeDetailActivity, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        val icon = if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        binding.fabFavorite.setImageResource(icon)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}