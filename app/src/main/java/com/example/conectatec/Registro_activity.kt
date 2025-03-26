package com.example.conectatec

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Usuario(
    val usuario_id: String,
    val nombre: String,
    val email: String,
    val telefono: String,  // Cambiado a 'telefono' (sin acento)
    val nombre_usuario: String,
    val password: String
)

class Registro_activity : AppCompatActivity() {

    // Declarar elementos de la UI
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

        // Inicializar elementos de la UI
        nombreApellidos = findViewById(R.id.txtnomapellidos)
        email = findViewById(R.id.txtemail)
        telefono = findViewById(R.id.txttelefono)
        usuario = findViewById(R.id.txtusuario)
        clave = findViewById(R.id.txtclave)
        btnRegistrar = findViewById(R.id.btnregistrar)
        tvGoBack = findViewById(R.id.lbliniciarsesion)

        // Navegar de regreso al login
        tvGoBack.setOnClickListener {
            finish()
        }

        // Evento del botón de registro con validaciones
        btnRegistrar.setOnClickListener {
            if (validateInputs()) {
                registerUser()
            }
        }
    }

    // Validaciones del formulario
    private fun validateInputs(): Boolean {
        var isValid = true

        // Validar nombre y apellidos
        if (nombreApellidos.text.toString().trim().isEmpty()) {
            nombreApellidos.error = "Por favor, ingrese su nombre y apellidos"
            isValid = false
        } else if (!nombreApellidos.text.toString().trim().contains(" ")) {
            nombreApellidos.error = "Ingrese nombre y apellido separados por espacio"
            isValid = false
        }

        // Validar email
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        if (email.text.toString().trim().isEmpty()) {
            email.error = "Por favor, ingrese su correo electrónico"
            isValid = false
        } else if (!email.text.toString().trim().matches(emailPattern.toRegex())) {
            email.error = "Ingrese un correo electrónico válido"
            isValid = false
        }

        // Validar teléfono
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

        // Validar nombre de usuario
        val username = usuario.text.toString().trim()
        if (username.isEmpty()) {
            usuario.error = "Por favor, ingrese un nombre de usuario"
            isValid = false
        } else if (username.contains(" ")) {
            usuario.error = "El usuario no puede contener espacios"
            isValid = false
        } else if (username.length < 4) {
            usuario.error = "El usuario debe tener al menos 4 caracteres"
            isValid = false
        }

        // Validar contraseña
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
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }

    private fun registerUser() {
        val nombre = nombreApellidos.text.toString().trim()
        val correo = email.text.toString().trim()
        val telefono = telefono.text.toString().trim()
        val nombreUsuario = usuario.text.toString().trim()
        val password = clave.text.toString().trim()

        val client = getClient()

        // Corrutina para validar si el usuario ya existe
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // **Importante:**  Solo necesitas el nombre_usuario para verificar la existencia.
                val existingUser = client.postgrest["usuarios"]
                    .select {
                        filter {
                            eq("nombre_usuario", nombreUsuario)
                        }
                    }
                    .decodeList<Usuario>() // Decodifica a lista de Usuario

                if (existingUser.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "El nombre de usuario ya está registrado", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Crear el objeto del usuario
                    val usuarioNuevo = Usuario(
                        usuario_id = nombreUsuario,
                        nombre = nombre,
                        email = correo,
                        telefono = telefono,
                        nombre_usuario = nombreUsuario,
                        password = password  // **Seguridad:** Hashea en el backend.
                    )

                    // Convertir el objeto a JSON
                    val jsonUsuario = Json.encodeToString(usuarioNuevo)

                    // Insertar el usuario en la base de datos
                    client.postgrest["usuarios"].insert(usuarioNuevo)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Usuario registrado correctamente", Toast.LENGTH_LONG).show()
                        val intent = Intent(applicationContext, Login_activity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Error al registrar: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("Registro", "Error en el registro", e)
                }
            }
        }
    }

    // Cliente de Supabase (se recomienda usar variables de entorno en producción)
    private fun getClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc",
        ) {
            install(Postgrest)
        }
    }
}