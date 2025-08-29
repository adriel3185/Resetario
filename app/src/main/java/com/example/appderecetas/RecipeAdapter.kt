package com.example.appderecetas.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.appderecetas.R
import com.example.appderecetas.databinding.ItemRecipeBinding
import com.example.appderecetas.model.Recipe

class RecipeAdapter(
    private val onRecipeClick: (Recipe) -> Unit,
    private val onFavoriteClick: (Recipe) -> Unit,
    private val onEditClick: (Recipe) -> Unit,
    private val onDeleteClick: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipeAdapter.RecipeViewHolder>(RecipeDiffCallback()) {

    companion object {
        private const val TAG = "RecipeAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        Log.d(TAG, "Creando ViewHolder")
        val binding = ItemRecipeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = getItem(position)
        Log.d(TAG, "Binding receta en posición $position: ${recipe.name}")
        holder.bind(recipe)
    }

    override fun getItemCount(): Int {
        val count = super.getItemCount()
        Log.d(TAG, "getItemCount: $count")
        return count
    }

    inner class RecipeViewHolder(
        private val binding: ItemRecipeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            Log.d(TAG, "Binding receta: ${recipe.name}")

            binding.apply {
                // Información básica
                tvRecipeName.text = recipe.name
                tvRecipeDescription.text = recipe.description

                // 🔥 AGREGAR INFORMACIÓN ADICIONAL SI EXISTEN LOS CAMPOS
                try {
                    // Solo agregar si existen estos TextView en el layout
                    binding.root.findViewById<android.widget.TextView>(R.id.tvCookingTime)?.let { textView ->
                        textView.text = "${recipe.cookingTimeMinutes} min"
                    }

                    binding.root.findViewById<android.widget.TextView>(R.id.tvDifficulty)?.let { textView ->
                        textView.text = recipe.difficulty.displayName
                    }

                    binding.root.findViewById<android.widget.TextView>(R.id.tvServings)?.let { textView ->
                        textView.text = "${recipe.servings} porciones"
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Algunos campos opcionales no existen en el layout, continuando...")
                }

                // Configurar botón favorito
                val favoriteIcon = if (recipe.isFavorite) {
                    R.drawable.ic_favorite
                } else {
                    R.drawable.ic_favorite_border
                }
                ibFavorite.setImageResource(favoriteIcon)

                Log.d(TAG, "Botón favorito configurado - isFavorite: ${recipe.isFavorite}")

                // 🔥 CLICK LISTENERS CON LOGS
                root.setOnClickListener {
                    Log.d(TAG, "Click en receta: ${recipe.name}")
                    onRecipeClick(recipe)
                }

                ibFavorite.setOnClickListener {
                    Log.d(TAG, "Click en favorito: ${recipe.name}")
                    onFavoriteClick(recipe)
                }

                // 🔥 NUEVO: Botón editar
                ibEdit.setOnClickListener {
                    Log.d(TAG, "Click en editar: ${recipe.name}")
                    onEditClick(recipe)
                }

                ibDelete.setOnClickListener {
                    Log.d(TAG, "Click en eliminar: ${recipe.name}")
                    onDeleteClick(recipe)
                }
            }
        }
    }
}

// 🔥 DIFFUTIL CALLBACK MEJORADO CON LOGS
class RecipeDiffCallback : DiffUtil.ItemCallback<Recipe>() {
    companion object {
        private const val TAG = "RecipeDiffCallback"
    }

    override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
        val same = oldItem.id == newItem.id
        Log.d(TAG, "areItemsTheSame: ${oldItem.name} == ${newItem.name} -> $same")
        return same
    }

    override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
        val same = oldItem == newItem
        Log.d(TAG, "areContentsTheSame: ${oldItem.name} -> $same")
        return same
    }
}