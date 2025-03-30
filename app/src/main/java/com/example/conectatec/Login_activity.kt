package com.example.conectatec

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import io.github.jan.supabase.postgrest.postgrest // ✅ Importación correcta
import android.util.Log // ✅ Importar esto
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.messaging.FirebaseMessaging


class Login_activity : AppCompatActivity() {

    data class User(
        val id: Int = 0,
        val name: String = "",
        val email: String = "",
        val phone: String = "",
        val username: String = "",
        var password: String = ""
        )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //habilito el metodo getData()
        getData()

        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        val tvRegister = findViewById<TextView>(R.id.lblregistrar)
        val btnLogin = findViewById<Button>(R.id.btningresar) // Referencia al botón


        tvRegister.setOnClickListener {
            // Crear Intent para iniciar la nueva Activity
            val intent = Intent(this, Registro_activity::class.java)
            startActivity(intent)
        }

        // Click en "Iniciar sesión"
        btnLogin.setOnClickListener {
            // Validar credenciales aquí (si es necesario)

            // Redirigir a la pantalla principal
            val intent = Intent(this, InicioActivity::class.java)
            // Limpia la pila de actividades y crea una nueva tarea
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish() // Opcional, pero ayuda a asegurar el cierre
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        FirebaseApp.initializeApp(this)

        getFCMToken()

    }

    // ✅ Función para obtener datos de Supabase con manejo de errores
    private fun getData() {
        // ✅ Utilizar lifecycleScope para manejar las corrutinas
        lifecycleScope.launch {
            try {
                val client = getClient()
                val supabaseResponse = client.postgrest["users"].select()
                // ✅ Decodificar la respuesta a una lista de objetos User
                val data = supabaseResponse.decodeList<User>()

                Log.e("supabase", data.toString()) // ✅ Corregido

            } catch (e: Exception) {
                Log.e("supabase", "Error obteniendo datos: ${e.message}", e)
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

    //Obtener el token para firebase
    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM_TOKEN", "Error al obtener el token FCM", task.exception)
                return@addOnCompleteListener
            }

            // Obtener el token FCM
            val token = task.result

            // Mostrar en Logcat
            Log.d("FCM_TOKEN", "Token FCM: $token")

            // Mostrar en un Toast para poder copiarlo fácilmente
            //Toast.makeText(this, "Token FCM: $token", Toast.LENGTH_LONG).show()
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