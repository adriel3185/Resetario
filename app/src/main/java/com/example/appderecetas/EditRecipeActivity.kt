package com.example.appderecetas

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
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
        private const val TAG = "EditRecipeActivity"
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

    // 🔥 MÉTODO CLAVE PARA ARREGLAR LA ALTURA DE LISTVIEW (IGUAL QUE EN ADD)
    private fun setListViewHeight(listView: ListView) {
        val adapter = listView.adapter ?: return
        var totalHeight = 0

        // Calcular la altura total de todos los elementos
        for (i in 0 until adapter.count) {
            val listItem = adapter.getView(i, null, listView)
            listItem.measure(
                View.MeasureSpec.makeMeasureSpec(listView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            totalHeight += listItem.measuredHeight
        }

        // Agregar la altura de los dividers
        val dividerHeight = if (listView.dividerHeight > 0) listView.dividerHeight else 1
        totalHeight += dividerHeight * (adapter.count - 1)

        // Agregar padding
        totalHeight += listView.paddingTop + listView.paddingBottom

        // Aplicar la nueva altura
        val params = listView.layoutParams
        params.height = totalHeight
        listView.layoutParams = params
        listView.requestLayout()

        Log.d(TAG, "ListView altura ajustada a: $totalHeight para ${adapter.count} elementos")
    }

    private fun setupClickListeners() {
        binding.btnAddIngredient.setOnClickListener { addIngredient() }
        binding.btnAddInstruction.setOnClickListener { addInstruction() }
        binding.btnUpdateRecipe.setOnClickListener { updateRecipe() }

        binding.lvIngredients.setOnItemLongClickListener { _, _, position, _ ->
            ingredientsList.removeAt(position)
            ingredientsAdapter.notifyDataSetChanged()
            setListViewHeight(binding.lvIngredients) // 🔥 AGREGAR AQUÍ
            Toast.makeText(this, "Ingrediente eliminado", Toast.LENGTH_SHORT).show()
            true
        }

        binding.lvInstructions.setOnItemLongClickListener { _, _, position, _ ->
            instructionsList.removeAt(position)
            // Renumerar las instrucciones
            for (i in instructionsList.indices) {
                val instruction = instructionsList[i]
                val cleanInstruction = instruction.substringAfter(". ")
                instructionsList[i] = "${i + 1}. $cleanInstruction"
            }
            instructionsAdapter.notifyDataSetChanged()
            setListViewHeight(binding.lvInstructions) // 🔥 AGREGAR AQUÍ
            Toast.makeText(this, "Instrucción eliminada", Toast.LENGTH_SHORT).show()
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
            setListViewHeight(binding.lvIngredients) // 🔥 CLAVE PARA MOSTRAR TODOS

            // Cargar instrucciones
            instructionsList.clear()
            instructionsList.addAll(recipe.instructions)
            instructionsAdapter.notifyDataSetChanged()
            setListViewHeight(binding.lvInstructions) // 🔥 CLAVE PARA MOSTRAR TODOS

            Log.d(TAG, "Receta cargada - Ingredientes: ${ingredientsList.size}, Instrucciones: ${instructionsList.size}")
        }
    }

    private fun addIngredient() {
        val ingredient = binding.etIngredient.text.toString().trim()
        if (ingredient.isNotEmpty()) {
            ingredientsList.add(ingredient)
            ingredientsAdapter.notifyDataSetChanged()
            setListViewHeight(binding.lvIngredients) // 🔥 LÍNEA CLAVE
            binding.etIngredient.setText("")
            Toast.makeText(this, "Ingrediente agregado", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Ingrediente agregado. Total: ${ingredientsList.size}")
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
            setListViewHeight(binding.lvInstructions) // 🔥 LÍNEA CLAVE
            binding.etInstruction.setText("")
            Toast.makeText(this, "Instrucción agregada", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Instrucción agregada. Total: ${instructionsList.size}")
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
                            Toast.makeText(
                                this@EditRecipeActivity,
                                "¡Receta \"${updatedRecipe.name}\" actualizada!",
                                Toast.LENGTH_SHORT
                            ).show()
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

        // Deshabilitar otros controles mientras se actualiza
        binding.btnAddIngredient.isEnabled = !show
        binding.btnAddInstruction.isEnabled = !show
        binding.etRecipeName.isEnabled = !show
        binding.etDescription.isEnabled = !show
        binding.etIngredient.isEnabled = !show
        binding.etInstruction.isEnabled = !show
        binding.etCookingTime.isEnabled = !show
        binding.etServings.isEnabled = !show
        binding.spinnerDifficulty.isEnabled = !show
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