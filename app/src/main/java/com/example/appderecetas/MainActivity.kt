package com.example.appderecetas

import android.content.Intent
import android.os.Bundle
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
        private const val ADD_RECIPE_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        // Recargar recetas cuando se regrese a la actividad
        loadRecipes()
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(
            onRecipeClick = { recipe ->
                // Navegar a la pantalla de detalles
                val intent = Intent(this, RecipeDetailActivity::class.java).apply {
                    putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id)
                }
                startActivity(intent)
            },
            onFavoriteClick = { recipe ->
                toggleFavorite(recipe)
            },
            onDeleteClick = { recipe ->
                // 🔥 NUEVA FUNCIÓN: Mostrar diálogo de confirmación para borrar
                showDeleteConfirmationDialog(recipe)
            }
        )

        binding.rvRecipes.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun setupClickListeners() {
        binding.fabAddRecipe.setOnClickListener {
            val intent = Intent(this, AddRecipeActivity::class.java)
            startActivityForResult(intent, ADD_RECIPE_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_RECIPE_REQUEST_CODE && resultCode == RESULT_OK) {
            // Recargar recetas cuando se agrega una nueva
            loadRecipes()
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        val welcomeText = "¡Hola ${currentUser?.email?.substringBefore("@") ?: "Usuario"}!"
        binding.tvWelcome.text = welcomeText

        loadRecipes()
    }

    private fun loadRecipes() {
        lifecycleScope.launch {
            try {
                repository.getUserRecipes().fold(
                    onSuccess = { recipes ->
                        recipesList.clear()
                        recipesList.addAll(recipes)
                        recipeAdapter.submitList(recipes.toList())
                        updateUI(recipes)
                    },
                    onFailure = { exception ->
                        Toast.makeText(
                            this@MainActivity,
                            "Error al cargar recetas: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        updateUI(emptyList())
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error inesperado: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                updateUI(emptyList())
            }
        }
    }

    private fun updateUI(recipes: List<Recipe>) {
        val hasRecipes = recipes.isNotEmpty()

        // Mostrar/ocultar estado vacío
        binding.layoutEmptyState.visibility = if (hasRecipes) View.GONE else View.VISIBLE
        binding.rvRecipes.visibility = if (hasRecipes) View.VISIBLE else View.GONE

        // Actualizar estadísticas
        val totalRecipes = recipes.size
        val favoriteRecipes = recipes.count { it.isFavorite }

        binding.tvTotalRecipes.text = totalRecipes.toString()
        binding.tvFavorites.text = favoriteRecipes.toString()
    }

    private fun toggleFavorite(recipe: Recipe) {
        lifecycleScope.launch {
            try {
                repository.toggleFavorite(recipe.id).fold(
                    onSuccess = { isFavorite ->
                        // Actualizar la receta localmente
                        val updatedRecipes = recipesList.map {
                            if (it.id == recipe.id) it.copy(isFavorite = isFavorite) else it
                        }
                        recipesList.clear()
                        recipesList.addAll(updatedRecipes)
                        recipeAdapter.submitList(updatedRecipes.toList())
                        updateUI(updatedRecipes)

                        val message = if (isFavorite) "Agregado a favoritos" else "Removido de favoritos"
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { exception ->
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error inesperado: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 🔥 NUEVA FUNCIÓN: Mostrar diálogo de confirmación para borrar
    private fun showDeleteConfirmationDialog(recipe: Recipe) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Receta")
            .setMessage("¿Estás seguro de que quieres eliminar \"${recipe.name}\"?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteRecipe(recipe)
            }
            .setNegativeButton("Cancelar", null)
            .setIcon(R.drawable.ic_delete)
            .show()
    }

    // 🔥 NUEVA FUNCIÓN: Eliminar receta
    private fun deleteRecipe(recipe: Recipe) {
        lifecycleScope.launch {
            try {
                repository.deleteRecipe(recipe.id).fold(
                    onSuccess = {
                        // Remover la receta de la lista local
                        val updatedRecipes = recipesList.filter { it.id != recipe.id }
                        recipesList.clear()
                        recipesList.addAll(updatedRecipes)
                        recipeAdapter.submitList(updatedRecipes.toList())
                        updateUI(updatedRecipes)

                        Toast.makeText(
                            this@MainActivity,
                            "\"${recipe.name}\" eliminada correctamente",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { exception ->
                        Toast.makeText(
                            this@MainActivity,
                            "Error al eliminar: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error inesperado: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
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
                // TODO: Implementar búsqueda
                Toast.makeText(this, "Búsqueda próximamente", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_profile -> {
                // TODO: Implementar perfil de usuario
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
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}