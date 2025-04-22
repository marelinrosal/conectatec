package com.example.conectatec

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
// import com.example.conectatec.util.Constants // <-- LÍNEA ANTERIOR COMENTADA/ELIMINADA
import com.example.conectatec.Constantes // <-- CAMBIO AQUÍ: Importar desde el paquete raíz
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class UserLoginData(
    val usuario_id: String = "",
    val password: String = "" // ADVERTENCIA: Inseguro
)

class Login_activity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(Constantes.PREFS_NAME, Context.MODE_PRIVATE) // Usa constante importada
        if (isUserLoggedIn()) {
            goToInicioActivity()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        val tvRegister = findViewById<TextView>(R.id.lblregistrar)
        val btnLogin = findViewById<Button>(R.id.btningresar)
        val txtUsuario = findViewById<EditText>(R.id.txtusuario)
        val txtContrasena = findViewById<EditText>(R.id.txtcontrasena)

        tvRegister.setOnClickListener {
            val intent = Intent(this, Registro_activity::class.java)
            startActivity(intent)
        }

        btnLogin.setOnClickListener {
            val usuario = txtUsuario.text.toString().trim()
            val contrasena = txtContrasena.text.toString()

            if (usuario.isNotEmpty() && contrasena.isNotEmpty()) {
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

        try {
            FirebaseApp.initializeApp(this)
            getFCMToken()
        } catch (e: IllegalStateException) {
            Log.w("FirebaseInit", "FirebaseApp ya inicializado: ${e.message}")
            getFCMToken()
        }
    }

    private fun login(usuarioId: String, passwordIngresada: String) {
        lifecycleScope.launch {
            try {
                val client = getClient()
                val usersResponse: List<UserLoginData> = client
                    .postgrest["usuarios"]
                    .select(columns = Columns.raw("usuario_id, password")) {
                        filter { eq("usuario_id", usuarioId) }
                    }
                    .decodeList()

                if (usersResponse.isNotEmpty()) {
                    val user = usersResponse.first()
                    if (user.password == passwordIngresada) { // ADVERTENCIA: Inseguro
                        Toast.makeText(this@Login_activity, "Bienvenido ${user.usuario_id}", Toast.LENGTH_SHORT).show()
                        saveLoginState(user.usuario_id) // Usa constante importada
                        goToInicioActivity()
                    } else {
                        Toast.makeText(this@Login_activity, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@Login_activity, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SupabaseLogin", "Error al iniciar sesión: ${e.message}", e)
                Toast.makeText(this@Login_activity, "Error de conexión. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveLoginState(userId: String) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(Constantes.KEY_IS_LOGGED_IN, true) // Usa constante importada
        editor.putString(Constantes.KEY_USER_ID, userId)     // Usa constante importada
        editor.apply()
    }

    private fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(Constantes.KEY_IS_LOGGED_IN, false) // Usa constante importada
    }

    private fun goToInicioActivity() {
        val intent = Intent(this, InicioActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun getClient(): SupabaseClient {
        // ¡¡¡MALA PRÁCTICA!!! NO INCRUSTAR CLAVES EN EL CÓDIGO.
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc",
        ) {
            install(Postgrest)
        }
    }

    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM_TOKEN", "Error al obtener el token FCM", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("FCM_TOKEN", "Token FCM obtenido: $token")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}