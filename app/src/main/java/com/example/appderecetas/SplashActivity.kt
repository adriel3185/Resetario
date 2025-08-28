package com.example.appderecetas

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Inicializar Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Splash screen con delay de 3 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserAuthentication()
        }, 3000)
    }

    private fun checkUserAuthentication() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // Usuario ya autenticado, ir a MainActivity
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // Usuario no autenticado, ir a LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        }

        finish()
    }
}