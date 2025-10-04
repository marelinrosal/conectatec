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
// import androidx.core.app.NotificationCompat // No se usa directamente
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.conectatec.Constantes // Asegura que la importación sea correcta
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Estructura de datos para deserializar la respuesta de la consulta de login desde Supabase.
 * <p>
 * <b>ADVERTENCIA DE SEGURIDAD:</b> Contiene el campo `password`. Almacenar y comparar
 * contraseñas en texto plano es una práctica insegura. Las contraseñas deben ser
 * hasheadas y salteadas en el backend.
 * </p>
 * @property usuario_id El identificador único del usuario.
 * @property password La contraseña del usuario (en texto plano, inseguro).
 */
@Serializable
data class UserLoginData(
    val usuario_id: String = "",
    val password: String = "" // ADVERTENCIA: Inseguro, la contraseña no debería recuperarse ni compararse en texto plano.
)

/**
 * Actividad responsable del proceso de inicio de sesión de los usuarios.
 * <p>
 * Presenta un formulario para ingresar credenciales (ID de usuario y contraseña).
 * Valida las credenciales contra una base de datos Supabase.
 * Si el login es exitoso, guarda el estado de la sesión en [SharedPreferences]
 * y navega a [InicioActivity]. También ofrece una opción para redirigir a
 * [Registro_activity] para nuevos usuarios.
 * </p>
 * <p>
 * Inicializa Firebase y obtiene el token FCM para notificaciones.
 * Maneja la comprobación de sesión previa para omitir el login si ya existe una sesión activa.
 * </p>
 * <p>
 * <b>ADVERTENCIAS DE SEGURIDAD:</b>
 * <ul>
 *   <li><b>Comparación de contraseñas en texto plano:</b> La lógica de login actual compara
 *       contraseñas en texto plano, lo cual es extremadamente inseguro. Implementar hashing y salting.</li>
 *   <li><b>Claves de Supabase hardcodeadas:</b> Las URL y claves de API de Supabase están
 *       directamente en el código, lo cual es un riesgo de seguridad.</li>
 * </ul>
 * </p>
 *
 * @property sharedPreferences Instancia de [SharedPreferences] para gestionar el estado de la sesión.
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see InicioActivity
 * @see Registro_activity
 * @see Constantes
 * @see SupabaseClient
 */
class Login_activity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    /**
     * Se llama cuando la actividad está iniciando.
     * <p>
     * Responsabilidades:
     * <ul>
     *   <li>Inicializa [SharedPreferences].</li>
     *   <li>Comprueba si el usuario ya ha iniciado sesión; si es así, navega a [InicioActivity].</li>
     *   <li>Configura la interfaz de usuario (edge-to-edge, layout).</li>
     *   <li>Inicializa los componentes de la UI (campos de texto, botones).</li>
     *   <li>Configura listeners para el botón de login y el texto de registro.</li>
     *   <li>Aplica window insets para el layout principal.</li>
     *   <li>Inicializa FirebaseApp y obtiene el token FCM.</li>
     *   <li>(Opcional, si es necesario) Solicita permiso de notificaciones en Android 13+.</li>
     * </ul>
     * </p>
     * @param savedInstanceState Si la actividad se está re-inicializando, este Bundle contiene
     *                           los datos más recientes suministrados en [onSaveInstanceState].
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa SharedPreferences para la gestión de sesión.
        sharedPreferences = getSharedPreferences(Constantes.PREFS_NAME, Context.MODE_PRIVATE)

        // Si el usuario ya está logueado, ir directamente a InicioActivity.
        if (isUserLoggedIn()) {
            goToInicioActivity()
            return // Importante: Salir de onCreate para evitar configurar la UI de login.
        }

        enableEdgeToEdge() // Habilita el modo edge-to-edge para la UI.
        setContentView(R.layout.activity_login)

        // Inicialización de vistas
        val tvRegister = findViewById<TextView>(R.id.lblregistrar)
        val btnLogin = findViewById<Button>(R.id.btningresar)
        val txtUsuario = findViewById<EditText>(R.id.txtusuario)
        val txtContrasena = findViewById<EditText>(R.id.txtcontrasena)

        // Listener para el texto de registro, navega a Registro_activity.
        tvRegister.setOnClickListener {
            val intent = Intent(this, Registro_activity::class.java)
            startActivity(intent)
        }

        // Listener para el botón de login.
        btnLogin.setOnClickListener {
            val usuario = txtUsuario.text.toString().trim()
            val contrasena = txtContrasena.text.toString()

            if (usuario.isNotEmpty() && contrasena.isNotEmpty()) {
                login(usuario, contrasena) // Intenta iniciar sesión.
            } else {
                Toast.makeText(this, "Por favor, introduce usuario y contraseña", Toast.LENGTH_SHORT).show()
            }
        }

        // Aplica padding para evitar que el contenido se superponga con las barras del sistema.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializa FirebaseApp. Es seguro llamarlo múltiples veces,
        // pero se usa try-catch para manejar la excepción si ya está inicializado (opcional).
        try {
            FirebaseApp.initializeApp(this)
            getFCMToken() // Obtiene el token FCM después de inicializar Firebase.
        } catch (e: IllegalStateException) {
            Log.w("FirebaseInit", "FirebaseApp ya inicializado: ${e.message}")
            // Aun así, intentar obtener el token, ya que la app podría estar inicializada de antes.
            getFCMToken()
        }

        // Si la app se dirige a Android 13 (API 33) o superior, solicitar permiso de notificaciones.
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        //     requestNotificationPermission()
        // }
    }

    /**
     * Intenta autenticar al usuario con el ID de usuario y contraseña proporcionados
     * utilizando Supabase.
     * <p>
     * Realiza una consulta a la tabla "usuarios" de Supabase para encontrar un usuario
     * con el `usuarioId` dado y luego compara la `passwordIngresada` con la contraseña
     * almacenada.
     * </p>
     * <p>
     * <b>ADVERTENCIA DE SEGURIDAD:</b> Este método compara contraseñas en texto plano.
     * Esto es altamente inseguro. Las contraseñas deben ser hasheadas en el servidor
     * durante el registro, y la contraseña ingresada aquí debe ser hasheada antes de
     * compararla con el hash almacenado, preferiblemente mediante una función de Supabase
     * o un endpoint de API seguro.
     * </p>
     *
     * @param usuarioId El ID de usuario ingresado.
     * @param passwordIngresada La contraseña ingresada.
     * @see getClient
     * @see UserLoginData
     * @see saveLoginState
     * @see goToInicioActivity
     */
    private fun login(usuarioId: String, passwordIngresada: String) {
        lifecycleScope.launch { // Ejecuta la operación de red en una corutina.
            try {
                val client = getClient() // Obtiene el cliente Supabase.
                // Realiza la consulta a Supabase para obtener el usuario y su contraseña.
                // ADVERTENCIA: Seleccionar la contraseña para compararla en el cliente es inseguro.
                val usersResponse: List<UserLoginData> = client
                    .postgrest["usuarios"] // Accede a la tabla "usuarios".
                    .select(columns = Columns.raw("usuario_id, password")) { // Selecciona solo usuario_id y password.
                        filter { eq("usuario_id", usuarioId) } // Filtra por el usuario_id proporcionado.
                    }
                    .decodeList() // Deserializa la respuesta en una lista de UserLoginData.

                if (usersResponse.isNotEmpty()) {
                    val user = usersResponse.first() // Obtiene el primer (y se espera único) usuario.
                    // ADVERTENCIA DE SEGURIDAD CRÍTICA: Comparación de contraseñas en texto plano.
                    if (user.password == passwordIngresada) {
                        Toast.makeText(this@Login_activity, "Bienvenido ${user.usuario_id}", Toast.LENGTH_SHORT).show()
                        saveLoginState(user.usuario_id) // Guarda el estado de login.
                        goToInicioActivity() // Navega a la actividad principal.
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

    /**
     * Guarda el estado de inicio de sesión del usuario en [SharedPreferences].
     * Almacena un booleano indicando que el usuario está logueado y su ID.
     *
     * @param userId El ID del usuario que ha iniciado sesión.
     * @see Constantes.KEY_IS_LOGGED_IN
     * @see Constantes.KEY_USER_ID
     */
    private fun saveLoginState(userId: String) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(Constantes.KEY_IS_LOGGED_IN, true)
        editor.putString(Constantes.KEY_USER_ID, userId)
        editor.apply() // Aplica los cambios de forma asíncrona.
    }

    /**
     * Comprueba si hay un usuario actualmente logueado consultando [SharedPreferences].
     *
     * @return `true` si el usuario está logueado, `false` en caso contrario.
     * @see Constantes.KEY_IS_LOGGED_IN
     */
    private fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(Constantes.KEY_IS_LOGGED_IN, false)
    }

    /**
     * Navega a [InicioActivity] y finaliza la [Login_activity] actual.
     * Utiliza flags para limpiar la pila de actividades, evitando que el usuario
     * pueda volver a la pantalla de login mediante el botón "Atrás".
     */
    private fun goToInicioActivity() {
        val intent = Intent(this, InicioActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Cierra LoginActivity.
    }

    /**
     * Crea y configura una instancia del cliente Supabase.
     * <p>
     * <b>ADVERTENCIA DE SEGURIDAD:</b> Las credenciales de Supabase (URL y clave anónima)
     * están hardcodeadas en este método. Esto es una mala práctica de seguridad.
     * Estas credenciales deben ser almacenadas de forma segura fuera del código fuente,
     * por ejemplo, en archivos de propiedades locales (no versionados) y accedidas
     * a través de `BuildConfig`, o mediante variables de entorno en un sistema de CI/CD.
     * </p>
     * @return Una instancia configurada de [SupabaseClient].
     */
    private fun getClient(): SupabaseClient {
        // ¡¡¡MALA PRÁCTICA DE SEGURIDAD!!! NO INCRUSTAR CLAVES DE API EN EL CÓDIGO FUENTE.
        // Estas claves pueden ser extraídas si el APK es descompilado.
        // Considerar usar Gradle properties, variables de entorno o un backend proxy.
        return createSupabaseClient(
            supabaseUrl =  "https://uqltgfifxwliboccsxko.supabase.co", // URL de tu proyecto Supabase
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVxbHRnZmlmeHdsaWJvY2NzeGtvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTk0MzAyODAsImV4cCI6MjA3NTAwNjI4MH0.EuLdr9RVIMRFLXR6VhxGqoskET6EUcb_B9zsPqe-bO0", // Clave anónima pública de Supabase
        ) {
            install(Postgrest) // Instala el plugin Postgrest para interactuar con la base de datos.
        }
    }

    /**
     * Obtiene el token de registro de Firebase Cloud Messaging (FCM).
     * Este token es necesario para que el dispositivo pueda recibir notificaciones push.
     * El token se registra en Logcat. En una aplicación real, este token
     * usualmente se enviaría al servidor backend para asociarlo con el usuario.
     */
    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM_TOKEN", "Error al obtener el token FCM", task.exception)
                return@addOnCompleteListener
            }
            // Obtiene el token FCM.
            val token = task.result
            Log.d("FCM_TOKEN", "Token FCM obtenido en LoginActivity: $token")
            // Aquí podrías guardar el token en SharedPreferences o enviarlo a tu servidor
            // si es necesario en el momento del login, aunque usualmente se maneja
            // en InicioActivity o un servicio dedicado.
        }
    }

    /**
     * Solicita el permiso `POST_NOTIFICATIONS` si la aplicación se ejecuta en Android 13 (API 33)
     * o superior y aún no se ha concedido.
     * <p>
     * Este método está marcado con `@RequiresApi(Build.VERSION_CODES.TIRAMISU)` y
     * debe ser llamado condicionalmente (por ejemplo, `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)`).
     * </p>
     * <p>
     * La gestión de la respuesta a esta solicitud de permiso (en `onRequestPermissionsResult`)
     * no está implementada en este fragmento de código.
     * </p>
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        // Comprueba si el permiso ya ha sido concedido.
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            // Si no, solicita el permiso. El resultado se maneja en onRequestPermissionsResult.
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100) // 100 es el requestCode.
        }
    }
}