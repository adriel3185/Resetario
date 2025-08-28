package com.example.appderecetas

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appderecetas.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Firebase Auth
        auth = FirebaseAuth.getInstance()

        Log.d(TAG, "LoginActivity iniciada")

        setupClickListeners()
    }

    override fun onStart() {
        super.onStart()
        // Verificar si el usuario ya está autenticado
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "Usuario ya autenticado: ${currentUser.email}")
            navigateToMainActivity()
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            loginUser()
        }

        binding.btnRegister.setOnClickListener {
            registerUser()
        }

        binding.tvForgotPassword.setOnClickListener {
            forgotPassword()
        }
    }

    private fun loginUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        Log.d(TAG, "Intentando login para: $email")

        if (validateInput(email, password)) {
            showProgressBar(true)

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    showProgressBar(false)
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        Log.d(TAG, "✅ Login exitoso para: ${user?.email}")
                        Toast.makeText(this, "¡Bienvenido ${user?.email?.substringBefore("@")}!", Toast.LENGTH_SHORT).show()
                        navigateToMainActivity()
                    } else {
                        val exception = task.exception
                        Log.e(TAG, "❌ Error en login", exception)

                        val errorMessage = when (exception) {
                            is FirebaseAuthInvalidUserException -> {
                                "No existe una cuenta con este email"
                            }
                            is FirebaseAuthInvalidCredentialsException -> {
                                "Contraseña incorrecta"
                            }
                            else -> {
                                "Error de autenticación: ${exception?.message}"
                            }
                        }

                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        Log.d(TAG, "Intentando registro para: $email")

        if (validateInputForRegistration(email, password)) {
            showProgressBar(true)

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    showProgressBar(false)
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        Log.d(TAG, "✅ Registro exitoso para: ${user?.email}")
                        Toast.makeText(this, "¡Cuenta creada exitosamente! Bienvenido ${user?.email?.substringBefore("@")}!", Toast.LENGTH_SHORT).show()
                        navigateToMainActivity()
                    } else {
                        val exception = task.exception
                        Log.e(TAG, "❌ Error en registro", exception)

                        val errorMessage = when (exception) {
                            is FirebaseAuthWeakPasswordException -> {
                                "La contraseña es muy débil: ${exception.reason}"
                            }
                            is FirebaseAuthInvalidCredentialsException -> {
                                "Email no válido"
                            }
                            is FirebaseAuthUserCollisionException -> {
                                "Ya existe una cuenta con este email"
                            }
                            else -> {
                                "Error al crear cuenta: ${exception?.message}"
                            }
                        }

                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun forgotPassword() {
        val email = binding.etEmail.text.toString().trim()

        if (email.isEmpty()) {
            binding.etEmail.error = "Ingresa tu email para recuperar la contraseña"
            binding.etEmail.requestFocus()
            Toast.makeText(this, "Ingresa tu email para recuperar la contraseña", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Ingresa un email válido"
            binding.etEmail.requestFocus()
            Toast.makeText(this, "Ingresa un email válido", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Enviando email de recuperación a: $email")
        showProgressBar(true)

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                showProgressBar(false)
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Email de recuperación enviado")
                    Toast.makeText(this,
                        "Se envió un email a $email para restablecer tu contraseña. Revisa tu bandeja de entrada.",
                        Toast.LENGTH_LONG).show()
                } else {
                    val exception = task.exception
                    Log.e(TAG, "❌ Error al enviar email de recuperación", exception)

                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidUserException -> {
                            "No existe una cuenta con este email"
                        }
                        else -> {
                            "Error al enviar email: ${exception?.message}"
                        }
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        // Limpiar errores previos
        binding.etEmail.error = null
        binding.etPassword.error = null

        if (email.isEmpty()) {
            binding.etEmail.error = "El email es requerido"
            binding.etEmail.requestFocus()
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Ingresa un email válido"
            binding.etEmail.requestFocus()
            isValid = false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "La contraseña es requerida"
            binding.etPassword.requestFocus()
            isValid = false
        }

        return isValid
    }

    private fun validateInputForRegistration(email: String, password: String): Boolean {
        if (!validateInput(email, password)) {
            return false
        }

        if (password.length < 6) {
            binding.etPassword.error = "La contraseña debe tener al menos 6 caracteres"
            binding.etPassword.requestFocus()
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validaciones adicionales para registro
        if (password.length > 50) {
            binding.etPassword.error = "La contraseña es demasiado larga (máximo 50 caracteres)"
            binding.etPassword.requestFocus()
            return false
        }

        return true
    }

    private fun showProgressBar(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
        binding.btnRegister.isEnabled = !show
        binding.tvForgotPassword.isEnabled = !show
        binding.etEmail.isEnabled = !show
        binding.etPassword.isEnabled = !show

        if (show) {
            Log.d(TAG, "Mostrando loading...")
        } else {
            Log.d(TAG, "Ocultando loading...")
        }
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "Navegando a MainActivity")
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LoginActivity destruida")
    }
}