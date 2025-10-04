package com.example.conectatec

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// import androidx.lifecycle.lifecycleScope // No se usa directamente en este scope, CoroutineScope es usado.
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString // No se usa directamente jsonUsuario
import kotlinx.serialization.json.Json     // No se usa directamente jsonUsuario

/**
 * Clase de datos que representa la información de un usuario para el registro.
 * <p>
 * Esta clase es serializable para facilitar su uso con Supabase Postgrest.
 * Contiene todos los campos necesarios para crear un nuevo registro de usuario en la base de datos.
 * </p>
 * <p>
 * <b>ADVERTENCIA DE SEGURIDAD:</b> El campo `password` se maneja en texto plano.
 * Esto es una práctica insegura. Las contraseñas deben ser hasheadas y salteadas
 * en el backend (preferiblemente usando Supabase Auth o funciones seguras de Supabase)
 * antes de ser almacenadas.
 * </p>
 *
 * @property usuario_id El identificador único del usuario, usualmente el mismo que `nombre_usuario` para este esquema.
 * @property nombre El nombre completo del usuario.
 * @property email La dirección de correo electrónico del usuario.
 * @property telefono El número de teléfono del usuario.
 * @property nombre_usuario El nombre de usuario elegido para el inicio de sesión.
 * @property password La contraseña elegida por el usuario (en texto plano, inseguro).
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 */
@Serializable
data class Usuario(
    val usuario_id: String, // Considerar si esto debería ser generado por la BD o ser igual a nombre_usuario
    val nombre: String,
    val email: String,
    val telefono: String,
    val nombre_usuario: String,
    val password: String // ADVERTENCIA: Inseguro almacenar/transmitir en texto plano.
)

/**
 * Actividad responsable del proceso de registro de nuevos usuarios.
 * <p>
 * Presenta un formulario donde los usuarios pueden ingresar sus datos personales,
 * nombre de usuario y contraseña. Realiza validaciones en los campos de entrada.
 * Si los datos son válidos y el nombre de usuario no existe previamente,
 * registra al nuevo usuario en la base de datos Supabase.
 * </p>
 * <p>
 * <b>ADVERTENCIAS DE SEGURIDAD:</b>
 * <ul>
 *   <li><b>Manejo de Contraseñas:</b> Las contraseñas se envían y (presumiblemente) almacenan
 *       en texto plano, lo cual es una grave vulnerabilidad. Se debe implementar hashing
 *       y salting en el backend.</li>
 *   <li><b>Claves de Supabase Hardcodeadas:</b> Las URL y claves de API de Supabase están
 *       directamente en el código, lo cual es un riesgo de seguridad.</li>
 * </ul>
 * </p>
 *
 * @property nombreApellidos Campo de texto para el nombre y apellidos.
 * @property email Campo de texto para el correo electrónico.
 * @property telefono Campo de texto para el número de teléfono.
 * @property usuario Campo de texto para el nombre de usuario.
 * @property clave Campo de texto para la contraseña.
 * @property btnRegistrar Botón para iniciar el proceso de registro.
 * @property tvGoBack TextView para navegar de regreso a la pantalla de inicio de sesión.
 * @property TAG Etiqueta para logging, con valor "RegistroActivity".
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see Login_activity
 * @see Usuario
 * @see SupabaseClient
 */
class Registro_activity : AppCompatActivity() {

    // Declaración de elementos de la UI
    private lateinit var nombreApellidos: EditText
    private lateinit var email: EditText
    private lateinit var telefono: EditText
    private lateinit var usuario: EditText // Nombre de usuario
    private lateinit var clave: EditText   // Contraseña
    private lateinit var btnRegistrar: Button
    private lateinit var tvGoBack: TextView
    private val TAG = "RegistroActivity" // Etiqueta para logs

    /**
     * Se llama cuando la actividad está iniciando.
     * <p>
     * Inicializa la interfaz de usuario, establece los listeners para los botones
     * (registro y regreso a login).
     * </p>
     * @param savedInstanceState Si la actividad se está re-inicializando, este Bundle contiene
     *                           los datos más recientes suministrados en [onSaveInstanceState].
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register) // Asume que R.layout.activity_register existe

        // Inicialización de los elementos de la UI
        nombreApellidos = findViewById(R.id.txtnomapellidos)
        email = findViewById(R.id.txtemail)
        telefono = findViewById(R.id.txttelefono)
        usuario = findViewById(R.id.txtusuario)
        clave = findViewById(R.id.txtclave)
        btnRegistrar = findViewById(R.id.btnregistrar)
        tvGoBack = findViewById(R.id.lbliniciarsesion) // Asumo que este es el ID correcto

        // Configura el listener para el TextView que permite regresar a la pantalla de login.
        tvGoBack.setOnClickListener {
            finish() // Cierra la actividad actual y regresa a la anterior (Login_activity).
        }

        // Configura el listener para el botón de registro.
        btnRegistrar.setOnClickListener {
            if (validateInputs()) { // Si todas las validaciones son exitosas...
                registerUser()      // ...procede a registrar al usuario.
            }
        }
        Log.d(TAG, "Registro_activity creada y UI inicializada.")
    }

    /**
     * Valida todos los campos de entrada del formulario de registro.
     * <p>
     * Comprueba que los campos no estén vacíos y cumplan con formatos específicos
     * (email, teléfono, longitud de usuario y contraseña, etc.). Muestra mensajes de error
     * directamente en los [EditText] si la validación falla.
     * </p>
     * @return `true` si todas las validaciones son exitosas, `false` en caso contrario.
     */
    private fun validateInputs(): Boolean {
        var isValid = true
        Log.d(TAG, "Iniciando validación de entradas.")

        // Validar nombre y apellidos
        if (nombreApellidos.text.toString().trim().isEmpty()) {
            nombreApellidos.error = "Por favor, ingrese su nombre y apellidos"
            isValid = false
        } else if (!nombreApellidos.text.toString().trim().contains(" ")) {
            // Esta validación asume "Nombre Apellido". Podría ser más flexible.
            nombreApellidos.error = "Ingrese nombre y apellido separados por espacio"
            isValid = false
        }

        // Validar email
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+" // Patrón simple de email
        if (email.text.toString().trim().isEmpty()) {
            email.error = "Por favor, ingrese su correo electrónico"
            isValid = false
        } else if (!email.text.toString().trim().matches(emailPattern.toRegex())) {
            email.error = "Ingrese un correo electrónico válido"
            isValid = false
        }

        // Validar teléfono
        val telefonoStr = telefono.text.toString().trim()
        if (telefonoStr.isEmpty()) {
            telefono.error = "Por favor, ingrese su número de teléfono"
            isValid = false
        } else if (telefonoStr.length != 10) { // Longitud mínima de 9 dígitos, ajustar según necesidad
            telefono.error = "El número debe tener exactamente 10 dígitos"
            isValid = false
        } else if (!telefonoStr.matches("\\d+".toRegex())) { // Solo dígitos
            telefono.error = "Ingrese solo números"
            isValid = false
        }

        // Validar nombre de usuario
        val usernameStr = usuario.text.toString().trim()
        if (usernameStr.isEmpty()) {
            usuario.error = "Por favor, ingrese un nombre de usuario"
            isValid = false
        } else if (usernameStr.contains(" ")) {
            usuario.error = "El usuario no puede contener espacios"
            isValid = false
        } else if (usernameStr.length < 4) {
            usuario.error = "El usuario debe tener al menos 4 caracteres"
            isValid = false
        }

        // Validar contraseña
        val passwordStr = clave.text.toString() // No trim() para contraseñas, espacios pueden ser intencionales.
        if (passwordStr.isEmpty()) {
            clave.error = "Por favor, ingrese una contraseña"
            isValid = false
        } else if (passwordStr.length < 6) { // Longitud mínima de 6 caracteres
            clave.error = "La contraseña debe tener al menos 6 caracteres"
            isValid = false
        } else if (!isPasswordStrong(passwordStr)) {
            clave.error = "La contraseña debe incluir al menos una letra y un número"
            isValid = false
        }

        Log.d(TAG, "Resultado de validación: $isValid")
        return isValid
    }

    /**
     * Comprueba si una contraseña cumple con los criterios de fortaleza básicos (contiene letras y números).
     * @param password La contraseña a verificar.
     * @return `true` si la contraseña contiene al menos una letra y al menos un dígito, `false` en caso contrario.
     */
    private fun isPasswordStrong(password: String): Boolean {
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }

    /**
     * Registra un nuevo usuario en la base de datos Supabase.
     * <p>
     * Primero, verifica si el `nombreUsuario` ya existe en la base de datos.
     * Si no existe, crea un objeto [Usuario] con los datos del formulario e intenta
     * insertarlo en la tabla "usuarios" de Supabase.
     * </p>
     * <p>
     * <b>ADVERTENCIA DE SEGURIDAD:</b> La contraseña se envía en texto plano.
     * Debe ser hasheada en el backend (Supabase Functions o Supabase Auth) antes de almacenarse.
     * </p>
     * Las operaciones de red se realizan en una corutina en el hilo de IO.
     * @see getClient
     * @see Usuario
     * @see Login_activity
     */
    private fun registerUser() {
        Log.d(TAG, "Iniciando proceso de registro de usuario.")
        val nombre = nombreApellidos.text.toString().trim()
        val correo = email.text.toString().trim()
        val telefonoNum = telefono.text.toString().trim() // Renombrado para evitar colisión con la propiedad
        val nombreUsuario = usuario.text.toString().trim()
        val password = clave.text.toString() // Sin trim() para contraseñas

        val client = getClient() // Obtiene el cliente Supabase

        // Ejecuta la lógica de red y base de datos en un hilo de fondo.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Verifica si el nombre de usuario ya existe.
                Log.d(TAG, "Verificando si el usuario '$nombreUsuario' ya existe.")
                val existingUserResponse = client.postgrest["usuarios"]
                    .select { filter { eq("nombre_usuario", nombreUsuario) } }
                    .decodeList<Usuario>() // Decodifica la respuesta.

                if (existingUserResponse.isNotEmpty()) {
                    // El usuario ya existe, muestra un mensaje en el hilo principal.
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "El nombre de usuario '$nombreUsuario' ya está registrado. Por favor, elige otro.", Toast.LENGTH_LONG).show()
                        usuario.error = "Este nombre de usuario ya existe" // Marca el campo de usuario
                        usuario.requestFocus()
                    }
                    Log.w(TAG, "Intento de registro con nombre de usuario existente: $nombreUsuario")
                } else {
                    // El usuario no existe, procede con el registro.
                    Log.d(TAG, "Nombre de usuario '$nombreUsuario' disponible. Procediendo con el registro.")
                    val nuevoUsuario = Usuario(
                        usuario_id = nombreUsuario, // Asigna el nombre de usuario como ID de usuario.
                        nombre = nombre,
                        email = correo,
                        telefono = telefonoNum,
                        nombre_usuario = nombreUsuario,
                        password = password  // ADVERTENCIA: Contraseña en texto plano. Implementar hashing en backend.
                    )

                    // Inserta el nuevo usuario en la tabla "usuarios".
                    // Supabase maneja la serialización del objeto 'nuevoUsuario' automáticamente.
                    client.postgrest["usuarios"].insert(nuevoUsuario)
                    Log.i(TAG, "Usuario '$nombreUsuario' insertado correctamente en Supabase.")

                    // Registro exitoso, muestra mensaje y navega a LoginActivity en el hilo principal.
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Usuario registrado correctamente. ¡Bienvenido!", Toast.LENGTH_LONG).show()
                        val intent = Intent(applicationContext, Login_activity::class.java)
                        startActivity(intent)
                        finish() // Cierra Registro_activity para que no se pueda volver con "Atrás".
                    }
                }
            } catch (e: Exception) {
                // Error durante el proceso de red o base de datos.
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Error al registrar el usuario: ${e.localizedMessage ?: "Error desconocido"}", Toast.LENGTH_LONG).show()
                }
                Log.e(TAG, "Error durante el registro del usuario '$nombreUsuario'", e)
            }
        }
    }

    /**
     * Crea y configura una instancia del cliente Supabase.
     * <p>
     * <b>ADVERTENCIA DE SEGURIDAD:</b> Las credenciales de Supabase (URL y clave anónima)
     * están hardcodeadas en este método. Esto es una mala práctica de seguridad.
     * Estas credenciales deben ser almacenadas de forma segura fuera del código fuente.
     * </p>
     * @return Una instancia configurada de [SupabaseClient].
     */
    private fun getClient(): SupabaseClient {
        // ¡¡¡MALA PRÁCTICA DE SEGURIDAD!!! NO INCRUSTAR CLAVES DE API EN EL CÓDIGO FUENTE.
        return createSupabaseClient(
            supabaseUrl =  "https://uqltgfifxwliboccsxko.supabase.co", // URL de tu proyecto Supabase
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVxbHRnZmlmeHdsaWJvY2NzeGtvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTk0MzAyODAsImV4cCI6MjA3NTAwNjI4MH0.EuLdr9RVIMRFLXR6VhxGqoskET6EUcb_B9zsPqe-bO0", // Clave anónima pública de Supabase
        ) {
            install(Postgrest) // Instala el plugin Postgrest.
        }
    }
}