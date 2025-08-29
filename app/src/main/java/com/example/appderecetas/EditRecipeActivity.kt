package com.example.appderecetas

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.appderecetas.databinding.ActivityEditRecipeBinding
import com.example.appderecetas.model.Difficulty
import com.example.appderecetas.model.Recipe
import com.example.appderecetas.repository.FirebaseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditRecipeBinding
    private lateinit var repository: FirebaseRepository
    private val ingredientsList = mutableListOf<String>()
    private val instructionsList = mutableListOf<String>()
    private lateinit var ingredientsAdapter: ArrayAdapter<String>
    private lateinit var instructionsAdapter: ArrayAdapter<String>
    private var currentRecipe: Recipe? = null

    companion object {
        const val EXTRA_RECIPE_ID = "extra_recipe_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()

        setupToolbar()
        setupDifficultyDropdown()
        setupListViews()
        setupClickListeners()

        // Cargar la receta a editar
        val recipeId = intent.getStringExtra(EXTRA_RECIPE_ID)
        if (recipeId != null) {
            loadRecipeForEditing(recipeId)
        } else {
            Toast.makeText(this, "Error: Receta no encontrada", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Editar Receta"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupDifficultyDropdown() {
        val difficulties = Difficulty.values().map { it.displayName }
        val difficultyAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            difficulties
        )
        binding.spinnerDifficulty.setAdapter(difficultyAdapter)
    }

    private fun setupListViews() {
        ingredientsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ingredientsList)
        binding.lvIngredients.adapter = ingredientsAdapter

        instructionsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, instructionsList)
        binding.lvInstructions.adapter = instructionsAdapter
    }

    private fun setupClickListeners() {
        binding.btnAddIngredient.setOnClickListener { addIngredient() }
        binding.btnAddInstruction.setOnClickListener { addInstruction() }
        binding.btnUpdateRecipe.setOnClickListener { updateRecipe() }

        binding.lvIngredients.setOnItemLongClickListener { _, _, position, _ ->
            ingredientsList.removeAt(position)
            ingredientsAdapter.notifyDataSetChanged()
            true
        }

        binding.lvInstructions.setOnItemLongClickListener { _, _, position, _ ->
            instructionsList.removeAt(position)
            instructionsAdapter.notifyDataSetChanged()
            true
        }
    }

    private fun loadRecipeForEditing(recipeId: String) {
        lifecycleScope.launch {
            repository.getRecipeById(recipeId).fold(
                onSuccess = { recipe ->
                    if (recipe != null) {
                        currentRecipe = recipe
                        populateFields(recipe)
                    } else {
                        Toast.makeText(this@EditRecipeActivity, "Receta no encontrada", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                },
                onFailure = { e ->
                    Toast.makeText(this@EditRecipeActivity, "Error al cargar: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            )
        }
    }

    private fun populateFields(recipe: Recipe) {
        binding.apply {
            etRecipeName.setText(recipe.name)
            etDescription.setText(recipe.description)
            etCookingTime.setText(recipe.cookingTimeMinutes.toString())
            etServings.setText(recipe.servings.toString())

            // Establecer dificultad
            spinnerDifficulty.setText(recipe.difficulty.displayName, false)

            // Cargar ingredientes
            ingredientsList.clear()
            ingredientsList.addAll(recipe.ingredients)
            ingredientsAdapter.notifyDataSetChanged()

            // Cargar instrucciones
            instructionsList.clear()
            instructionsList.addAll(recipe.instructions)
            instructionsAdapter.notifyDataSetChanged()
        }
    }

    private fun addIngredient() {
        val ingredient = binding.etIngredient.text.toString().trim()
        if (ingredient.isNotEmpty()) {
            ingredientsList.add(ingredient)
            ingredientsAdapter.notifyDataSetChanged()
            binding.etIngredient.setText("")
        } else {
            Toast.makeText(this, "Ingresa un ingrediente", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addInstruction() {
        val instruction = binding.etInstruction.text.toString().trim()
        if (instruction.isNotEmpty()) {
            val stepNumber = instructionsList.size + 1
            instructionsList.add("$stepNumber. $instruction")
            instructionsAdapter.notifyDataSetChanged()
            binding.etInstruction.setText("")
        } else {
            Toast.makeText(this, "Ingresa una instrucción", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRecipe() {
        if (!validateForm() || currentRecipe == null) return

        showProgressBar(true, "Actualizando receta...")

        val selectedDifficulty = binding.spinnerDifficulty.text.toString()
        val difficultyEnum = Difficulty.values()
            .firstOrNull { it.displayName == selectedDifficulty }
            ?: Difficulty.FACIL

        val updatedRecipe = currentRecipe!!.copy(
            name = binding.etRecipeName.text.toString().trim(),
            description = binding.etDescription.text.toString().trim(),
            ingredients = ingredientsList.toList(),
            instructions = instructionsList.toList(),
            cookingTimeMinutes = binding.etCookingTime.text.toString().toIntOrNull() ?: 0,
            servings = binding.etServings.text.toString().toIntOrNull() ?: 1,
            difficulty = difficultyEnum,
            updatedAt = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            try {
                repository.updateRecipe(updatedRecipe).fold(
                    onSuccess = {
                        runOnUiThread {
                            showProgressBar(true, "¡Receta actualizada! Regresando...")
                        }

                        delay(2000)

                        runOnUiThread {
                            showProgressBar(false)
                            setResult(RESULT_OK)
                            finish()
                        }
                    },
                    onFailure = { exception ->
                        runOnUiThread {
                            showProgressBar(false)
                            Toast.makeText(
                                this@EditRecipeActivity,
                                "Error al actualizar: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                runOnUiThread {
                    showProgressBar(false)
                    Toast.makeText(
                        this@EditRecipeActivity,
                        "Error inesperado: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        if (binding.etRecipeName.text.toString().trim().isEmpty()) {
            binding.tilRecipeName.error = "El nombre es requerido"
            isValid = false
        } else binding.tilRecipeName.error = null

        if (binding.etDescription.text.toString().trim().isEmpty()) {
            binding.tilDescription.error = "La descripción es requerida"
            isValid = false
        } else binding.tilDescription.error = null

        if (ingredientsList.isEmpty()) {
            Toast.makeText(this, "Agrega al menos un ingrediente", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        if (instructionsList.isEmpty()) {
            Toast.makeText(this, "Agrega al menos una instrucción", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        val cookingTime = binding.etCookingTime.text.toString()
        if (cookingTime.isEmpty() || cookingTime.toIntOrNull() == null || cookingTime.toInt() <= 0) {
            binding.tilCookingTime.error = "Ingresa un tiempo válido"
            isValid = false
        } else binding.tilCookingTime.error = null

        val servings = binding.etServings.text.toString()
        if (servings.isEmpty() || servings.toIntOrNull() == null || servings.toInt() <= 0) {
            binding.tilServings.error = "Ingresa un número válido"
            isValid = false
        } else binding.tilServings.error = null

        if (binding.spinnerDifficulty.text.toString().isEmpty()) {
            binding.tilDifficulty.error = "Selecciona una dificultad"
            isValid = false
        } else binding.tilDifficulty.error = null

        return isValid
    }

    private fun showProgressBar(show: Boolean, message: String? = null) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvUpdatingMessage.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvUpdatingMessage.text = message ?: "Actualizando..."
        binding.btnUpdateRecipe.isEnabled = !show
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