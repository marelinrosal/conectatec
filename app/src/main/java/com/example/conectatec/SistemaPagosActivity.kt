package com.example.conectatec

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences // Importar SharedPreferences
import android.os.*
import android.util.Log // Importar Log
import android.view.GestureDetector
import android.view.MenuItem
import android.view.MotionEvent
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope // Importar lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.stripe.android.PaymentConfiguration
import io.github.jan.supabase.SupabaseClient // Importar SupabaseClient
import io.github.jan.supabase.createSupabaseClient // Importar createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest // Importar Postgrest
import io.github.jan.supabase.postgrest.postgrest // Importar postgrest
import io.github.jan.supabase.postgrest.query.Columns // Importar Columns (opcional)
import kotlinx.coroutines.launch // Importar launch
import kotlinx.serialization.Serializable // Importar Serializable
import android.view.ViewGroup //



// --- Data class para la información del ticket ---
data class TicketInfo(
    val url: String,
    val tipo: String, // "urbano" o "interurbano"
    val duracion: String, // "dia", "semana", "mes", "viaje"
    val costo: Double // Usar Double para el costo
)

// --- Data class para la tabla transaccion ---
@Serializable
data class TransaccionData(
    val codigo_qr: String,
    val usuario_id: String,
    val tipo_qr: String,
    val duracion_qr: String,
    val costo_qr: Double // Coincide con numeric/decimal en la BD
)


class SistemaPagosActivity : AppCompatActivity() {
    private lateinit var btnPagar: Button
    private var selectedTicketInfo: TicketInfo? = null // Guardará la info del ticket seleccionado
    private val stripePublishableKey = "pk_test_TU_CLAVE_PUBLICA" // Reemplaza con tu clave publica REAL
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var usuarioInteractuando = false

    private lateinit var sharedPreferences: SharedPreferences // Para obtener userId
    private lateinit var supabaseClient: SupabaseClient // Cliente Supabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sistema_pagos)

        // Inicializar SharedPreferences y Supabase Client
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        supabaseClient = getClient() // Inicializar cliente

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar2)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sistema de Pagos" // Título más descriptivo

        PaymentConfiguration.init(this, stripePublishableKey)

        btnPagar = findViewById(R.id.btnPagar)
        btnPagar.isEnabled = false

        val grupoInterurbano = findViewById<RadioGroup>(R.id.grupoInterurbano)
        val grupoUrbano = findViewById<RadioGroup>(R.id.grupoUrbano)

        // --- MAPA ACTUALIZADO CON PRECIOS CORRECTOS ---
        val ticketInfoMap = mapOf(
            // Urbano
            R.id.diaUrbano to TicketInfo("https://buy.stripe.com/test_fZe3gj2qceQi7DibIJ", "urbano", "dia", 30.0),
            R.id.semanaUrbano to TicketInfo("https://buy.stripe.com/test_00g9EHfcYdMe6ze4gj", "urbano", "semana", 150.0),
            R.id.mesUrbano to TicketInfo("https://buy.stripe.com/test_aEU7wzc0M37AaPuaEJ", "urbano", "mes", 600.0),
            R.id.viajeUrbano to TicketInfo("https://buy.stripe.com/test_28o7wzaWIeQi1eU5ks", "urbano", "viaje", 15.0),

            // Interurbano
            R.id.diaInterurbano to TicketInfo("https://buy.stripe.com/test_9AQeZ11m86jM5vacMO", "interurbano", "dia", 120.0),
            R.id.semanaInterurbano to TicketInfo("https://buy.stripe.com/test_bIYg358OAeQif5KdQU", "interurbano", "semana", 540.0),
            R.id.mesInterurbano to TicketInfo("https://buy.stripe.com/test_28o18b5CoaA2bTyeV0", "interurbano", "mes", 2040.0),
            R.id.viajeInterurbano to TicketInfo("https://buy.stripe.com/test_5kAg35d4Q0ZsbTy9AH", "interurbano", "viaje", 60.0)
        )
        // --- FIN DEL MAPA ACTUALIZADO ---

        grupoInterurbano.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                grupoUrbano.clearCheck()
                selectedTicketInfo = ticketInfoMap[checkedId] // Guardar TicketInfo
                btnPagar.isEnabled = true
            } else {
                // Si se deselecciona
                if (grupoUrbano.checkedRadioButtonId == -1) {
                    selectedTicketInfo = null
                    btnPagar.isEnabled = false
                }
            }
        }

        grupoUrbano.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                grupoInterurbano.clearCheck()
                selectedTicketInfo = ticketInfoMap[checkedId] // Guardar TicketInfo
                btnPagar.isEnabled = true
            } else {
                if (grupoInterurbano.checkedRadioButtonId == -1) {
                    selectedTicketInfo = null
                    btnPagar.isEnabled = false
                }
            }
        }

        // --- OnClickListener del botón Pagar con lógica de inserción ---
        btnPagar.setOnClickListener {
            val currentTicketInfo = selectedTicketInfo
            val userId = getLoggedInUserId()

            if (currentTicketInfo != null && userId != null) {
                // Generar código QR simple (timestamp + userId para unicidad básica)
                // Puedes mejorar esto si necesitas un consecutivo real desde la BD
                val codigoQrGenerado = "QR-${System.currentTimeMillis()}-${userId.take(5)}"

                // Crear objeto de transacción
                val transaccion = TransaccionData(
                    codigo_qr = codigoQrGenerado,
                    usuario_id = userId,
                    tipo_qr = currentTicketInfo.tipo,
                    duracion_qr = currentTicketInfo.duracion,
                    costo_qr = currentTicketInfo.costo
                )

                // Lanzar corrutina para insertar en Supabase
                lifecycleScope.launch {
                    try {
                        Log.d("SupabaseInsert", "Intentando insertar: $transaccion")
                        supabaseClient.postgrest["transaccion"].insert(transaccion)
                        Log.i("SupabaseInsert", "Transacción registrada exitosamente con código: ${transaccion.codigo_qr}")
                        Toast.makeText(this@SistemaPagosActivity, "Registro de transacción exitoso", Toast.LENGTH_SHORT).show()

                        // --- SOLO SI LA INSERCIÓN ES EXITOSA, PROCEDER AL PAGO ---
                        mostrarBottomSheet(currentTicketInfo.url)

                    } catch (e: Exception) {
                        Log.e("SupabaseInsert", "Error al registrar transacción: ${e.message}", e)
                        Toast.makeText(this@SistemaPagosActivity, "Error al registrar la transacción. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
                        // No proceder a mostrarBottomSheet si falla el registro
                    }
                }

            } else if (userId == null) {
                Toast.makeText(this, "Error: No se pudo obtener el ID de usuario. Intenta reiniciar sesión.", Toast.LENGTH_LONG).show()
                // Opcional: Redirigir al login
                // logout() // Necesitarías implementar o importar esta función si la usas
            } else {
                Toast.makeText(this, "Selecciona una opción de boleto primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Función para obtener el ID de usuario logueado ---
    private fun getLoggedInUserId(): String? {
        return sharedPreferences.getString(Constants.KEY_USER_ID, null)
    }

    // --- Función para obtener el cliente Supabase ---
    private fun getClient(): SupabaseClient {
        // ¡¡¡MALA PRÁCTICA!!! NO INCRUSTAR CLAVES EN EL CÓDIGO.
        // Considera usar BuildConfig, secrets.gradle o variables de entorno.
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc", // Reemplaza con tu clave ANON real
        ) {
            install(Postgrest)
        }
    }

    // --- Lógica del WebView y BottomSheet (sin cambios funcionales) ---

    private fun mostrarBottomSheet(url: String) {
        val bottomSheetDialog = BottomSheetDialog(this)
        // Verificar si la vista ya existe y removerla si es necesario (prevención de errores)
        if (::webView.isInitialized && webView.parent != null) {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
        webView = WebView(this) // Crear nueva instancia

        // Limpiar caché y historial ANTES de configurar
        webView.clearCache(true)
        webView.clearHistory()
        CookieManager.getInstance().removeAllCookies(null) // Limpiar cookies también
        CookieManager.getInstance().flush()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE // Forzar no usar caché

        var pagoExitoso = false
        var cierreManual = true // Asumir cierre manual hasta que se detecte éxito/cancelación

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                usuarioInteractuando = true
                Log.d("WebViewInteraction", "onDown detectado")
                return super.onDown(e)
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                usuarioInteractuando = true
                Log.d("WebViewInteraction", "onScroll detectado")
                return super.onScroll(e1, e2, distanceX, distanceY)
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                usuarioInteractuando = true
                Log.d("WebViewInteraction", "onSingleTapUp detectado")
                return super.onSingleTapUp(e)
            }
        })

        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Devolver false para que el WebView maneje el evento normalmente
            false
        }

        webView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                usuarioInteractuando = true
                Log.d("WebViewInteraction", "onFocusChange detectado (hasFocus=true)")
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("WebView", "Iniciando carga de: $url")
                usuarioInteractuando = false // Reiniciar interacción al cargar nueva página
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("WebView", "Página cargada: $url")

                // Ajusta estas URLs a las que Stripe USA REALMENTE para éxito y cancelación
                val successUrlPattern = "success" // Simplificado, ajusta si es más complejo
                val cancelUrlPattern = "cancel"   // Simplificado, ajusta

                if (url != null) {
                    if (url.contains(successUrlPattern, ignoreCase = true)) {
                        Log.i("WebView", "URL de éxito detectada: $url")
                        pagoExitoso = true
                        cierreManual = false // El flujo terminó automáticamente
                        handler.postDelayed({
                            if (bottomSheetDialog.isShowing) {
                                Toast.makeText(this@SistemaPagosActivity, "Pago exitoso. Redirigiendo...", Toast.LENGTH_SHORT).show()
                                bottomSheetDialog.dismiss() // Cerrar el diálogo
                                // No redirigir desde aquí, esperar al onDismissListener para la lógica final
                            }
                        }, 1500) // Retraso para mostrar mensaje
                    } else if (url.contains(cancelUrlPattern, ignoreCase = true)) {
                        Log.w("WebView", "URL de cancelación detectada: $url")
                        pagoExitoso = false
                        cierreManual = false // El flujo terminó automáticamente
                        handler.postDelayed({
                            if (bottomSheetDialog.isShowing) {
                                Toast.makeText(this@SistemaPagosActivity, "Pago cancelado.", Toast.LENGTH_SHORT).show()
                                bottomSheetDialog.dismiss() // Cerrar el diálogo
                                // No redirigir, el usuario decide qué hacer
                            }
                        }, 1500) // Retraso
                    }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.e("WebViewError", "Error: ${error?.errorCode} ${error?.description} en ${request?.url}")
                } else {
                    Log.e("WebViewError", "Error cargando ${request?.url}")
                }
                // Considera mostrar un mensaje al usuario o intentar recargar
                // Toast.makeText(this@SistemaPagosActivity, "Error de red al cargar la página.", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheetDialog.setOnDismissListener {
            Log.d("BottomSheet", "Cerrado. Exitoso: $pagoExitoso, Cierre Manual: $cierreManual")
            handler.removeCallbacksAndMessages(null) // Detener cualquier recarga pendiente

            if (pagoExitoso) {
                redirigirAHome() // Redirigir a Home SOLO si el pago fue exitoso
            } else if (cierreManual) {
                // Si se cerró manualmente sin éxito ni cancelación detectada
                Toast.makeText(this@SistemaPagosActivity, "Transacción no completada.", Toast.LENGTH_LONG).show()
            }
            // Si fue cancelación detectada (cierreManual=false, pagoExitoso=false), no se hace nada extra aquí.
        }

        Log.d("WebView", "Cargando URL: $url")
        webView.loadUrl(url)
        bottomSheetDialog.setContentView(webView)
        bottomSheetDialog.show()

        usuarioInteractuando = false // Reiniciar estado de interacción
        iniciarRecargaAutomatica(bottomSheetDialog)
    }

    private fun iniciarRecargaAutomatica(bottomSheetDialog: BottomSheetDialog) {
        handler.removeCallbacksAndMessages(null) // Limpiar callbacks anteriores

        handler.postDelayed(object : Runnable {
            override fun run() {
                if (bottomSheetDialog.isShowing && ::webView.isInitialized && webView.parent != null) {
                    if (!usuarioInteractuando) {
                        Log.d("WebViewReload", "Recargando WebView...")
                        webView.reload()
                    } else {
                        Log.d("WebViewReload", "Omitiendo recarga (usuario interactuando)")
                    }
                    usuarioInteractuando = false // Resetear para el próximo ciclo
                    handler.postDelayed(this, 7000) // Reprogramar
                } else {
                    Log.d("WebViewReload", "Deteniendo recarga (BottomSheet no visible o WebView no listo)")
                }
            }
        }, 7000) // Iniciar después de 7 segundos
    }

    private fun redirigirAHome() {
        val intent = Intent(this, InicioActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish() // Finalizar esta actividad para que no quede en la pila
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish() // Acción estándar para el botón "Up" o "Atrás"
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar el handler para evitar fugas de memoria
        handler.removeCallbacksAndMessages(null)
        // Destruir WebView explícitamente si es necesario
        if (::webView.isInitialized) {
            webView.destroy()
        }
        Log.d("SistemaPagosActivity", "onDestroy llamado")
    }
}