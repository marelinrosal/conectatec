package com.example.conectatec

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences // Importar SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
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
import io.github.jan.supabase.postgrest.postgrest
import android.util.Log
import android.widget.EditText // Importar EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable // Asegúrate de tener esta importación

// --- Definición del Data Class para la respuesta del login ---
// Mantén la que necesitas para el login. Si necesitas la otra en otro lugar,
// dale un nombre diferente (ej. UserProfile)
@Serializable
data class UserLoginData( // Renombrado para evitar confusión si tienes otro 'User'
    val usuario_id: String = "",
    val password: String = ""
)

// --- Constantes para SharedPreferences ---
private const val PREFS_NAME = "AppSessionPrefs"
private const val KEY_IS_LOGGED_IN = "isLoggedIn"
private const val KEY_USER_ID = "userId"


class Login_activity : AppCompatActivity() {

    // Mover la declaración de SharedPreferences aquí para fácil acceso
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Comprobación de Sesión Iniciada ---
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (isUserLoggedIn()) {
            // Si el usuario ya inició sesión, ir directamente a InicioActivity
            goToInicioActivity()
            return // Importante: Salir de onCreate para no mostrar la pantalla de login
        }
        // --- Fin de Comprobación de Sesión ---


        // El resto del código solo se ejecuta si el usuario NO ha iniciado sesión
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // --- Referencias a Vistas (añadido EditText) ---
        val tvRegister = findViewById<TextView>(R.id.lblregistrar)
        val btnLogin = findViewById<Button>(R.id.btningresar)
        val txtUsuario = findViewById<EditText>(R.id.txtusuario) // Añadido
        val txtContrasena = findViewById<EditText>(R.id.txtcontrasena) // Añadido

        // --- Listener para Registro ---
        tvRegister.setOnClickListener {
            val intent = Intent(this, Registro_activity::class.java)
            startActivity(intent)
        }

        // --- Listener para Iniciar Sesión (Actualizado) ---
        btnLogin.setOnClickListener {
            val usuario = txtUsuario.text.toString().trim() // Usar trim para quitar espacios
            val contrasena = txtContrasena.text.toString()

            if (usuario.isNotEmpty() && contrasena.isNotEmpty()) {
                // Iniciar el proceso de login con Supabase
                login(usuario, contrasena)
            } else {
                Toast.makeText(this, "Por favor, introduce usuario y contraseña", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Ajuste de Insets ---
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- Inicialización de Firebase y obtención de Token ---
        // Nota: La inicialización de Firebase idealmente se hace una vez en la clase Application
        try {
            FirebaseApp.initializeApp(this)
            getFCMToken()
            // Considera solicitar permiso de notificación aquí si es relevante para el login
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            //     requestNotificationPermission()
            // }
        } catch (e: IllegalStateException) {
            Log.w("FirebaseInit", "FirebaseApp ya inicializado: ${e.message}")
            // Si ya está inicializado (puede pasar), obtener el token igualmente
            getFCMToken()
        }

        // La llamada a getData() original parece ser para probar/obtener todos los usuarios.
        // Probablemente no la necesites aquí en el flujo de login normal.
        // getData() // Comentado o eliminado si no es necesario aquí
    }

    // --- Función de Login con Supabase (Actualizada) ---
    private fun login(usuarioId: String, passwordIngresada: String) {
        lifecycleScope.launch {
            try {
                val client = getClient()

                // Consulta a Supabase para obtener el usuario por usuario_id
                val usersResponse: List<UserLoginData> = client
                    .postgrest["usuarios"] // Asegúrate que "usuarios" es el nombre correcto de tu tabla
                    .select(columns = Columns.raw("usuario_id, password")) { // Selecciona solo los campos necesarios
                        filter {
                            eq("usuario_id", usuarioId) // Filtra por el usuario_id ingresado
                        }
                    }
                    .decodeList() // Decodifica la respuesta a una lista de UserLoginData

                if (usersResponse.isNotEmpty()) {
                    val user = usersResponse.first() // Obtiene el primer usuario (debería ser único)

                    // --- ¡¡¡ADVERTENCIA DE SEGURIDAD IMPORTANTE!!! ---
                    // Comparar contraseñas en texto plano como aquí es MUY INSEGURO.
                    // Deberías usar Supabase Auth (que maneja hashing seguro) o implementar
                    // hashing de contraseñas en tu backend si usas Postgrest directamente.
                    // Esta comparación es solo para fines demostrativos con tu código actual.
                    if (user.password == passwordIngresada) {
                        // Login exitoso
                        Toast.makeText(this@Login_activity, "Bienvenido ${user.usuario_id}", Toast.LENGTH_SHORT).show()

                        // --- Guardar Estado de Sesión ---
                        saveLoginState(user.usuario_id)

                        // Redirigir a la pantalla principal
                        goToInicioActivity()

                    } else {
                        // Contraseña incorrecta
                        Toast.makeText(this@Login_activity, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Usuario no encontrado
                    Toast.makeText(this@Login_activity, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                // Manejo de errores (red, decodificación, etc.)
                Log.e("SupabaseLogin", "Error al iniciar sesión: ${e.message}", e)
                Toast.makeText(this@Login_activity, "Error al conectar. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Funciones de SharedPreferences para Sesión ---

    private fun saveLoginState(userId: String) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_USER_ID, userId) // Guarda el ID del usuario si lo necesitas después
        editor.apply() // apply() es asíncrono y preferido sobre commit()
    }

    private fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false) // Devuelve false si no se encuentra
    }

    // Puedes crear una función para obtener el ID del usuario guardado desde otras Activities
    // companion object { // Si quieres acceder desde otras clases fácilmente
    //    fun getLoggedInUserId(context: Context): String? {
    //        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    //        return prefs.getString(KEY_USER_ID, null) // Devuelve null si no está logueado o no se guardó
    //    }
    // }

    // --- Función para navegar a InicioActivity ---
    private fun goToInicioActivity() {
        val intent = Intent(this, InicioActivity::class.java)
        // Limpia la pila de actividades para que el usuario no pueda volver al Login con el botón "Atrás"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Cierra Login_Activity
    }


    // --- Cliente Supabase (Sin cambios, pero ¡CUIDADO CON LAS KEYS!) ---
    private fun getClient(): SupabaseClient {
        // ¡¡¡MALA PRÁCTICA!!! No pongas las claves directamente en el código.
        // Usa variables de entorno, Gradle secrets, o un sistema de configuración seguro.
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc",
        ) {
            install(Postgrest)
        }
    }

    // --- Obtener Token FCM (Sin cambios) ---
    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM_TOKEN", "Error al obtener el token FCM", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("FCM_TOKEN", "Token FCM: $token")
            // Toast.makeText(this, "Token FCM: $token", Toast.LENGTH_LONG).show() // Descomentar si necesitas verlo
        }
    }

    // --- Permiso de Notificaciones (Sin cambios) ---
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    // --- Función getData() original (Comentada/Opcional) ---
    /*
    private fun getData() {
        lifecycleScope.launch {
            try {
                val client = getClient()
                // Necesitarías un data class 'User' adecuado si usas esto
                // val supabaseResponse = client.postgrest["users"].select()
                // val data = supabaseResponse.decodeList<User>() // Asegúrate que 'User' exista y coincida
                // Log.e("supabase", data.toString())
                 Log.d("SupabaseData", "Función getData() llamada")

            } catch (e: Exception) {
                Log.e("supabase", "Error obteniendo datos: ${e.message}", e)
            }
        }
    }
    */
}