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
    private var isLoading = false

    companion object {
        private const val ADD_RECIPE_REQUEST_CODE = 1001
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Firebase Auth y Repository
        auth = FirebaseAuth.getInstance()
        repository = FirebaseRepository()

        // Verificar autenticación
        if (auth.currentUser == null) {
            Log.e(TAG, "Usuario no autenticado en MainActivity")
            redirectToLogin()
            return
        }

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

        Log.d(TAG, "MainActivity inicializada correctamente")
    }

    override fun onResume() {
        super.onResume()
        // Verificar autenticación al reanudar
        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }
        // Recargar recetas cuando se regrese a la actividad
        loadRecipes()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
                showDeleteConfirmationDialog(recipe)
            }
        )

        binding.rvRecipes.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        binding.fabAddRecipe.setOnClickListener {
            if (auth.currentUser != null) {
                val intent = Intent(this, AddRecipeActivity::class.java)
                startActivityForResult(intent, ADD_RECIPE_REQUEST_CODE)
            } else {
                Toast.makeText(this, "Debes iniciar sesión para agregar recetas", Toast.LENGTH_SHORT).show()
                redirectToLogin()
            }
        }

        // Agregar swipe to refresh si tienes SwipeRefreshLayout
        // binding.swipeRefreshLayout?.setOnRefreshListener { loadRecipes() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_RECIPE_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d(TAG, "Receta agregada, recargando lista...")
            loadRecipes()
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val welcomeText = "¡Hola ${currentUser.email?.substringBefore("@") ?: "Usuario"}!"
            binding.tvWelcome.text = welcomeText
            Log.d(TAG, "Usuario cargado: ${currentUser.email}")
        } else {
            Log.e(TAG, "No hay usuario autenticado")
            redirectToLogin()
            return
        }

        loadRecipes()
    }

    private fun loadRecipes() {
        if (isLoading) {
            Log.d(TAG, "Ya se está cargando, evitando carga duplicada")
            return
        }

        if (auth.currentUser == null) {
            Log.e(TAG, "Usuario no autenticado al cargar recetas")
            redirectToLogin()
            return
        }

        isLoading = true
        showLoading(true)

        Log.d(TAG, "Cargando recetas del usuario: ${auth.currentUser?.uid}")

        lifecycleScope.launch {
            try {
                repository.getUserRecipes().fold(
                    onSuccess = { recipes ->
                        Log.d(TAG, "✅ Recetas cargadas exitosamente: ${recipes.size}")
                        runOnUiThread {
                            recipesList.clear()
                            recipesList.addAll(recipes)
                            recipeAdapter.submitList(recipes.toList())
                            updateUI(recipes)
                            showLoading(false)
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "❌ Error al cargar recetas", exception)
                        runOnUiThread {
                            val errorMessage = when {
                                exception.message?.contains("PERMISSION_DENIED") == true ->
                                    "Error de permisos. Reinicia la aplicación."
                                exception.message?.contains("network") == true ||
                                        exception.message?.contains("connectivity") == true ->
                                    "Sin conexión. Verifica tu internet."
                                else -> "Error al cargar recetas: ${exception.message}"
                            }

                            Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                            updateUI(emptyList())
                            showLoading(false)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error inesperado al cargar recetas", e)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Error inesperado: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    updateUI(emptyList())
                    showLoading(false)
                }
            } finally {
                isLoading = false
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

        Log.d(TAG, "UI actualizada: $totalRecipes recetas, $favoriteRecipes favoritas")
    }

    private fun showLoading(show: Boolean) {
        // Si tienes un ProgressBar o SwipeRefreshLayout, úsalo aquí
        // binding.progressBar?.visibility = if (show) View.VISIBLE else View.GONE
        // binding.swipeRefreshLayout?.isRefreshing = show
    }

    private fun toggleFavorite(recipe: Recipe) {
        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        Log.d(TAG, "Cambiando favorito para: ${recipe.name}")

        lifecycleScope.launch {
            try {
                repository.toggleFavorite(recipe.id).fold(
                    onSuccess = { isFavorite ->
                        Log.d(TAG, "✅ Favorito cambiado a: $isFavorite")

                        runOnUiThread {
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
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "❌ Error al cambiar favorito", exception)
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
                Log.e(TAG, "❌ Error inesperado al cambiar favorito", e)
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

    private fun deleteRecipe(recipe: Recipe) {
        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        Log.d(TAG, "Eliminando receta: ${recipe.name}")

        lifecycleScope.launch {
            try {
                repository.deleteRecipe(recipe.id).fold(
                    onSuccess = {
                        Log.d(TAG, "✅ Receta eliminada exitosamente")

                        runOnUiThread {
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
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "❌ Error al eliminar receta", exception)
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
                Log.e(TAG, "❌ Error inesperado al eliminar", e)
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
                // TODO: Implementar búsqueda
                Toast.makeText(this, "Búsqueda próximamente", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_profile -> {
                showUserProfile()
                true
            }
            R.id.action_logout -> {
                showLogoutConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showUserProfile() {
        val user = auth.currentUser
        if (user != null) {
            val message = """
                Usuario: ${user.email}
                UID: ${user.uid}
                Total de recetas: ${recipesList.size}
                Favoritas: ${recipesList.count { it.isFavorite }}
            """.trimIndent()

            AlertDialog.Builder(this)
                .setTitle("Perfil de Usuario")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro de que quieres cerrar sesión?")
            .setPositiveButton("Cerrar Sesión") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun logout() {
        Log.d(TAG, "Cerrando sesión del usuario: ${auth.currentUser?.email}")
        auth.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity destruida")
    }
}