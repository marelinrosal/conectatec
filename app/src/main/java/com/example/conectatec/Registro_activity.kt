package com.example.conectatec

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.postgrest // ✅ Importación correcta
import android.util.Log // ✅ Importar esto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Registro_activity : AppCompatActivity() {

    data class User(
        val id: Int = 0,
        val name: String = "",
        val email: String = "",
        val phone: String = "",
        val username: String = "",
        var password: String = ""
    )

    // Declare UI elements
    private lateinit var nombreApellidos: EditText
    private lateinit var email: EditText
    private lateinit var telefono: EditText
    private lateinit var usuario: EditText
    private lateinit var clave: EditText
    private lateinit var btnRegistrar: Button
    private lateinit var tvGoBack: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize UI elements
        nombreApellidos = findViewById(R.id.txtnomapellidos)
        email = findViewById(R.id.txtemail)
        telefono = findViewById(R.id.txttelefono)
        usuario = findViewById(R.id.txtusuario)
        clave = findViewById(R.id.txtclave)
        btnRegistrar = findViewById(R.id.btnregistrar)
        tvGoBack = findViewById(R.id.lbliniciarsesion)

        // Set up navigation back to login
        tvGoBack.setOnClickListener {
            finish()
        }

        // Set up registration button with validations
        btnRegistrar.setOnClickListener {
            if (validateInputs()) {
                // All validations passed - proceed with registration
                registerUser()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Validate name and surname
        if (nombreApellidos.text.toString().trim().isEmpty()) {
            nombreApellidos.error = "Por favor, ingrese su nombre y apellidos"
            isValid = false
        } else if (!nombreApellidos.text.toString().trim().contains(" ")) {
            nombreApellidos.error = "Ingrese nombre y apellido separados por espacio"
            isValid = false
        }

        // Validate email
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        if (email.text.toString().trim().isEmpty()) {
            email.error = "Por favor, ingrese su correo electrónico"
            isValid = false
        } else if (!email.text.toString().trim().matches(emailPattern.toRegex())) {
            email.error = "Ingrese un correo electrónico válido"
            isValid = false
        }

        // Validate phone number
        if (telefono.text.toString().trim().isEmpty()) {
            telefono.error = "Por favor, ingrese su número de teléfono"
            isValid = false
        } else if (telefono.text.toString().trim().length < 9) {
            telefono.error = "El número debe tener al menos 9 dígitos"
            isValid = false
        } else if (!telefono.text.toString().trim().matches("\\d+".toRegex())) {
            telefono.error = "Ingrese solo números"
            isValid = false
        }

        // Validate username
        if (usuario.text.toString().trim().isEmpty()) {
            usuario.error = "Por favor, ingrese un nombre de usuario"
            isValid = false
        } else if (usuario.text.toString().trim().length < 4) {
            usuario.error = "El usuario debe tener al menos 4 caracteres"
            isValid = false
        }

        // Validate password
        if (clave.text.toString().isEmpty()) {
            clave.error = "Por favor, ingrese una contraseña"
            isValid = false
        } else if (clave.text.toString().length < 6) {
            clave.error = "La contraseña debe tener al menos 6 caracteres"
            isValid = false
        } else if (!isPasswordStrong(clave.text.toString())) {
            clave.error = "La contraseña debe incluir letras y números"
            isValid = false
        }

        return isValid
    }

    private fun isPasswordStrong(password: String): Boolean {
        // Password should contain at least one letter and one number
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }

    private fun registerUser() {
        // Here you would implement the actual registration logic
        // For example, API calls, database operations, etc.

        Toast.makeText(
            this,
            "Registro exitoso para: ${nombreApellidos.text}",
            Toast.LENGTH_LONG
        ).show()

        // After successful registration, you might want to navigate to another activity
        val intent = Intent(this, Login_activity::class.java)
        startActivity(intent)
        finish()
    }


    // se crea el cliente para conectar con supabse
    //mala práctica hay que crear variables de entorno
        private fun getClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc",
        ) {
            install(Postgrest)
        }
    }


}