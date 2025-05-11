package com.example.conectatec

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.stripe.android.PaymentConfiguration
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Clase de datos para almacenar la información de un tipo de ticket
 * que el usuario puede seleccionar para comprar.
 
 */
data class TicketInfo(
    val url: String,
    val tipo: String,
    val duracion: String,
    val costo: Double,
    val fecha: String
)

/**
 * Clase de datos para representar una transacción de compra de un ticket.
 * Esta clase está diseñada para ser serializada y almacenada en la base de datos Supabase.
 *
 */
@Serializable
data class TransaccionData(
    val codigo_qr: String,
    val usuario_id: String,
    val tipo_qr: String,
    val duracion_qr: String,
    val costo_qr: Double,
    @SerialName("fechaCompra")
    val fecha_compra: String
)

/**
 * `SistemaPagosActivity` es la pantalla donde los usuarios pueden seleccionar
 * y pagar por diferentes tipos de tickets de transporte utilizando Stripe.
 * Maneja la selección de tickets, la interacción con la pasarela de pago de Stripe
 * a través de un WebView, y el registro de transacciones exitosas en Supabase.
 */
class SistemaPagosActivity : AppCompatActivity() {
    // Componentes de la UI
    private lateinit var btnPagar: Button // Botón para iniciar el proceso de pago.
    private var selectedTicketInfo: TicketInfo? = null // Almacena la información del ticket actualmente seleccionado por el usuario.

    // Configuración de Stripe
    // Clave publicable de Stripe para identificar esta aplicación con Stripe.
    // IMPORTANTE: Esta es una clave de prueba. Para producción, usa tu clave publicable de producción.
    private val stripePublishableKey = "pk_test_51R5VpuLlYPVHsiF1QcQnwjIuiXdv8hZ0l1uJe3dvVyzVplOBn22XXClKKYeyl7JHQu9oqPJ2wwXiO0AVPa0wQdFi00WMA5kqln"

    // WebView para mostrar la pasarela de pago de Stripe
    private lateinit var webView: WebView // Se usa para cargar las URLs de pago de Stripe.

    // Handler y estado de la transacción
    // Handler para ejecutar tareas en el hilo principal (UI).
    private val mHandler = Handler(Looper.getMainLooper())
    // Almacena temporalmente los datos de la transacción que se guardarán si el pago es exitoso.
    private var transaccionPendiente: TransaccionData? = null

    // Dependencias
    private lateinit var sharedPreferences: SharedPreferences // Para acceder a datos guardados localmente, como el ID de usuario.
    private lateinit var supabaseClient: SupabaseClient // Cliente para interactuar con la base de datos Supabase.

    // Variables para el Poller de URL (chequeo periódico de la URL del WebView)
    private var pollerRunnable: Runnable? = null // La tarea que se ejecuta periódicamente para verificar la URL del WebView.
    private var pollCount = 0 // Contador de cuántas veces se ha ejecutado el poller para la transacción actual.
    private val MAX_POLLS = 300  // Límite de ejecuciones del poller (ej: 300 * 2s = 10 minutos de espera).
    private val POLL_INTERVAL = 3000L // Intervalo entre chequeos del poller (3 segundos).

    /**
     * Enum para representar los posibles resultados de la operación del poller de URL.
     * Ayuda a comunicar el estado del poller a otras partes del código.
     */
    enum class PollerResult {
        SUCCESS,                // El poller detectó la URL de éxito.
        TIMEOUT,                // El poller alcanzó el número máximo de intentos sin éxito.
        CANCELLED_BY_NAVIGATION,// El poller fue detenido porque el WebView navegó a una URL no esperada o el usuario canceló.
        NONE                    // Estado inicial o el poller no se ejecutó/fue detenido sin un resultado específico.
    }

    /**
     * Se llama cuando la actividad es creada por primera vez.
     * Aquí se inicializa la interfaz de usuario, se configuran los listeners de los botones,
     * y se preparan las dependencias como Stripe y Supabase.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sistema_pagos)
        Log.d("WebViewDebug", "SistemaPagosActivity onCreate")

        // Configura SharedPreferences para almacenar el ID de usuario y contadores de escaneos.
        sharedPreferences = getSharedPreferences(Constantes.PREFS_NAME, MODE_PRIVATE)
        // Inicializa el cliente de Supabase para interactuar con la base de datos.
        supabaseClient = getClient()

        // Configura la Toolbar con título y botón de regreso.
        val toolbar = findViewById<Toolbar>(R.id.toolbar2)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sistema de Pagos"

        // Inicializa Stripe con la clave publicable de prueba.
        PaymentConfiguration.init(this, stripePublishableKey)

        // Obtiene referencias a la UI: botón de pagar y grupos de RadioButtons.
        btnPagar = findViewById(R.id.btnPagar)
        btnPagar.isEnabled = false // Deshabilitado hasta seleccionar un ticket.
        val grupoInterurbano = findViewById<RadioGroup>(R.id.grupoInterurbano)
        val grupoUrbano = findViewById<RadioGroup>(R.id.grupoUrbano)
        val currentDate = Instant.now().toString() // Fecha actual para los tickets.

        // Mapea IDs de RadioButtons a objetos TicketInfo con URLs de Stripe y detalles.
        val ticketInfoMap = mapOf(
            R.id.diaUrbano to TicketInfo("https://buy.stripe.com/test_fZe3gj2qceQi7DibIJ", "urbano", "dia", 30.0, currentDate),
            R.id.semanaUrbano to TicketInfo("https://buy.stripe.com/test_00g9EHfcYdMe6ze4gj", "urbano", "semana", 150.0, currentDate),
            R.id.mesUrbano to TicketInfo("https://buy.stripe.com/test_aEU7wzc0M37AaPuaEJ", "urbano", "mes", 600.0, currentDate),
            R.id.viajeUrbano to TicketInfo("https://buy.stripe.com/test_28o7wzaWIeQi1eU5ks", "urbano", "viaje", 15.0, currentDate),
            R.id.diaInterurbano to TicketInfo("https://buy.stripe.com/test_9AQeZ11m86jM5vacMO", "interurbano", "dia", 120.0, currentDate),
            R.id.semanaInterurbano to TicketInfo("https://buy.stripe.com/test_bIYg358OAeQif5KdQU", "interurbano", "semana", 540.0, currentDate),
            R.id.mesInterurbano to TicketInfo("https://buy.stripe.com/test_28o18b5CoaA2bTyeV0", "interurbano", "mes", 2040.0, currentDate),
            R.id.viajeInterurbano to TicketInfo("https://buy.stripe.com/test_5kAg35d4Q0ZsbTy9AH", "interurbano", "viaje", 60.0, currentDate)
        )

        // Listener para el grupo de tickets interurbanos: actualiza la selección y habilita el botón de pagar.
        grupoInterurbano.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                grupoUrbano.clearCheck() // Desmarca el grupo opuesto.
                selectedTicketInfo = ticketInfoMap[checkedId] // Guarda el ticket seleccionado.
                btnPagar.isEnabled = true // Habilita el botón de pagar.
            } else {
                // Si no hay selección en ambos grupos, desactiva el botón y limpia la selección.
                if (grupoUrbano.checkedRadioButtonId == -1) {
                    selectedTicketInfo = null
                    btnPagar.isEnabled = false
                }
            }
        }

        // Listener para el grupo de tickets urbanos: similar al anterior.
        grupoUrbano.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                grupoInterurbano.clearCheck()
                selectedTicketInfo = ticketInfoMap[checkedId]
                btnPagar.isEnabled = true
            } else {
                if (grupoInterurbano.checkedRadioButtonId == -1) {
                    selectedTicketInfo = null
                    btnPagar.isEnabled = false
                }
            }
        }

        // Listener del botón Pagar: inicia el proceso de pago si es válido.
        btnPagar.setOnClickListener {
            Log.d("WebViewDebug", "btnPagar Clicked")
            val currentTicketInfo = selectedTicketInfo
            val userId = getLoggedInUserId()

            if (currentTicketInfo != null && userId != null) {
                // Verifica en una corutina si el usuario tiene un ticket válido.
                lifecycleScope.launch {
                    val hasValidTicket = ticketValido(userId)
                    withContext(Dispatchers.Main) {
                        if (hasValidTicket) {
                            // Si hay un ticket válido, muestra un mensaje y detiene el proceso.
                            Log.d("WebViewDebug", "Usuario ya tiene un boleto vigente.")
                            Toast.makeText(this@SistemaPagosActivity, "Ya tienes un boleto vigente. No puedes comprar otro hasta que expire.", Toast.LENGTH_LONG).show()
                            return@withContext
                        }
                        // Crea una nueva transacción pendiente con un código QR único.
                        transaccionPendiente = TransaccionData(
                            codigo_qr = UUID.randomUUID().toString(),
                            usuario_id = userId,
                            tipo_qr = currentTicketInfo.tipo,
                            duracion_qr = currentTicketInfo.duracion,
                            costo_qr = currentTicketInfo.costo,
                            fecha_compra = currentTicketInfo.fecha
                        )
                        Log.d("WebViewDebug", "Transacción pendiente creada: ${transaccionPendiente?.codigo_qr}")
                        Toast.makeText(this@SistemaPagosActivity, "Procediendo al pago...", Toast.LENGTH_SHORT).show()
                        // Muestra el BottomSheet con el WebView para el pago.
                        mostrarBottomSheet(currentTicketInfo.url)
                    }
                }
            } else if (userId == null) {
                // Si no hay ID de usuario, muestra un error.
                Log.w("WebViewDebug", "Error: User ID es null.")
                Toast.makeText(this, "Error: No se pudo obtener el ID de usuario. Intenta reiniciar sesión.", Toast.LENGTH_LONG).show()
            } else {
                // Si no se seleccionó un ticket, muestra un error.
                Log.w("WebViewDebug", "Error: No se ha seleccionado un boleto.")
                Toast.makeText(this, "Selecciona una opción de boleto primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Verifica si el usuario especificado ya posee un ticket válido y activo.
     * Consulta la base de datos Supabase para la transacción más reciente del usuario
     * y determina su validez basada en el tipo, duración y uso (para tickets de "viaje").
     */
    private suspend fun ticketValido(userId: String): Boolean {
        // Verifica si el usuario tiene un ticket activo en Supabase.
        Log.d("WebViewDebug", "ticketValido: Verificando para usuario $userId")
        return try {
            // Consulta la transacción más reciente del usuario.
            val latestTransaction: InfoTransaccionWallet? = supabaseClient.postgrest["transaccion"]
                .select(Columns.list("usuario_id", "tipo_qr", "duracion_qr", "codigo_qr", "fechaCompra")) {
                    filter {
                        eq("usuario_id", userId)
                    }
                    order("fechaCompra", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull()

            // Si no hay transacciones, retorna false (sin ticket válido).
            if (latestTransaction == null) {
                Log.d("WebViewDebug", "ticketValido: No hay transacciones para $userId")
                return false
            }
            Log.d("WebViewDebug", "ticketValido: Última transacción encontrada ${latestTransaction.codigo_qr}")

            // Para tickets de viaje: valida por número de escaneos y tiempo.
            if (latestTransaction.duracion_qr == "viaje") {
                val scanCount = numeroEscaneos(latestTransaction.codigo_qr)
                val isExpiredByTime = TicketUtils.transaccionExpirada(latestTransaction)
                Log.d("WebViewDebug", "ticketValido: Boleto de viaje ${latestTransaction.codigo_qr}. Escaneos: $scanCount, Expirado por tiempo: $isExpiredByTime")
                if (scanCount >= 2 || isExpiredByTime) {
                    // Si tiene 2 o más escaneos, reinicia el contador.
                    if (scanCount >= 2) reinicioEscaneo(latestTransaction.codigo_qr)
                    return false // Ticket no válido.
                }
                return true // Ticket válido.
            }

            // Para otros tickets: valida solo por tiempo.
            val isExpired = TicketUtils.transaccionExpirada(latestTransaction)
            Log.d("WebViewDebug", "ticketValido: Boleto no-viaje ${latestTransaction.codigo_qr}. Expirado: $isExpired")
            return !isExpired // Válido si no ha expirado.
        } catch (e: Exception) {
            // En caso de error (ej. red), asume que no hay ticket válido.
            Log.e("WebViewDebug", "ticketValido: Error al verificar para $userId: ${e.message}", e)
            return false
        }
    }

    /**
     * Obtiene el número de veces que un ticket de tipo "viaje" (identificado por su `codigoQr`)
     * ha sido escaneado. Esta información se almacena en SharedPreferences.
     */
    private fun numeroEscaneos(codigoQr: String): Int {
        // Lee el contador de escaneos desde SharedPreferences.
        // Retorna 0 si no existe la clave (ticket no escaneado).
        return sharedPreferences.getInt("scan_count_$codigoQr", 0)
    }

    /**
     * Reinicia (elimina) el contador de escaneos para un ticket de tipo "viaje" específico
     * de SharedPreferences. Esto se hace típicamente cuando el ticket ha sido completamente utilizado.
     */
    private fun reinicioEscaneo(codigoQr: String) {
        // Elimina la entrada de escaneos en SharedPreferences para permitir nuevos usos.
        sharedPreferences.edit().remove("scan_count_$codigoQr").apply()
        Log.d("WebViewDebug", "reinicioEscaneo: Contador limpiado para $codigoQr")
    }

    /**
     * Obtiene el ID del usuario que ha iniciado sesión actualmente.
     * El ID se lee desde SharedPreferences, donde se asume que fue guardado al iniciar sesión.
     */
    private fun getLoggedInUserId(): String? {
        // Lee el ID de usuario almacenado en SharedPreferences.
        // Retorna null si no hay usuario autenticado.
        return sharedPreferences.getString(Constantes.KEY_USER_ID, null)
    }

    /**
     * Crea y configura una instancia del cliente de Supabase.
     * Este cliente se utiliza para todas las interacciones con la base de datos Supabase.

     */
    private fun getClient(): SupabaseClient {
        // Configura el cliente de Supabase con URL y clave anónima.
        // Instala el plugin Postgrest para consultas a la base de datos.
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc"
        ) {
            install(Postgrest)
        }
    }

    /**
     * Muestra un BottomSheetDialog que contiene un WebView. El WebView carga la URL de la
     * pasarela de pago de Stripe. Esta función también configura el `WebViewClient` para
     * monitorear los cambios de URL y detectar si el pago fue exitoso, cancelado, o si hubo un error.

     */
    private fun mostrarBottomSheet(originalUrlForWebView: String) {
        // Crea un BottomSheetDialog para mostrar la pasarela de pago.
        Log.d("WebViewDebug", "mostrarBottomSheet: Preparando para URL: $originalUrlForWebView")
        val bottomSheetDialog = BottomSheetDialog(this)

        // Limpia cualquier WebView existente para evitar conflictos.
        if (::webView.isInitialized && webView.parent != null) {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
        webView = WebView(this)
        // Detiene cualquier poller activo de URL.
        stopUrlPoller()

        // Limpia caché, historial y cookies para una sesión de pago limpia.
        webView.clearCache(true)
        webView.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        // Configura el WebView para soportar la pasarela de Stripe.
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        // Variables para rastrear el estado del pago.
        var pagoExitoso = false // Indica si el pago fue exitoso.
        var cierreManual = true // Indica si el usuario cerró el diálogo manualmente.
        var isInitialLoad = true // Marca la primera carga de la URL.
        var operacionCanceladaPorTimeout = false // Indica si el poller agotó el tiempo.

        // Define URLs clave para detectar éxito y cancelación.
        val miSuccessUrlGitHub = "https://star0110.github.io/"
        val stripeCancelPaymentLinkIndicator = "buy.stripe.com/c/pay/"

        Log.d("WebViewDebug", "mostrarBottomSheet: Vars init. pagoExitoso=$pagoExitoso, cierreManual=$cierreManual, isInitialLoad=$isInitialLoad")
        Log.d("WebViewDebug", "  Success URL (GitHub): $miSuccessUrlGitHub")
        Log.d("WebViewDebug", "  Cancel Indicator (Stripe): $stripeCancelPaymentLinkIndicator")
        Log.d("WebViewDebug", "  Original Payment Link URL (Stripe): $originalUrlForWebView")

        // Configura el WebViewClient para manejar eventos de navegación.
        webView.webViewClient = object : WebViewClient() {
            // Se ejecuta al iniciar la carga de una nueva URL.
            override fun onPageStarted(view: WebView?, currentUrl: String?, favicon: Bitmap?) {
                super.onPageStarted(view, currentUrl, favicon)
                Log.d("WebViewDebug", "onPageStarted: $currentUrl, isInitialLoad: $isInitialLoad")
                // Detiene el poller si se navega a una URL no esperada (ni inicial, ni éxito, ni Stripe intermedia).
                if (currentUrl != null &&
                    currentUrl != originalUrlForWebView &&
                    !currentUrl.startsWith(miSuccessUrlGitHub, ignoreCase = true) &&
                    !currentUrl.contains("checkout.stripe.com") &&
                    pollerRunnable != null) {
                    Log.d("WebViewDebug", "onPageStarted: Navegación a URL ($currentUrl) no esperada directamente, deteniendo poller si estaba en original.")
                    if (webView.url == originalUrlForWebView) {
                        stopUrlPoller(PollerResult.CANCELLED_BY_NAVIGATION)
                    }
                }
            }

            // Se ejecuta cuando una URL termina de cargarse.
            override fun onPageFinished(view: WebView?, currentUrl: String?) {
                super.onPageFinished(view, currentUrl)
                Log.d("WebViewDebug", "onPageFinished - URL: $currentUrl, isInitialLoad: $isInitialLoad, pagoExitoso: $pagoExitoso")

                // Maneja la carga inicial de la URL de Stripe.
                if (isInitialLoad && currentUrl == originalUrlForWebView) {
                    Log.d("WebViewDebug", "  -> Carga inicial de '$originalUrlForWebView'. Marcando isInitialLoad = false.")
                    isInitialLoad = false
                    if (!pagoExitoso) {
                        // Inicia el poller para verificar cambios de URL.
                        Log.d("WebViewDebug", "  -> Carga inicial completa, iniciando poller.")
                        startUrlPoller(bottomSheetDialog, originalUrlForWebView, miSuccessUrlGitHub, stripeCancelPaymentLinkIndicator) { detectedResult ->
                            when (detectedResult) {
                                PollerResult.SUCCESS -> {
                                    // Marca el pago como exitoso si no lo estaba.
                                    if (!pagoExitoso) {
                                        pagoExitoso = true
                                        cierreManual = false
                                    }
                                }
                                PollerResult.TIMEOUT -> {
                                    // Cierra el diálogo si el poller agota el tiempo.
                                    if (!pagoExitoso && bottomSheetDialog.isShowing) {
                                        Log.w("WebViewDebug", "    --> Poller TIMEOUT. Cerrando diálogo.")
                                        operacionCanceladaPorTimeout = true
                                        cierreManual = false
                                        Toast.makeText(this@SistemaPagosActivity, "Operación cancelada por tiempo límite.", Toast.LENGTH_LONG).show()
                                        bottomSheetDialog.dismiss()
                                    }
                                }
                                PollerResult.CANCELLED_BY_NAVIGATION -> {
                                    // Cierra el diálogo si se detecta cancelación.
                                    if (!pagoExitoso && bottomSheetDialog.isShowing) {
                                        Log.w("WebViewDebug", "    --> Poller detectó CANCELACIÓN (retorno a /c/pay/). Cerrando diálogo.")
                                        cierreManual = false
                                        Toast.makeText(this@SistemaPagosActivity, "Pago cancelado.", Toast.LENGTH_SHORT).show()
                                        bottomSheetDialog.dismiss()
                                    }
                                }
                                else -> { /* NONE */ }
                            }
                        }
                    }
                }
                // Maneja URLs posteriores a la carga inicial.
                else if (currentUrl != null && !isInitialLoad) {
                    Log.d("WebViewDebug", "  -> Evaluando URL (no inicial): $currentUrl")

                    // Detecta éxito si la URL es la de GitHub Pages.
                    if (currentUrl.startsWith(miSuccessUrlGitHub, ignoreCase = true)) {
                        Log.i("WebViewDebug", "    --> MI URL DE ÉXITO (GitHub: $miSuccessUrlGitHub) DETECTADA: $currentUrl")
                        if (!pagoExitoso) {
                            // Procesa el éxito y detiene el poller.
                            stopUrlPoller(PollerResult.SUCCESS)
                            procesarLogicaDeExito(bottomSheetDialog) { success ->
                                if (success) {
                                    pagoExitoso = true
                                    cierreManual = false
                                }
                            }
                        } else { Log.i("WebViewDebug", "      --> Éxito (GitHub) ya procesado.") }
                    }
                    // Detecta cancelación si la URL es de Stripe y no es éxito.
                    else if (currentUrl.contains(stripeCancelPaymentLinkIndicator, ignoreCase = false) &&
                        (currentUrl.startsWith(originalUrlForWebView.substringBefore("?").substringBefore("#"), ignoreCase = true) ||
                                currentUrl.contains("/c/pay/cs_live_") || currentUrl.contains("/c/pay/cs_test_")) &&
                        !currentUrl.startsWith(miSuccessUrlGitHub, ignoreCase = true) ) {
                        if (!pagoExitoso) {
                            // Cierra el diálogo tras un retraso si se detecta cancelación.
                            Log.w("WebViewDebug", "    --> URL DE CANCELACIÓN (/c/pay/) DETECTADA: $currentUrl");
                            cierreManual = false
                            stopUrlPoller(PollerResult.CANCELLED_BY_NAVIGATION)
                            mHandler.postDelayed({
                                if (bottomSheetDialog.isShowing) {
                                    Toast.makeText(this@SistemaPagosActivity, "Operación cancelada o abandonada.", Toast.LENGTH_SHORT).show()
                                    bottomSheetDialog.dismiss()
                                }
                            }, 300)
                        }
                    }
                    // Reinicia el poller si está en una página intermedia de Stripe.
                    else if (currentUrl.contains("checkout.stripe.com") &&
                        !currentUrl.startsWith(miSuccessUrlGitHub, ignoreCase = true) &&
                        pollerRunnable == null && !pagoExitoso) {
                        Log.d("WebViewDebug", "    --> En página intermedia de Stripe (checkout.stripe.com), poller no activo. Reiniciando poller.")
                        startUrlPoller(bottomSheetDialog, originalUrlForWebView, miSuccessUrlGitHub, stripeCancelPaymentLinkIndicator) { detectedResult ->
                            when (detectedResult) {
                                PollerResult.SUCCESS -> { if (!pagoExitoso) { pagoExitoso = true; cierreManual = false } }
                                PollerResult.TIMEOUT -> {
                                    if (!pagoExitoso && bottomSheetDialog.isShowing) {
                                        operacionCanceladaPorTimeout = true; cierreManual = false
                                        Toast.makeText(this@SistemaPagosActivity, "Operación cancelada por tiempo límite (reintento).", Toast.LENGTH_LONG).show()
                                        bottomSheetDialog.dismiss()
                                    }
                                }
                                PollerResult.CANCELLED_BY_NAVIGATION -> {
                                    if (!pagoExitoso && bottomSheetDialog.isShowing) {
                                        cierreManual = false
                                        Toast.makeText(this@SistemaPagosActivity, "Pago cancelado (poller).", Toast.LENGTH_SHORT).show()
                                        bottomSheetDialog.dismiss()
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                    else {
                        // Espera si la URL no es éxito ni cancelación.
                        Log.d("WebViewDebug", "    --> URL ($currentUrl) no es GitHub success ni cancelación /c/pay/. Esperando...")
                    }
                } else if (currentUrl == null) {
                    // Maneja URLs nulas temporalmente.
                    Log.w("WebViewDebug", "  -> onPageFinished: currentUrl es null.")
                }
                // Maneja redirecciones tempranas de Stripe.
                else if (isInitialLoad && currentUrl != originalUrlForWebView){
                    Log.d("WebViewDebug", "  -> onPageFinished: Aún en carga inicial (isInitialLoad=true), pero URL ($currentUrl) es diferente de la original ($originalUrlForWebView). Stripe redirigiendo.")
                    isInitialLoad = false
                }
            }

            // Maneja errores de carga en el WebView.
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                // Construye y muestra un mensaje de error según la versión de Android.
                val errorMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    "Error: ${error?.errorCode} ${error?.description} en ${request?.url}"
                } else {
                    "Error cargando ${request?.url}"
                }
                Log.e("WebViewError", errorMessage)
                Toast.makeText(this@SistemaPagosActivity, "Error cargando página de pago.", Toast.LENGTH_SHORT).show()
            }
        }

        // Configura el listener para cuando el BottomSheet se cierra.
        bottomSheetDialog.setOnDismissListener {
            Log.d("WebViewDebug", "BottomSheet DISMISS Listener. pagoExitoso: $pagoExitoso, cierreManual: $cierreManual, timeout: $operacionCanceladaPorTimeout")
            // Detiene el poller y limpia callbacks.
            stopUrlPoller(PollerResult.NONE)
            mHandler.removeCallbacksAndMessages(null)

            // Redirige a InicioActivity si el pago fue exitoso.
            if (pagoExitoso) {
                Log.i("WebViewDebug", "  --> Redirigiendo a QR porque pagoExitoso es true.")
                redirigirAQR()
            } else if (operacionCanceladaPorTimeout) {
                // No hace nada adicional si fue timeout (Toast ya mostrado).
                Log.w("WebViewDebug", "  --> Operación cancelada por timeout.")
            } else if (cierreManual) {
                // Muestra un mensaje si el usuario cerró manualmente.
                Log.w("WebViewDebug", "  --> Transacción no completada (cierre manual).")
                Toast.makeText(this@SistemaPagosActivity, "Transacción no completada.", Toast.LENGTH_LONG).show()
            } else {
                // Caso de cancelación explícita (Toast ya mostrado).
                Log.i("WebViewDebug", "  --> Pago no exitoso (no manual, no timeout). El Toast específico ya se mostró.")
            }
            // Limpia la transacción pendiente.
            transaccionPendiente = null
        }

        // Carga la URL de Stripe y muestra el BottomSheet.
        Log.d("WebViewDebug", "loadUrl: $originalUrlForWebView")
        webView.loadUrl(originalUrlForWebView)
        bottomSheetDialog.setContentView(webView)
        bottomSheetDialog.show()
    }

    /**
     * Procesa la lógica que debe ocurrir después de un pago exitoso.
     * Esto incluye guardar la transacción pendiente en Supabase.
     * Después de guardar, y si tiene éxito, se actualiza la UI (Toast) y se cierra el diálogo
     * a través del callback `onResult` y el `dismiss` posterior.
     *
     */
    private fun procesarLogicaDeExito(dialog: BottomSheetDialog, onResult: (Boolean) -> Unit) {
        // Procesa la transacción exitosa y la registra en Supabase.
        Log.i("WebViewDebug", "procesarLogicaDeExito: Procesando...")
        stopUrlPoller(PollerResult.SUCCESS)

        // Lanza una corutina para la operación de red.
        lifecycleScope.launch {
            var success = false
            try {
                transaccionPendiente?.let { transaccion ->
                    // Inserta la transacción en Supabase.
                    supabaseClient.postgrest["transaccion"].insert(transaccion)
                    Log.i("SupabaseInsert", "Transacción registrada: ${transaccion.codigo_qr}")
                    success = true
                } ?: run {
                    // Si no hay transacción pendiente, marca como fallo.
                    Log.w("SupabaseInsert", "Transacción pendiente era null al intentar registrar el éxito.")
                    success = false
                }
            } catch (e: Exception) {
                // Maneja errores de inserción (ej. red).
                Log.e("SupabaseInsert", "Error al registrar transacción en Supabase: ${e.message}", e)
                success = false
            }
            // Actualiza la UI en el hilo principal.
            withContext(Dispatchers.Main) {
                onResult(success)
                if (success) {
                    // Cierra el diálogo tras un retraso con un mensaje de éxito.
                    mHandler.postDelayed({
                        if (dialog.isShowing) {
                            Toast.makeText(this@SistemaPagosActivity, "Pago exitoso. Redirigiendo...", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    }, 500)
                } else {
                    // Muestra un error si la inserción falla.
                    Toast.makeText(this@SistemaPagosActivity, "Error al confirmar la transacción con la base de datos.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Inicia un "poller" que verifica periódicamente la URL actual del WebView.
     * Se utiliza como un mecanismo de respaldo para detectar cambios de URL (éxito o cancelación)
     * en caso de que los eventos `onPageFinished` no se disparen de manera predecible o
     * para capturar estados donde el usuario está en una página intermedia de Stripe.
     */
    private fun startUrlPoller(
        dialog: BottomSheetDialog,
        originalStripeUrl: String,
        successGitHubUrl: String,
        stripeCancelIndicator: String,
        onPollerComplete: (PollerResult) -> Unit
    ) {
        // Verifica que no haya un poller activo y que el WebView/dialog estén disponibles.
        if (pollerRunnable != null || !::webView.isInitialized) {
            Log.d("WebViewDebug", "startUrlPoller: Poller ya activo o WebView no lista.")
            return
        }
        if (!dialog.isShowing) {
            Log.d("WebViewDebug", "startUrlPoller: Dialogo no se muestra, no se inicia poller.")
            onPollerComplete(PollerResult.NONE)
            return
        }

        // Inicia el poller para verificar URLs periódicamente.
        Log.d("WebViewDebug", "startUrlPoller: Iniciando. SuccessGitHub: $successGitHubUrl, CancelIndicator: $stripeCancelIndicator, OriginalStripe: $originalStripeUrl")
        pollCount = 0

        // Crea el Runnable que verifica la URL cada POLL_INTERVAL.
        pollerRunnable = object : Runnable {
            override fun run() {
                // Detiene si el diálogo/WebView no están disponibles.
                if (!dialog.isShowing || !::webView.isInitialized || pollerRunnable == null) {
                    if (pollerRunnable != null) {
                        stopUrlPoller(PollerResult.NONE)
                        onPollerComplete(PollerResult.NONE)
                    }
                    return
                }

                // Incrementa el contador y obtiene la URL actual.
                pollCount++
                val actualUrl = webView.url
                Log.d("WebViewDebug", "  --> Poll #$pollCount/$MAX_POLLS: URL actual: $actualUrl")

                // Maneja URLs nulas (espera hasta MAX_POLLS).
                if (actualUrl == null) {
                    if (pollCount < MAX_POLLS) mHandler.postDelayed(this, POLL_INTERVAL) else {
                        Log.w("WebViewDebug", "    --> Poller TIMEOUT con URL null.")
                        stopUrlPoller(PollerResult.TIMEOUT)
                        onPollerComplete(PollerResult.TIMEOUT)
                    }
                    return
                }

                // Detecta éxito si la URL es la de GitHub Pages.
                if (actualUrl.startsWith(successGitHubUrl, ignoreCase = true)) {
                    Log.i("WebViewDebug", "    --> ÉXITO (GitHub) detectado por Poller!")
                    // Procesa el éxito y reporta el resultado.
                    procesarLogicaDeExito(dialog) { success ->
                        onPollerComplete(if (success) PollerResult.SUCCESS else PollerResult.NONE)
                    }
                }
                // Detecta posible cancelación si la URL es de Stripe.
                else if (actualUrl.contains(stripeCancelIndicator, ignoreCase = false) &&
                    (actualUrl.startsWith(originalStripeUrl.substringBefore("?").substringBefore("#"), ignoreCase = true) ||
                            actualUrl.contains("/c/pay/cs_live_") || actualUrl.contains("/c/pay/cs_test_")) &&
                    !actualUrl.startsWith(successGitHubUrl, ignoreCase = true) ) {
                    // Espera hasta MAX_POLLS para declarar timeout (cancelación fina en onPageFinished).
                    Log.d("WebViewDebug", "    --> Poller en URL de Stripe /c/pay/ ($actualUrl). Esperando éxito o timeout.")
                    if (pollCount >= MAX_POLLS) {
                        Log.w("WebViewDebug", "    --> Poller TIMEOUT en URL de Stripe /c/pay/.")
                        stopUrlPoller(PollerResult.TIMEOUT)
                        onPollerComplete(PollerResult.TIMEOUT)
                    } else {
                        mHandler.postDelayed(this, POLL_INTERVAL)
                    }
                }
                // Declara timeout si se excede MAX_POLLS.
                else if (pollCount >= MAX_POLLS) {
                    Log.w("WebViewDebug", "    --> Poller TIMEOUT. URL: $actualUrl.")
                    stopUrlPoller(PollerResult.TIMEOUT)
                    onPollerComplete(PollerResult.TIMEOUT)
                }
                // Continúa verificando.
                else {
                    mHandler.postDelayed(this, POLL_INTERVAL)
                }
            }
        }
        // Programa la primera verificación.
        mHandler.postDelayed(pollerRunnable!!, POLL_INTERVAL)
    }

    /**
     * Detiene el poller de URL si está activo.
     * Elimina el `pollerRunnable` de la cola de ejecución del `mHandler`.
     */
    private fun stopUrlPoller(reason: PollerResult = PollerResult.NONE) {
        // Detiene el poller eliminando su Runnable del handler.
        pollerRunnable?.let {
            mHandler.removeCallbacks(it)
            Log.d("WebViewDebug", "stopUrlPoller: Runnable del poller removido. Razón: $reason")
        }
        // Permite nuevos pollers.
        pollerRunnable = null
    }

    /**
     * Redirige al usuario a la `InicioActivity` después de un pago exitoso.
     * Se pasa un extra para indicar a `InicioActivity` que navegue directamente
     * a la sección de la billetera o donde se muestra el QR.
     * La pila de actividades se limpia para que el usuario no pueda volver a la pantalla de pagos.
     */
    private fun redirigirAQR() {
        // Redirige a InicioActivity con un extra para mostrar la billetera.
        Log.i("WebViewDebug", "redirigirAQR: Iniciando Intent a InicioActivity.")
        val intent = Intent(this, InicioActivity::class.java).apply {
            putExtra("navigate_to_wallet", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // Lanza la actividad y finaliza esta.
        startActivity(intent)
        Log.i("WebViewDebug", "redirigirAQR: startActivity llamado.")
        finish()
        Log.i("WebViewDebug", "redirigirAQR: finish() llamado.")
    }

    /**
     * Maneja la selección de ítems en el menú de la ActionBar.
     * Específicamente, maneja el clic en el botón de "regresar" (home).
     *
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Maneja clics en la Toolbar (botón de regreso).
        return when (item.itemId) {
            android.R.id.home -> {
                Log.d("WebViewDebug", "onOptionsItemSelected: Botón Home presionado.")
                // Cierra la actividad actual.
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Se llama cuando la actividad está siendo destruida.
     * Es importante limpiar recursos aquí para evitar memory leaks, como detener el poller,
     * limpiar callbacks del handler y destruir el WebView.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("WebViewDebug", "SistemaPagosActivity onDestroy.")
        // Detiene el poller y limpia callbacks.
        stopUrlPoller(PollerResult.NONE)
        mHandler.removeCallbacksAndMessages(null)

        // Destruye el WebView para liberar recursos.
        if (::webView.isInitialized) {
            Log.d("WebViewDebug", "Destruyendo WebView...")
            val parent = webView.parent
            if (parent is ViewGroup) {
                parent.removeView(webView)
            }
            webView.stopLoading()
            webView.settings.javaScriptEnabled = false
            webView.clearHistory()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
            Log.d("WebViewDebug", "WebView destruido.")
        }
        // Limpia la transacción pendiente.
        transaccionPendiente = null
    }
}