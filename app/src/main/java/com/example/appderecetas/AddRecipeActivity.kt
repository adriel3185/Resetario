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
import com.example.appderecetas.databinding.ActivityAddRecipeBinding
import com.example.appderecetas.model.Difficulty
import com.example.appderecetas.model.Recipe
import com.example.appderecetas.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddRecipeBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var auth: FirebaseAuth
    private val ingredientsList = mutableListOf<String>()
    private val instructionsList = mutableListOf<String>()
    private lateinit var ingredientsAdapter: ArrayAdapter<String>
    private lateinit var instructionsAdapter: ArrayAdapter<String>

    companion object {
        private const val TAG = "AddRecipeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        auth = FirebaseAuth.getInstance()

        setupToolbar()
        setupDifficultyDropdown()
        setupListViews()
        setupClickListeners()

        // Debug info
        debugFirebaseInfo()
    }

    private fun debugFirebaseInfo() {
        Log.d(TAG, "=== DEBUG FIREBASE INFO ===")
        Log.d(TAG, "Usuario autenticado: ${auth.currentUser != null}")
        Log.d(TAG, "UID: ${auth.currentUser?.uid}")
        Log.d(TAG, "Email: ${auth.currentUser?.email}")

        // Test de conectividad
        lifecycleScope.launch {
            repository.testConnection().fold(
                onSuccess = {
                    Log.d(TAG, "✅ Test de conexión Firebase: EXITOSO")
                },
                onFailure = { e ->
                    Log.e(TAG, "❌ Test de conexión Firebase: FALLÓ", e)
                }
            )
        }
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
        ingredientsAdapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, ingredientsList)
        binding.lvIngredients.adapter = ingredientsAdapter

        instructionsAdapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, instructionsList)
        binding.lvInstructions.adapter = instructionsAdapter
    }

    // 🔥 MÉTODO CLAVE PARA ARREGLAR LA ALTURA DE LISTVIEW
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
        binding.btnSaveRecipe.setOnClickListener { saveRecipe() }

        binding.lvIngredients.setOnItemLongClickListener { _, _, position, _ ->
            ingredientsList.removeAt(position)
            ingredientsAdapter.notifyDataSetChanged()
            setListViewHeight(binding.lvIngredients) // 🔥 AGREGAR AQUÍ
            showTemporaryMessage("Ingrediente eliminado")
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
            showTemporaryMessage("Instrucción eliminada")
            true
        }
    }

    private fun addIngredient() {
        val ingredient = binding.etIngredient.text.toString().trim()
        if (ingredient.isNotEmpty()) {
            ingredientsList.add(ingredient)
            ingredientsAdapter.notifyDataSetChanged()
            setListViewHeight(binding.lvIngredients) // 🔥 LÍNEA CLAVE
            binding.etIngredient.setText("")
            showTemporaryMessage("Ingrediente agregado")
            Log.d(TAG, "Ingrediente agregado. Total: ${ingredientsList.size}")
        } else {
            showTemporaryMessage("Ingresa un ingrediente válido")
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
            showTemporaryMessage("Instrucción agregada")
            Log.d(TAG, "Instrucción agregada. Total: ${instructionsList.size}")
        } else {
            showTemporaryMessage("Ingresa una instrucción válida")
        }
    }

    private fun saveRecipe() {
        Log.d(TAG, "=== INICIANDO PROCESO DE GUARDADO ===")

        // Verificar autenticación antes de validar
        if (auth.currentUser == null) {
            showTemporaryMessage("Error: Usuario no autenticado. Reinicia la aplicación.")
            return
        }

        if (!validateForm()) {
            Log.d(TAG, "Formulario no válido")
            return
        }

        Log.d(TAG, "Formulario válido, procediendo a guardar...")

        // Mostrar barra de progreso y mensaje inicial
        showProgressBar(true, "Validando datos...")

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

        Log.d(TAG, "Receta creada:")
        Log.d(TAG, "- Nombre: ${recipe.name}")
        Log.d(TAG, "- Descripción: ${recipe.description}")
        Log.d(TAG, "- Ingredientes: ${recipe.ingredients.size}")
        Log.d(TAG, "- Instrucciones: ${recipe.instructions.size}")
        Log.d(TAG, "- Tiempo: ${recipe.cookingTimeMinutes}")
        Log.d(TAG, "- Porciones: ${recipe.servings}")
        Log.d(TAG, "- Dificultad: ${recipe.difficulty}")

        lifecycleScope.launch {
            try {
                // Cambiar mensaje
                runOnUiThread {
                    showProgressBar(true, "Guardando receta en Firebase...")
                }

                Log.d(TAG, "Llamando a repository.saveRecipe()...")
                val result = repository.saveRecipe(recipe)

                result.fold(
                    onSuccess = { recipeId ->
                        Log.d(TAG, "✅ Receta guardada exitosamente con ID: $recipeId")

                        // Cambiar mensaje mientras esperamos
                        runOnUiThread {
                            showProgressBar(true, "¡Receta guardada! Regresando al menú...")
                        }

                        // Esperar 2 segundos
                        delay(2000)

                        // Regresar a MainActivity
                        runOnUiThread {
                            showProgressBar(false)
                            Toast.makeText(
                                this@AddRecipeActivity,
                                "¡Receta \"${recipe.name}\" guardada exitosamente!",
                                Toast.LENGTH_SHORT
                            ).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "❌ Error al guardar receta", exception)
                        runOnUiThread {
                            showProgressBar(false)

                            // Mostrar error más específico al usuario
                            val userMessage = when {
                                exception.message?.contains("PERMISSION_DENIED") == true ->
                                    "Error de permisos. Verifica tu conexión y vuelve a intentar."

                                exception.message?.contains("network") == true ||
                                        exception.message?.contains("connectivity") == true ->
                                    "Sin conexión a internet. Verifica tu red y vuelve a intentar."

                                exception.message?.contains("timeout") == true ->
                                    "Tiempo de espera agotado. Verifica tu conexión."

                                else -> "Error al guardar: ${exception.message}"
                            }

                            Toast.makeText(
                                this@AddRecipeActivity,
                                userMessage,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error inesperado al guardar", e)
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

        // Limpiar errores previos
        binding.tilRecipeName.error = null
        binding.tilDescription.error = null
        binding.tilCookingTime.error = null
        binding.tilServings.error = null
        binding.tilDifficulty.error = null

        // Validar nombre
        if (binding.etRecipeName.text.toString().trim().isEmpty()) {
            binding.tilRecipeName.error = "El nombre es requerido"
            isValid = false
        }

        // Validar descripción
        if (binding.etDescription.text.toString().trim().isEmpty()) {
            binding.tilDescription.error = "La descripción es requerida"
            isValid = false
        }

        // Validar ingredientes
        if (ingredientsList.isEmpty()) {
            showTemporaryMessage("Agrega al menos un ingrediente")
            isValid = false
        }

        // Validar instrucciones
        if (instructionsList.isEmpty()) {
            showTemporaryMessage("Agrega al menos una instrucción")
            isValid = false
        }

        // Validar tiempo de cocción
        val cookingTime = binding.etCookingTime.text.toString().trim()
        if (cookingTime.isEmpty() || cookingTime.toIntOrNull() == null || cookingTime.toInt() <= 0) {
            binding.tilCookingTime.error = "Ingresa un tiempo válido (mayor a 0)"
            isValid = false
        }

        // Validar porciones
        val servings = binding.etServings.text.toString().trim()
        if (servings.isEmpty() || servings.toIntOrNull() == null || servings.toInt() <= 0) {
            binding.tilServings.error = "Ingresa un número válido (mayor a 0)"
            isValid = false
        }

        // Validar dificultad
        if (binding.spinnerDifficulty.text.toString().isEmpty()) {
            binding.tilDifficulty.error = "Selecciona una dificultad"
            isValid = false
        }

        if (!isValid) {
            showTemporaryMessage("Por favor corrige los errores en el formulario")
        }

        return isValid
    }

    private fun showProgressBar(show: Boolean, message: String? = null) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvSavingMessage.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvSavingMessage.text = message ?: "Guardando..."
        binding.btnSaveRecipe.isEnabled = !show

        // Deshabilitar otros controles mientras se guarda
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

    private fun showTemporaryMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AddRecipeActivity destruida")
    }
}