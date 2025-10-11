package com.example.conectatec
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.query.Columns


class Perfil_Activity : AppCompatActivity() {

    private lateinit var txtUsuario: EditText
    private lateinit var txtNombre: EditText
    private lateinit var txtEmail: EditText
    private lateinit var txtTelefono: EditText
    private lateinit var txtClave: EditText
    private lateinit var btnEditar: Button

    private var modoEditar = false
    private var passwordVisible = false
    // SharedPreferences y Supabase Client
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var supabaseClient: SupabaseClient
    private var currentUserId: String? = null // ID del usuario logueado


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        txtUsuario = findViewById(R.id.txtusuario)
        txtNombre = findViewById(R.id.txtnomapellidos)
        txtEmail = findViewById(R.id.txtemail)
        txtTelefono = findViewById(R.id.txttelefono)
        txtClave = findViewById(R.id.txtclave)
        btnEditar = findViewById(R.id.btnregistrar)

        txtUsuario.isEnabled = false // usuario siempre bloqueado
        // Bloqueo de campos al inicio
        setCamposEditable(false)
        // Inicialización de SharedPreferences y Supabase Client
        sharedPreferences = getSharedPreferences(Constantes.PREFS_NAME, Context.MODE_PRIVATE)
        supabaseClient = getClient()
        currentUserId = getLoggedInUserId()

        // Configuración de la Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbarEditar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Botón de regreso
        supportActionBar?.title = "Perfil"

        // Verifica si se pudo obtener el ID del usuario.
        if (currentUserId == null) {
            Log.e("WalletFragment", "No se encontro el usuario.") // Log existente
            Toast.makeText(this, "Error: Usuario no identificado.", Toast.LENGTH_LONG).show()
            return // No continuar si no hay usuario.
        }
        getCargarDatos()
        mostrarContraseña()

        btnEditar.setOnClickListener {
            if (!modoEditar) {
                modoEditar = true
                setCamposEditable(true)
                txtUsuario.isEnabled = false
                btnEditar.text = "GUARDAR"
            } else {
                if (validarPerfil()) {
                    actualizarDatos()
                } else {
                    Toast.makeText(this, "Corrige los errores antes de guardar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun getCargarDatos() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = supabaseClient.postgrest["usuarios"]
                    .select(Columns.list("usuario_id", "nombre", "email", "telefono", "password", "nombre_usuario")) {
                        filter {
                            eq("usuario_id", currentUserId!!)
                        }
                    }

                val usuarios = result.decodeList<Usuario>()

                if (usuarios.isNotEmpty()) {
                    val user = usuarios.first()

                    runOnUiThread {
                        txtUsuario.setText(user.usuario_id)
                        txtNombre.setText(user.nombre)
                        txtEmail.setText(user.email)
                        txtTelefono.setText(user.telefono)
                        txtClave.setText(user.password)
                        txtUsuario.setText(user.nombre_usuario)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@Perfil_Activity,
                            "No se encontraron datos del usuario.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("Perfil_Activity", "Error al cargar datos: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@Perfil_Activity,
                        "Error al cargar datos del usuario",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun actualizarDatos() {
        val nombre = txtNombre.text.toString().trim()
        val email = txtEmail.text.toString().trim()
        val telefono = txtTelefono.text.toString().trim()
        val password = txtClave.text.toString().trim()

        if (nombre.isEmpty() || email.isEmpty() || telefono.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Todos los campos deben estar llenos", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Actualiza el registro usando Supabase
                supabaseClient.postgrest["usuarios"].update(
                    mapOf(
                        "nombre" to nombre,
                        "email" to email,
                        "telefono" to telefono,
                        "password" to password
                    )
                ) {
                    filter { eq("usuario_id", currentUserId!!) }
                }


                runOnUiThread {
                    Toast.makeText(this@Perfil_Activity, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                    modoEditar = false
                    setCamposEditable(false)
                    btnEditar.text = "EDITAR"
                }
            } catch (e: Exception) {
                Log.e("Perfil_Activity", "Error al actualizar perfil: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@Perfil_Activity, "Error al actualizar perfil", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }




    private fun setCamposEditable(habilitar: Boolean) {
        txtNombre.isEnabled = habilitar
        txtEmail.isEnabled = habilitar
        txtTelefono.isEnabled = habilitar
        txtClave.isEnabled = habilitar

        if (!habilitar) {
            setPasswordHidden()
        }
    }


    private fun getLoggedInUserId(): String? {
        // Verifica si sharedPreferences ha sido inicializada antes de usarla.
        if (!::sharedPreferences.isInitialized) {
            Log.e("WalletFragment2", "SharedPreferences not initialized in getLoggedInUserId") // Log existente
            return null
        }
        return sharedPreferences.getString(Constantes.KEY_USER_ID, null)
    }
    private fun getClient(): SupabaseClient {
        // ¡¡¡MALA PRÁCTICA DE SEGURIDAD!!! NO INCRUSTAR CLAVES DE API EN EL CÓDIGO FUENTE.
        return createSupabaseClient(
            supabaseUrl = "https://uqltgfifxwliboccsxko.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVxbHRnZmlmeHdsaWJvY2NzeGtvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTk0MzAyODAsImV4cCI6MjA3NTAwNjI4MH0.EuLdr9RVIMRFLXR6VhxGqoskET6EUcb_B9zsPqe-bO0",
        ) {
            install(Postgrest)
        }
    }
    //Método para ver la contraseña
    private fun mostrarContraseña() {
        txtClave.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = 2
                val drawable = txtClave.compoundDrawables[drawableEnd]
                if (drawable != null) {
                    if (event.rawX >= (txtClave.right - drawable.bounds.width())) {
                        passwordVisible = !passwordVisible
                        if (passwordVisible) {
                            txtClave.inputType =
                                android.text.InputType.TYPE_CLASS_TEXT or
                                        android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        } else {
                            txtClave.inputType =
                                android.text.InputType.TYPE_CLASS_TEXT or
                                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        }
                        txtClave.setSelection(txtClave.text.length)
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun setPasswordHidden() {
        passwordVisible = false
        txtClave.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        txtClave.setSelection(txtClave.text.length)
    }
    /**
     * Maneja la selección de ítems en el menú de la ActionBar (ej: botón de regreso).
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { // Si se presiona el botón de "home" (flecha de regreso)
                Log.d("WebViewDebug", "onOptionsItemSelected: Botón Home presionado.")
                finish() // Finalizar la actividad actual
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun validarPerfil(): Boolean {
        var isValid = true

        // Nombre y apellidos
        val nombreStr = txtNombre.text.toString().trim()
        if (nombreStr.isEmpty()) {
            txtNombre.error = "Por favor, ingrese su nombre y apellidos"
            isValid = false
        } else if (!nombreStr.contains(" ")) {
            txtNombre.error = "Ingrese nombre y apellido separados por espacio"
            isValid = false
        }

        // Email
        val emailStr = txtEmail.text.toString().trim()
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        if (emailStr.isEmpty()) {
            txtEmail.error = "Por favor, ingrese su correo electrónico"
            isValid = false
        } else if (!emailStr.matches(emailPattern.toRegex())) {
            txtEmail.error = "Ingrese un correo electrónico válido"
            isValid = false
        }

        // Teléfono
        val telefonoStr = txtTelefono.text.toString().trim()
        if (telefonoStr.isEmpty()) {
            txtTelefono.error = "Por favor, ingrese su número de teléfono"
            isValid = false
        } else if (telefonoStr.length != 10) {
            txtTelefono.error = "El número debe tener exactamente 10 dígitos"
            isValid = false
        } else if (!telefonoStr.matches("\\d+".toRegex())) {
            txtTelefono.error = "Ingrese solo números"
            isValid = false
        }

        // Contraseña
        val passwordStr = txtClave.text.toString()
        if (passwordStr.isEmpty()) {
            txtClave.error = "Por favor, ingrese una contraseña"
            isValid = false
        } else if (passwordStr.length < 6) {
            txtClave.error = "La contraseña debe tener al menos 6 caracteres"
            isValid = false
        } else if (!isPasswordStrong(passwordStr)) {
            txtClave.error = "La contraseña debe incluir al menos una letra y un número"
            isValid = false
        }

        return isValid
    }

    private fun isPasswordStrong(password: String): Boolean {
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }



}