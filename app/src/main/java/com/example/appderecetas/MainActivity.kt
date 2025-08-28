package com.example.appderecetas

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
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
                // TODO: Implementar ver detalles de la receta
                Toast.makeText(this, "Ver receta: ${recipe.name}", Toast.LENGTH_SHORT).show()
            },
            onFavoriteClick = { recipe ->
                toggleFavorite(recipe)
            }
        )

        binding.rvRecipes.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun setupClickListeners() {
        binding.fabAddRecipe.setOnClickListener {
            startActivity(Intent(this, AddRecipeActivity::class.java))
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