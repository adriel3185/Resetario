package com.example.appderecetas

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
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
        private const val EDIT_RECIPE_REQUEST_CODE = 2001
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

    override fun onResume() {
        super.onResume()
        // Recargar los detalles cuando regresemos de editar
        currentRecipe?.let {
            loadRecipeDetails(it.id)
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
            tvServings.text = "${recipe.servings}"

            // 🔥 SOLUCIÓN: Reemplazar ListView con LinearLayout dinámico
            populateIngredientsList(recipe.ingredients)
            populateInstructionsList(recipe.instructions)

            // Icono del botón de favorito
            val favoriteIcon = if (recipe.isFavorite) {
                R.drawable.ic_favorite
            } else {
                R.drawable.ic_favorite_border
            }
            fabFavorite.setImageResource(favoriteIcon)
        }
    }

    // 🔥 NUEVA FUNCIÓN: Poblar lista de ingredientes dinámicamente
    private fun populateInstructionsList(instructions: List<String>) {
        binding.llInstructionsContainer.removeAllViews()

        instructions.forEachIndexed { index, instruction ->
            val textView = TextView(this).apply {
                text = "${index + 1}. $instruction"
                textSize = 16f
                setPadding(0, 12, 0, 12)
                setTextColor(getColor(R.color.text_primary))
                setLineSpacing(4f, 1.1f) // ✅ corrección aquí

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
            }
            binding.llInstructionsContainer.addView(textView)

            if (index < instructions.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        topMargin = 8
                        bottomMargin = 8
                    }
                    setBackgroundColor(getColor(R.color.divider_color))
                }
                binding.llInstructionsContainer.addView(divider)
            }
        }
    }

    // 🔥 NUEVA FUNCIÓN: Poblar lista de instrucciones dinámicamente


    private fun toggleFavorite(recipe: Recipe) {
        lifecycleScope.launch {
            repository.toggleFavorite(recipe.id).fold(
                onSuccess = { isFavorite ->
                    currentRecipe = recipe.copy(isFavorite = isFavorite)
                    currentRecipe?.let { displayRecipe(it) }

                    val msg = if (isFavorite) "Añadido a favoritos" else "Eliminado de favoritos"
                    Toast.makeText(this@RecipeDetailActivity, msg, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(this@RecipeDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
    private fun populateIngredientsList(ingredients: List<String>) {
        binding.llIngredientsContainer.removeAllViews()

        ingredients.forEachIndexed { index, ingredient ->
            val textView = TextView(this).apply {
                text = "• $ingredient"
                textSize = 16f
                setPadding(0, 12, 0, 12)
                setTextColor(getColor(R.color.text_primary))

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
            }
            binding.llIngredientsContainer.addView(textView)

            if (index < ingredients.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        topMargin = 8
                        bottomMargin = 8
                    }
                    setBackgroundColor(getColor(R.color.divider_color))
                }
                binding.llIngredientsContainer.addView(divider)
            }
        }
    }
    private fun editRecipe(recipe: Recipe) {
        val intent = Intent(this, EditRecipeActivity::class.java).apply {
            putExtra(EditRecipeActivity.EXTRA_RECIPE_ID, recipe.id)
        }
        startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE)
    }

    private fun shareRecipe(recipe: Recipe) {
        val shareText = buildString {
            append("🍽️ ${recipe.name}\n\n")
            append("📝 ${recipe.description}\n\n")
            append("⏱️ Tiempo: ${recipe.cookingTimeMinutes} min\n")
            append("👥 Porciones: ${recipe.servings}\n")
            append("📊 Dificultad: ${recipe.difficulty.displayName}\n\n")

            append("📋 INGREDIENTES:\n")
            recipe.ingredients.forEachIndexed { index, ingredient ->
                append("${index + 1}. $ingredient\n")
            }

            append("\n👩‍🍳 INSTRUCCIONES:\n")
            recipe.instructions.forEach { instruction ->
                append("$instruction\n")
            }

            append("\n¡Compartido desde App de Recetas! 📱")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Receta: ${recipe.name}")
        }

        startActivity(Intent.createChooser(shareIntent, "Compartir receta"))
    }

    private fun deleteRecipe(recipe: Recipe) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar receta")
            .setMessage("¿Seguro que deseas eliminar \"${recipe.name}\"?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteRecipe(recipe.id).fold(
                        onSuccess = {
                            Toast.makeText(this@RecipeDetailActivity, "\"${recipe.name}\" eliminada", Toast.LENGTH_SHORT).show()
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
            .setIcon(R.drawable.ic_delete)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_RECIPE_REQUEST_CODE && resultCode == RESULT_OK) {
            // Recargar la receta después de editarla
            currentRecipe?.let {
                loadRecipeDetails(it.id)
            }
        }
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
            R.id.action_edit -> {
                currentRecipe?.let { editRecipe(it) }
                true
            }
            R.id.action_share -> {
                currentRecipe?.let { shareRecipe(it) }
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