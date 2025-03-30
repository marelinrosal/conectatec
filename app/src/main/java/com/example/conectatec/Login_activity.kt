package com.example.conectatec

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.postgrest // ✅ Importación correcta
import android.util.Log // ✅ Importar esto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.EditText
import android.widget.Toast
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val usuario_id: String = "", // Cambiado a String
    val password: String = ""
)


class Login_activity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        val tvRegister = findViewById<TextView>(R.id.lblregistrar)
        val btnLogin = findViewById<Button>(R.id.btningresar)
        val txtUsuario = findViewById<EditText>(R.id.txtusuario)
        val txtContrasena = findViewById<EditText>(R.id.txtcontrasena)


        tvRegister.setOnClickListener {
            // Crear Intent para iniciar la nueva Activity
            val intent = Intent(this, Registro_activity::class.java)
            startActivity(intent)
        }

        // Click en "Iniciar sesión"
        btnLogin.setOnClickListener {
            val usuario = txtUsuario.text.toString()
            val contrasena = txtContrasena.text.toString()

            if (usuario.isNotEmpty() && contrasena.isNotEmpty()) {
                // Iniciar sesión
                login(usuario, contrasena)
            } else {
                Toast.makeText(this, "Por favor, introduce usuario y contraseña", Toast.LENGTH_SHORT).show()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }


    private fun login(usuarioId: String, passwordIngresada: String) {
        lifecycleScope.launch {
            try {
                val client = getClient()
                //val supabaseResponse = client.postgrest["users"].select()

                val users: List<User> = client
                    .postgrest["usuarios"] // Reemplaza "usuarios" con el nombre real de tu tabla
                    .select(columns = Columns.raw("usuario_id, password")) {
                        filter {
                            eq("usuario_id", usuarioId)
                        }
                    }
                    .decodeList()


                if (users.isNotEmpty()) {
                    val user = users.first()
                    if (user.password == passwordIngresada) {
                        // Login exitoso
                        Toast.makeText(this@Login_activity, "Bienvenido " + user.usuario_id, Toast.LENGTH_SHORT).show()

                        // Redirigir a la pantalla principal
                        val intent = Intent(this@Login_activity, InicioActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // Contraseña incorrecta
                        Toast.makeText(this@Login_activity, "Usuario o contraseña incorrectos, verifique de nuevo", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Usuario no encontrado
                    Toast.makeText(this@Login_activity, "El usuario no existe, registre nuevo usuario", Toast.LENGTH_SHORT).show()
                }


            } catch (e: Exception) {
                Log.e("supabase", "Error al iniciar sesión: ${e.message}", e)
                Toast.makeText(this@Login_activity, "Error al iniciar sesión: ${e.message}", Toast.LENGTH_SHORT).show() // Mostrar error al usuario
            }
        }
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