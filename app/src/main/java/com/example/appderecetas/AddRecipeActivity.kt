package com.example.appderecetas

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.appderecetas.databinding.ActivityAddRecipeBinding
import com.example.appderecetas.model.Difficulty
import com.example.appderecetas.model.Recipe
import com.example.appderecetas.repository.FirebaseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddRecipeBinding
    private lateinit var repository: FirebaseRepository
    private val ingredientsList = mutableListOf<String>()
    private val instructionsList = mutableListOf<String>()
    private lateinit var ingredientsAdapter: ArrayAdapter<String>
    private lateinit var instructionsAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()

        setupToolbar()
        setupDifficultyDropdown()
        setupListViews()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Nueva Receta"
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
        binding.btnSaveRecipe.setOnClickListener { saveRecipe() }

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

    private fun saveRecipe() {
        if (!validateForm()) return

        // Mostrar barra de progreso y mensaje inicial
        showProgressBar(true, "Guardando receta...")

        val selectedDifficulty = binding.spinnerDifficulty.text.toString()
        val difficultyEnum = Difficulty.values()
            .firstOrNull { it.displayName == selectedDifficulty }
            ?: Difficulty.FACIL

        val recipe = Recipe(
            name = binding.etRecipeName.text.toString().trim(),
            description = binding.etDescription.text.toString().trim(),
            ingredients = ingredientsList.toList(),
            instructions = instructionsList.toList(),
            cookingTimeMinutes = binding.etCookingTime.text.toString().toIntOrNull() ?: 0,
            servings = binding.etServings.text.toString().toIntOrNull() ?: 1,
            difficulty = difficultyEnum
        )

        lifecycleScope.launch {
            try {
                val result = repository.saveRecipe(recipe)
                result.fold(
                    onSuccess = {
                        // Cambiar mensaje mientras esperamos 2 segundos
                        runOnUiThread {
                            showProgressBar(true, "¡Receta guardada! Regresando al menú...")
                        }

                        // Esperar 2 segundos
                        delay(2000)

                        // Regresar a MainActivity
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
                                this@AddRecipeActivity,
                                "Error al guardar: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                runOnUiThread {
                    showProgressBar(false)
                    Toast.makeText(
                        this@AddRecipeActivity,
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
        binding.tvSavingMessage.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvSavingMessage.text = message ?: "Guardando..."
        binding.btnSaveRecipe.isEnabled = !show
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
