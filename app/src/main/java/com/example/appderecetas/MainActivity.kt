package com.example.appderecetas

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appderecetas.adapter.RecipeAdapter
import com.example.appderecetas.databinding.ActivityMainBinding
import com.example.appderecetas.model.Recipe
import com.example.appderecetas.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: FirebaseRepository
    private lateinit var recipeAdapter: RecipeAdapter
    private val recipesList = mutableListOf<Recipe>()

    companion object {
        private const val TAG = "MainActivity"
        private const val ADD_RECIPE_REQUEST_CODE = 1001
        private const val EDIT_RECIPE_REQUEST_CODE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "=== MainActivity onCreate ===")

        // Inicializar Firebase Auth y Repository
        auth = FirebaseAuth.getInstance()
        repository = FirebaseRepository()

        // Configurar toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Mis Recetas"

        // Configurar edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupClickListeners()
        loadUserData()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== MainActivity onResume ===")
        // Recargar recetas cuando se regrese a la actividad
        loadRecipes()
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Configurando RecyclerView")

        recipeAdapter = RecipeAdapter(
            onRecipeClick = { recipe ->
                Log.d(TAG, "Click en receta: ${recipe.name}")
                val intent = Intent(this, RecipeDetailActivity::class.java).apply {
                    putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id)
                }
                startActivity(intent)
            },
            onFavoriteClick = { recipe ->
                Log.d(TAG, "Click en favorito: ${recipe.name}")
                toggleFavorite(recipe)
            },
            onEditClick = { recipe ->
                Log.d(TAG, "Click en editar: ${recipe.name}")
                editRecipe(recipe)
            },
            onDeleteClick = { recipe ->
                Log.d(TAG, "Click en eliminar: ${recipe.name}")
                showDeleteConfirmationDialog(recipe)
            }
        )

        binding.rvRecipes.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(false)
        }

        Log.d(TAG, "RecyclerView configurado correctamente")
    }

    private fun setupClickListeners() {
        binding.fabAddRecipe.setOnClickListener {
            Log.d(TAG, "Click en FAB - Navegando a AddRecipeActivity")
            val intent = Intent(this, AddRecipeActivity::class.java)
            startActivityForResult(intent, ADD_RECIPE_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "=== onActivityResult ===")
        Log.d(TAG, "RequestCode: $requestCode, ResultCode: $resultCode")

        when (requestCode) {
            ADD_RECIPE_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Receta agregada exitosamente - Recargando lista")
                    loadRecipes()
                }
            }
            EDIT_RECIPE_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Receta editada exitosamente - Recargando lista")
                    loadRecipes()
                }
            }
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        val welcomeText = "¡Hola ${currentUser?.email?.substringBefore("@") ?: "Usuario"}!"
        binding.tvWelcome.text = welcomeText
        Log.d(TAG, "Usuario cargado: $welcomeText")

        loadRecipes()
    }

    private fun loadRecipes() {
        Log.d(TAG, "=== INICIANDO CARGA DE RECETAS ===")

        lifecycleScope.launch {
            try {
                repository.getUserRecipes().fold(
                    onSuccess = { recipes ->
                        Log.d(TAG, "Recetas obtenidas del repositorio: ${recipes.size}")

                        // 🔥 ACTUALIZACIÓN MEJORADA
                        runOnUiThread {
                            // Limpiar lista actual
                            recipesList.clear()
                            recipesList.addAll(recipes)

                            Log.d(TAG, "Lista local actualizada con ${recipesList.size} recetas")

                            // 🔥 MÉTODO MEJORADO PARA FORZAR ACTUALIZACIÓN
                            recipeAdapter.submitList(emptyList()) {
                                // Callback que se ejecuta cuando la lista vacía se ha aplicado
                                recipeAdapter.submitList(recipes.toMutableList()) {
                                    // Callback que se ejecuta cuando la nueva lista se ha aplicado
                                    Log.d(TAG, "RecyclerView actualizado correctamente")
                                    updateUI(recipes)
                                }
                            }
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error al cargar recetas", exception)
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Error al cargar recetas: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            updateUI(emptyList())
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado al cargar recetas", e)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Error inesperado: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    updateUI(emptyList())
                }
            }
        }
    }

    private fun updateUI(recipes: List<Recipe>) {
        val hasRecipes = recipes.isNotEmpty()

        Log.d(TAG, "=== ACTUALIZANDO UI ===")
        Log.d(TAG, "Tiene recetas: $hasRecipes")
        Log.d(TAG, "Total: ${recipes.size}")

        // Mostrar/ocultar estado vacío
        binding.layoutEmptyState.visibility = if (hasRecipes) View.GONE else View.VISIBLE
        binding.rvRecipes.visibility = if (hasRecipes) View.VISIBLE else View.GONE

        // Actualizar estadísticas
        val totalRecipes = recipes.size
        val favoriteRecipes = recipes.count { it.isFavorite }

        binding.tvTotalRecipes.text = totalRecipes.toString()
        binding.tvFavorites.text = favoriteRecipes.toString()

        Log.d(TAG, "UI actualizada - Total: $totalRecipes, Favoritos: $favoriteRecipes")

        // 🔥 FORZAR REFRESH DEL RECYCLERVIEW
        if (hasRecipes) {
            binding.rvRecipes.post {
                binding.rvRecipes.adapter?.notifyDataSetChanged()
                Log.d(TAG, "NotifyDataSetChanged ejecutado")
            }
        }
    }

    private fun toggleFavorite(recipe: Recipe) {
        Log.d(TAG, "Cambiando estado de favorito para: ${recipe.name}")

        lifecycleScope.launch {
            try {
                repository.toggleFavorite(recipe.id).fold(
                    onSuccess = { isFavorite ->
                        Log.d(TAG, "Estado de favorito actualizado: $isFavorite")

                        runOnUiThread {
                            // Actualizar la receta localmente
                            val updatedRecipes = recipesList.map {
                                if (it.id == recipe.id) it.copy(isFavorite = isFavorite) else it
                            }
                            recipesList.clear()
                            recipesList.addAll(updatedRecipes)

                            // Actualizar adapter
                            recipeAdapter.submitList(emptyList()) {
                                recipeAdapter.submitList(updatedRecipes.toMutableList()) {
                                    updateUI(updatedRecipes)
                                }
                            }

                            val message = if (isFavorite) "Agregado a favoritos" else "Removido de favoritos"
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error al cambiar favorito", exception)
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Error: ${exception.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado al cambiar favorito", e)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Error inesperado: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // 🔥 NUEVA FUNCIÓN: Editar receta
    private fun editRecipe(recipe: Recipe) {
        Log.d(TAG, "Iniciando edición de receta: ${recipe.name}")

        val intent = Intent(this, EditRecipeActivity::class.java).apply {
            putExtra(EditRecipeActivity.EXTRA_RECIPE_ID, recipe.id)
        }
        startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE)
    }

    private fun showDeleteConfirmationDialog(recipe: Recipe) {
        Log.d(TAG, "Mostrando diálogo de confirmación para eliminar: ${recipe.name}")

        AlertDialog.Builder(this)
            .setTitle("Eliminar Receta")
            .setMessage("¿Estás seguro de que quieres eliminar \"${recipe.name}\"?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteRecipe(recipe)
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                Log.d(TAG, "Eliminación cancelada")
                dialog.dismiss()
            }
            .setIcon(R.drawable.ic_delete)
            .show()
    }

    private fun deleteRecipe(recipe: Recipe) {
        Log.d(TAG, "Eliminando receta: ${recipe.name}")

        lifecycleScope.launch {
            try {
                repository.deleteRecipe(recipe.id).fold(
                    onSuccess = {
                        Log.d(TAG, "Receta eliminada exitosamente: ${recipe.name}")

                        runOnUiThread {
                            // Remover la receta de la lista local
                            val updatedRecipes = recipesList.filter { it.id != recipe.id }
                            recipesList.clear()
                            recipesList.addAll(updatedRecipes)

                            // Actualizar adapter
                            recipeAdapter.submitList(emptyList()) {
                                recipeAdapter.submitList(updatedRecipes.toMutableList()) {
                                    updateUI(updatedRecipes)
                                }
                            }

                            Toast.makeText(
                                this@MainActivity,
                                "\"${recipe.name}\" eliminada correctamente",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error al eliminar receta", exception)
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Error al eliminar: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado al eliminar receta", e)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Error inesperado: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                Toast.makeText(this, "Búsqueda próximamente", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_profile -> {
                Toast.makeText(this, "Perfil próximamente", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun logout() {
        Log.d(TAG, "Cerrando sesión")
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}