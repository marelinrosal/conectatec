 package com.example.conectatec

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.*
import android.util.Log
// Imports de GestureDetector, KeyEvent, MotionEvent eliminados al quitar la detección de movimiento para refresco
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
 * Clase de datos para la información de un tipo de ticket seleccionable por el usuario.
 * Contiene la URL de Stripe para ese ticket, su tipo, duración, costo y fecha de creación.
 */
data class TicketInfo(
    val url: String,
    val tipo: String, // "urbano" o "interurbano"
    val duracion: String, // "dia", "semana", "mes", "viaje"
    val costo: Double,
    val fecha: String
)

/**
 * Clase de datos para representar una transacción de compra de ticket,
 * diseñada para ser almacenada en la base de datos Supabase.
 */
@Serializable
data class TransaccionData(
    val codigo_qr: String,
    val usuario_id: String,
    val tipo_qr: String,
    val duracion_qr: String,
    val costo_qr: Double,
    @SerialName("fechaCompra") // Mapea a la columna 'fechaCompra' en Supabase
    val fecha_compra: String
)



class SistemaPagosActivity : AppCompatActivity() {
    // Componentes de la UI
    private lateinit var btnPagar: Button
    private var selectedTicketInfo: TicketInfo? = null // Información del ticket que el usuario ha seleccionado

    // Configuración de Stripe
    private val stripePublishableKey = "pk_test_51R5VpuLlYPVHsiF1QcQnwjIuiXdv8hZ0l1uJe3dvVyzVplOBn22XXClKKYeyl7JHQu9oqPJ2wwXiO0AVPa0wQdFi00WMA5kqln"

    // WebView para mostrar la pasarela de pago de Stripe
    private lateinit var webView: WebView

    // Handler principal para operaciones en el hilo de UI (Toasts, cierre de diálogos) y para el poller
    private val mHandler = Handler(Looper.getMainLooper())
    private var transaccionPendiente: TransaccionData? = null // Almacena datos de la transacción antes de confirmar el pago

    // Dependencias
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var supabaseClient: SupabaseClient

    // Variables para el Poller de URL (chequeo periódico de la URL del WebView)
    private var pollerRunnable: Runnable? = null // Tarea que se ejecuta periódicamente
    private var pollCount = 0 // Contador de cuántas veces se ha ejecutado el poller
    private val MAX_POLLS = 300  // Límite de ejecuciones del poller (10 minutos / 2 segs por poll)
    private val POLL_INTERVAL = 3000L // Intervalo entre chequeos del poller (3 segundos)

    /**
     * Se llama cuando la actividad es creada.
     * Inicializa la UI, Stripe, Supabase y los listeners de los botones.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sistema_pagos)
        Log.d("WebViewDebug", "SistemaPagosActivity onCreate")

        // Inicialización de SharedPreferences y Supabase
        sharedPreferences = getSharedPreferences(Constantes.PREFS_NAME, MODE_PRIVATE)
        supabaseClient = getClient()

        // Configuración de la Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar2)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Botón de regreso
        supportActionBar?.title = "Sistema de Pagos"

        // Inicialización de la librería de Stripe
        PaymentConfiguration.init(this, stripePublishableKey)

        // Inicialización de componentes de la UI y mapeo de tickets
        btnPagar = findViewById(R.id.btnPagar)
        btnPagar.isEnabled = false // Deshabilitado hasta que se seleccione un ticket
        val grupoInterurbano = findViewById<RadioGroup>(R.id.grupoInterurbano)
        val grupoUrbano = findViewById<RadioGroup>(R.id.grupoUrbano)
        val currentDate = Instant.now().toString() // Fecha actual para los nuevos tickets

        // Mapa que relaciona los IDs de los RadioButton con la información del TicketInfo correspondiente
        val ticketInfoMap = mapOf(
            R.id.diaUrbano to TicketInfo("https://buy.stripe.com/test_fZe3gj2qceQi7DibIJ", "urbano", "dia", 30.0, currentDate),
            R.id.semanaUrbano to TicketInfo("https://buy.stripe.com/test_00g9EHfcYdMe6ze4gj", "urbano", "semana", 150.0, currentDate),
            R.id.mesUrbano to TicketInfo("https://buy.stripe.com/test_aEU7wzc0M37AaPuaEJ", "urbano", "mes", 600.0, currentDate),
            R.id.viajeUrbano to TicketInfo("https://buy.stripe.com/test_5kAg35d4Q0ZsbTy9AH", "urbano", "viaje", 15.0, currentDate),
            R.id.diaInterurbano to TicketInfo("https://buy.stripe.com/test_9AQeZ11m86jM5vacMO", "interurbano", "dia", 120.0, currentDate),
            R.id.semanaInterurbano to TicketInfo("https://buy.stripe.com/test_bIYg358OAeQif5KdQU", "interurbano", "semana", 540.0, currentDate),
            R.id.mesInterurbano to TicketInfo("https://buy.stripe.com/test_28o18b5CoaA2bTyeV0", "interurbano", "mes", 2040.0, currentDate),
            R.id.viajeInterurbano to TicketInfo("https://buy.stripe.com/test_28o7wzaWIeQi1eU5ks", "interurbano", "viaje", 60.0, currentDate)
        )

        // Listener para el grupo de RadioButtons de tickets interurbanos
        grupoInterurbano.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) { // Si se selecciona una opción
                grupoUrbano.clearCheck() // Desmarcar la opción del otro grupo
                selectedTicketInfo = ticketInfoMap[checkedId] // Guardar info del ticket seleccionado
                btnPagar.isEnabled = true // Habilitar botón de pagar
            } else {
                // Si se desmarca y el otro grupo también está desmarcado, deshabilitar botón
                if (grupoUrbano.checkedRadioButtonId == -1) {
                    selectedTicketInfo = null
                    btnPagar.isEnabled = false
                }
            }
        }

        // Listener para el grupo de RadioButtons de tickets urbanos
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

        // Listener para el botón de Pagar
        btnPagar.setOnClickListener {
            Log.d("WebViewDebug", "btnPagar Clicked")
            val currentTicketInfo = selectedTicketInfo
            val userId = getLoggedInUserId()

            if (currentTicketInfo != null && userId != null) {
                // Iniciar corutina para operaciones asíncronas (chequeo de ticket y Supabase)
                lifecycleScope.launch {
                    val hasValidTicket = ticketValido(userId) // Verificar si ya tiene un ticket válido
                    // Volver al hilo principal para actualizar UI o mostrar BottomSheet
                    withContext(Dispatchers.Main) {
                        if (hasValidTicket) {
                            Log.d("WebViewDebug", "Usuario ya tiene un boleto vigente.")
                            Toast.makeText(this@SistemaPagosActivity, "Ya tienes un boleto vigente. No puedes comprar otro hasta que expire.", Toast.LENGTH_LONG).show()
                            return@withContext // Salir si ya tiene ticket
                        }

                        // Crear los datos de la transacción que se guardarán si el pago es exitoso
                        transaccionPendiente = TransaccionData(
                            codigo_qr = UUID.randomUUID().toString(), // Generar ID único para el QR
                            usuario_id = userId,
                            tipo_qr = currentTicketInfo.tipo,
                            duracion_qr = currentTicketInfo.duracion,
                            costo_qr = currentTicketInfo.costo,
                            fecha_compra = currentTicketInfo.fecha
                        )
                        Log.d("WebViewDebug", "Transacción pendiente creada: ${transaccionPendiente?.codigo_qr}")
                        Toast.makeText(this@SistemaPagosActivity, "Procediendo al pago...", Toast.LENGTH_SHORT).show()
                        // Mostrar el BottomSheet con el WebView para la pasarela de Stripe
                        mostrarBottomSheet(currentTicketInfo.url)
                    }
                }
            } else if (userId == null) {
                Log.w("WebViewDebug", "Error: User ID es null.")
                Toast.makeText(this, "Error: No se pudo obtener el ID de usuario. Intenta reiniciar sesión.", Toast.LENGTH_LONG).show()
            } else { // currentTicketInfo es null
                Log.w("WebViewDebug", "Error: No se ha seleccionado un boleto.")
                Toast.makeText(this, "Selecciona una opción de boleto primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Verifica si el usuario ya tiene un ticket válido y activo en Supabase.
     * @param userId ID del usuario.
     * @return `true` si tiene un ticket válido, `false` en caso contrario.
     */
    private suspend fun ticketValido(userId: String): Boolean {
        Log.d("WebViewDebug", "ticketValido: Verificando para usuario $userId")
        return try {
            // Consulta a Supabase para obtener la última transacción del usuario
            val latestTransaction: InfoTransaccionWallet? = supabaseClient.postgrest["transaccion"]
                .select(Columns.list("usuario_id", "tipo_qr", "duracion_qr", "codigo_qr", "fechaCompra")) {
                    filter {
                        eq("usuario_id", userId)
                    }
                    order("fechaCompra", Order.DESCENDING) // Ordenar por fecha para obtener la más reciente
                    limit(1) // Solo la última transacción
                }
                .decodeSingleOrNull() // Decodificar como un solo objeto InfoTransaccionWallet o null

            if (latestTransaction == null) {
                Log.d("WebViewDebug", "ticketValido: No hay transacciones para $userId")
                return false // No hay transacciones, por lo tanto no hay ticket válido
            }
            Log.d("WebViewDebug", "ticketValido: Última transacción encontrada ${latestTransaction.codigo_qr}")

            // Lógica específica para tickets de tipo "viaje"
            if (latestTransaction.duracion_qr == "viaje") {
                val scanCount = numeroEscaneos(latestTransaction.codigo_qr) // Obtener conteo de escaneos
                val isExpiredByTime = TicketUtils.transaccionExpirada(latestTransaction) // Verificar expiración por tiempo
                Log.d("WebViewDebug", "ticketValido: Boleto de viaje ${latestTransaction.codigo_qr}. Escaneos: $scanCount, Expirado por tiempo: $isExpiredByTime")
                if (scanCount >= 2 || isExpiredByTime) { // Si se usó 2 veces o expiró por tiempo
                    if (scanCount >= 2) reinicioEscaneo(latestTransaction.codigo_qr) // Reiniciar conteo si se usaron los escaneos
                    return false // El ticket de viaje ya no es válido
                }
                return true // El ticket de viaje es válido
            }

            // Para otros tipos de tickets, solo verificar la expiración por tiempo
            val isExpired = TicketUtils.transaccionExpirada(latestTransaction)
            Log.d("WebViewDebug", "ticketValido: Boleto no-viaje ${latestTransaction.codigo_qr}. Expirado: $isExpired")
            return !isExpired // El ticket es válido si NO ha expirado
        } catch (e: Exception) {
            Log.e("WebViewDebug", "ticketValido: Error al verificar para $userId: ${e.message}", e)
            return false // En caso de error, asumir que no hay ticket válido
        }
    }

    /**
     * Obtiene el número de veces que un ticket de tipo "viaje" ha sido escaneado.
     * @param codigoQr Código QR del ticket.
     * @return Número de escaneos.
     */
    private fun numeroEscaneos(codigoQr: String): Int {
        val contador = sharedPreferences.getInt("scan_count_$codigoQr", 0)
        return contador
    }

    /**
     * Reinicia el contador de escaneos para un ticket de tipo "viaje".
     * @param codigoQr Código QR del ticket.
     */
    private fun reinicioEscaneo(codigoQr: String) {
        sharedPreferences.edit().remove("scan_count_$codigoQr").apply()
        Log.d("WebViewDebug", "reinicioEscaneo: Contador limpiado para $codigoQr")
    }

    /**
     * Obtiene el ID del usuario que ha iniciado sesión desde SharedPreferences.
     * @return ID del usuario o `null` si no se encuentra.
     */
    private fun getLoggedInUserId(): String? {
        val userId = sharedPreferences.getString(Constantes.KEY_USER_ID, null)
        return userId
    }

    /**
     * Crea y retorna una instancia del cliente de Supabase.
     */
    private fun getClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://uqltgfifxwliboccsxko.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVxbHRnZmlmeHdsaWJvY2NzeGtvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTk0MzAyODAsImV4cCI6MjA3NTAwNjI4MH0.EuLdr9RVIMRFLXR6VhxGqoskET6EUcb_B9zsPqe-bO0"
        ) {
            install(Postgrest) // Instala el plugin de Postgrest para interactuar con la base de datos
        }
    }

    /**
     * Enum para representar el resultado del poller de URL.
     */
    enum class PollerResult { SUCCESS, TIMEOUT, CANCELLED_BY_NAVIGATION, NONE }

    /**
     * Muestra un BottomSheetDialog con un WebView para cargar la URL de pago de Stripe.
     * Implementa la lógica para detectar el éxito, cancelación o timeout del pago.
     * @param urlToLoad URL de la pasarela de pago de Stripe.
     */
    private fun mostrarBottomSheet(urlToLoad: String) {
        Log.d("WebViewDebug", "mostrarBottomSheet: Preparando para URL: $urlToLoad")
        val bottomSheetDialog = BottomSheetDialog(this)
        // Recrear o limpiar WebView
        if (::webView.isInitialized && webView.parent != null) {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
        webView = WebView(this) // webView es una propiedad de la clase
        stopUrlPoller() // Detener cualquier poller anterior si estaba activo

        // Limpiar caché y cookies del WebView para una sesión de pago fresca
        webView.clearCache(true)
        webView.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        // Configuración del WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true // Necesario para algunas páginas de pago
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE // No usar caché para la página de pago

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        // Variables locales al contexto de este BottomSheet para rastrear el estado del pago
        var pagoExitoso = false
        var cierreManual = true // Asumir cierre manual hasta que se confirme éxito, cancelación o timeout
        var isInitialLoad = true // Flag para la primera carga de la URL
        var operacionCanceladaPorTimeout = false // Flag para timeout del poller
        val originalUrlForWebView = urlToLoad // Guardar la URL original para comparaciones

        Log.d("WebViewDebug", "mostrarBottomSheet: Vars init. pagoExitoso=$pagoExitoso, cierreManual=$cierreManual, isInitialLoad=$isInitialLoad")

        // Configuración del WebViewClient para manejar eventos de carga de página
        webView.webViewClient = object : WebViewClient() {
            /**
             * Se llama cuando el WebView comienza a cargar una URL.
             */
            override fun onPageStarted(view: WebView?, currentUrl: String?, favicon: Bitmap?) {
                super.onPageStarted(view, currentUrl, favicon)
                Log.d("WebViewDebug", "onPageStarted: $currentUrl, isInitialLoad: $isInitialLoad")
                // Si la página navega a una URL diferente de la original y el poller está activo, detener el poller.
                if (currentUrl != originalUrlForWebView && pollerRunnable != null) {
                    Log.d("WebViewDebug", "onPageStarted: Navegación a nueva URL ($currentUrl) detectada, deteniendo poller.")
                    stopUrlPoller(PollerResult.CANCELLED_BY_NAVIGATION)
                }
            }

            /**
             * Se llama cuando el WebView ha terminado de cargar una URL.
             * Aquí se implementa la lógica principal de detección de éxito/cancelación
             * y el inicio del poller de URL.
             */
            override fun onPageFinished(view: WebView?, currentUrl: String?) {
                super.onPageFinished(view, currentUrl)
                Log.d("WebViewDebug", "onPageFinished - URL: $currentUrl, isInitialLoad: $isInitialLoad, pagoExitoso: $pagoExitoso, originalUrl: $originalUrlForWebView")

                // Path específico en la URL de Stripe que indica éxito (ej: /c/pay/)
                val specificSuccessPath = "/c/pay/"
                val cancelUrlKeyword = "cancel" // Palabra clave para URLs de cancelación

                // Si es la primera carga de la URL original
                if (isInitialLoad && currentUrl == originalUrlForWebView) {
                    Log.d("WebViewDebug", "  -> Carga inicial de '$currentUrl'. Marcando isInitialLoad = false.")
                    isInitialLoad = false
                    // Si el pago aún no se ha marcado como exitoso, iniciar el poller de URL.
                    // El poller verificará periódicamente si la URL del WebView cambia a la de éxito.
                    if (!pagoExitoso) {
                        Log.d("WebViewDebug", "  -> Carga inicial completa, iniciando poller.")
                        startUrlPoller(bottomSheetDialog, originalUrlForWebView) { detectedResult ->
                            // Callback del poller
                            when (detectedResult) {
                                PollerResult.SUCCESS -> { // El poller detectó la URL de éxito
                                    if (!pagoExitoso) { // Procesar solo una vez
                                        pagoExitoso = true
                                        cierreManual = false
                                    }
                                }
                                PollerResult.TIMEOUT -> { // El poller alcanzó el tiempo límite
                                    if (!pagoExitoso && bottomSheetDialog.isShowing) { // Si no hubo éxito y el diálogo sigue abierto
                                        Log.w("WebViewDebug", "    --> Poller TIMEOUT. Cerrando diálogo.")
                                        operacionCanceladaPorTimeout = true
                                        cierreManual = false // No fue un cierre manual del usuario
                                        Toast.makeText(this@SistemaPagosActivity, "Operación cancelada por tiempo límite.", Toast.LENGTH_LONG).show()
                                        bottomSheetDialog.dismiss() // Cerrar el diálogo
                                    }
                                }
                                else -> { /* CANCELLED_BY_NAVIGATION o NONE: El poller se detuvo por otra razón, no hacer nada aquí */ }
                            }
                        }
                    }
                }
                // Si no es la carga inicial y la URL actual no es nula
                else if (currentUrl != null && !isInitialLoad) {
                    Log.d("WebViewDebug", "  -> Evaluando URL (no inicial): $currentUrl")
                    // Si onPageFinished se dispara para una URL que no es la original,
                    // el poller (si estaba activo) ya debería haberse detenido por onPageStarted.
                    // Una llamada extra a stopUrlPoller aquí es una precaución.
                    stopUrlPoller(PollerResult.CANCELLED_BY_NAVIGATION)

                    // Verificar si la URL actual es la de éxito de Stripe
                    if (currentUrl.contains("stripe.com", ignoreCase = true) &&
                        currentUrl.contains(specificSuccessPath, ignoreCase = false)) { // ignoreCase=false para el path específico
                        Log.i("WebViewDebug", "    --> URL DE ÉXITO ($specificSuccessPath) DETECTADA por onPageFinished: $currentUrl")
                        if (!pagoExitoso) { // Procesar solo una vez
                            procesarLogicaDeExito(bottomSheetDialog) { success ->
                                if (success && !pagoExitoso) { // Doble check por si acaso
                                    pagoExitoso = true
                                    cierreManual = false
                                }
                            }
                        } else { Log.i("WebViewDebug", "      --> Éxito ya procesado (onPageFinished).") }
                    }
                    // Verificar si la URL actual es la de cancelación de Stripe
                    else if (currentUrl.contains("stripe.com", ignoreCase = true) &&
                        currentUrl.contains(cancelUrlKeyword, ignoreCase = true)) {
                        Log.w("WebViewDebug", "    --> URL DE CANCELACIÓN DETECTADA por onPageFinished: $currentUrl")
                        if (!pagoExitoso) { // Si no fue exitoso previamente
                            cierreManual = false // No es un cierre manual del usuario
                            // Mostrar Toast y cerrar diálogo después de un breve delay
                            mHandler.postDelayed({
                                if (bottomSheetDialog.isShowing) {
                                    Toast.makeText(this@SistemaPagosActivity, "Pago cancelado.", Toast.LENGTH_SHORT).show()
                                    bottomSheetDialog.dismiss()
                                }
                            }, 500)
                        }
                    }
                    // Si la URL actual es la original de nuevo (podría pasar si el usuario navega hacia atrás o por un reload)
                    // y el pago aún no es exitoso.
                    else if (currentUrl == originalUrlForWebView && !pagoExitoso) {
                        Log.d("WebViewDebug", "    --> De vuelta en URL original ($currentUrl) y sin éxito. Poller se encargará o reiniciará si es necesario.")
                        // Si el poller no está activo (ya terminó o fue detenido), reiniciarlo.
                        if(pollerRunnable == null && !pagoExitoso){
                            Log.d("WebViewDebug", "    --> Reiniciando poller porque estamos en URL original y poller no activo.")
                            startUrlPoller(bottomSheetDialog, originalUrlForWebView) { detectedResult ->
                                when (detectedResult) {
                                    PollerResult.SUCCESS -> { if (!pagoExitoso) { pagoExitoso = true; cierreManual = false } }
                                    PollerResult.TIMEOUT -> {
                                        if (!pagoExitoso && bottomSheetDialog.isShowing) {
                                            operacionCanceladaPorTimeout = true; cierreManual = false
                                            Toast.makeText(this@SistemaPagosActivity, "Operación cancelada por tiempo límite (reintento).", Toast.LENGTH_LONG).show()
                                            bottomSheetDialog.dismiss()
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    } else {
                        Log.d("WebViewDebug", "    --> URL ($currentUrl) no coincide con patrones conocidos.")
                    }
                } else if (currentUrl == null) {
                    Log.w("WebViewDebug", "  -> onPageFinished: currentUrl es null.")
                } else { // Caso: isInitialLoad == true PERO currentUrl != originalUrlForWebView (redirección temprana)
                    Log.d("WebViewDebug", "  -> onPageFinished: Aún en carga inicial (isInitialLoad=true), pero URL ($currentUrl) es diferente de la original ($originalUrlForWebView).")
                    isInitialLoad = false // Marcar como no inicial, la carga principal ha terminado o redirigido
                }
            }

            /**
             * Se llama si ocurre un error al cargar una URL en el WebView.
             */
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val errorMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    "Error: ${error?.errorCode} ${error?.description} en ${request?.url}"
                } else {
                    "Error cargando ${request?.url}"
                }
                Log.e("WebViewError", errorMessage)
                Toast.makeText(this@SistemaPagosActivity, "Error cargando página de pago.", Toast.LENGTH_SHORT).show()
                // Podrías considerar cerrar el BottomSheet aquí si el error es crítico.
            }
        }

        // Listener para cuando el BottomSheetDialog se cierra (por cualquier motivo)
        bottomSheetDialog.setOnDismissListener {
            Log.d("WebViewDebug", "BottomSheet DISMISS Listener. pagoExitoso: $pagoExitoso, cierreManual: $cierreManual, timeout: $operacionCanceladaPorTimeout")
            stopUrlPoller(PollerResult.NONE) // Detener el poller
            mHandler.removeCallbacksAndMessages(null) // Limpiar todos los callbacks del handler principal

            if (pagoExitoso) {
                Log.i("WebViewDebug", "  --> Redirigiendo a QR porque pagoExitoso es true.")
                redirigirAQR() // Ir a la pantalla del QR
            } else if (operacionCanceladaPorTimeout) {
                // El mensaje de timeout ya se mostró, no hacer nada más.
                Log.w("WebViewDebug", "  --> Operación cancelada por timeout, no se muestra Toast adicional en dismiss.")
            } else if (cierreManual) {
                // Si el usuario cerró manualmente el diálogo antes de completar/cancelar.
                Log.w("WebViewDebug", "  --> Transacción no completada (cierre manual).")
                Toast.makeText(this@SistemaPagosActivity, "Transacción no completada.", Toast.LENGTH_LONG).show()
            } else {
                // Otro caso: pago no exitoso, no cierre manual, no timeout (ej: cancelación explícita de Stripe)
                Log.i("WebViewDebug", "  --> Pago no exitoso, no cierre manual, no timeout.")
            }
            transaccionPendiente = null // Limpiar la transacción pendiente
        }

        // Cargar la URL inicial en el WebView y mostrar el BottomSheet
        Log.d("WebViewDebug", "loadUrl: $urlToLoad")
        webView.loadUrl(urlToLoad)
        bottomSheetDialog.setContentView(webView)
        bottomSheetDialog.show()
    }


    /**
     * Procesa la lógica de un pago exitoso: guarda la transacción en Supabase,
     * actualiza flags y cierra el diálogo después de un delay.
     * @param dialog El BottomSheetDialog actual.
     * @param onResult Callback que se invoca con `true` si la operación con Supabase fue exitosa, `false` si no.
     */
    private fun procesarLogicaDeExito(dialog: BottomSheetDialog, onResult: (Boolean) -> Unit) {
        Log.i("WebViewDebug", "procesarLogicaDeExito: Procesando...")
        stopUrlPoller(PollerResult.SUCCESS) // Detener el poller porque se detectó éxito

        lifecycleScope.launch {
            var success = false
            try {
                transaccionPendiente?.let { transaccion ->
                    supabaseClient.postgrest["transaccion"].insert(transaccion) // Insertar en Supabase
                    Log.i("SupabaseInsert", "Transacción registrada: ${transaccion.codigo_qr}")
                    success = true
                } ?: run {
                    Log.w("SupabaseInsert", "Transacción pendiente era null al intentar registrar el éxito.")
                    success = false // No se puede registrar si no hay transacción pendiente
                }
            } catch (e: Exception) {
                Log.e("SupabaseInsert", "Error al registrar transacción en Supabase: ${e.message}", e)
                success = false
            }
            // Volver al hilo principal para actualizar UI
            withContext(Dispatchers.Main) {
                onResult(success) // Informar al llamador (onPageFinished/poller) si la DB fue exitosa
                if (success) {
                    // Mostrar Toast y cerrar el diálogo después de un breve delay
                    mHandler.postDelayed({
                        if (dialog.isShowing) {
                            Toast.makeText(this@SistemaPagosActivity, "Pago exitoso. Redirigiendo...", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    }, 500) // 500ms delay
                } else {
                    // Si la inserción en Supabase falló
                    Toast.makeText(this@SistemaPagosActivity, "Error al confirmar la transacción con la base de datos.", Toast.LENGTH_LONG).show()
                    // Podrías decidir cerrar el diálogo aquí también o dejarlo abierto para que el usuario reintente/vea el error.
                }
            }
        }
    }

    /**
     * Inicia un poller que verifica periódicamente la URL del WebView.
     * Se usa como respaldo si `onPageFinished` no detecta la URL de éxito inmediatamente.
     * @param dialog El BottomSheetDialog actual.
     * @param originalUrl La URL original que se cargó en el WebView.
     * @param onPollerComplete Callback que se invoca con el resultado del poller (éxito, timeout, etc.).
     */
    private fun startUrlPoller(dialog: BottomSheetDialog, originalUrl: String, onPollerComplete: (PollerResult) -> Unit) {
        if (pollerRunnable != null || !::webView.isInitialized) {
            Log.d("WebViewDebug", "startUrlPoller: Poller ya activo o WebView no lista. No se inicia. PollerRunnable null? ${pollerRunnable == null}")
            return // Evitar iniciar múltiples pollers o si webView no está lista
        }
        if (!dialog.isShowing) {
            Log.d("WebViewDebug", "startUrlPoller: Dialogo no se muestra, no se inicia poller.")
            onPollerComplete(PollerResult.NONE) // No se puede ejecutar
            return
        }

        Log.d("WebViewDebug", "startUrlPoller: Iniciando para URL original: $originalUrl. MAX_POLLS=$MAX_POLLS, INTERVAL=$POLL_INTERVAL")
        pollCount = 0 // Resetear contador de polls

        // Crear el Runnable para el poller
        pollerRunnable = object : Runnable {
            override fun run() {
                if (!dialog.isShowing || !::webView.isInitialized || pollerRunnable == null) {
                    // Si el poller fue detenido o las condiciones cambiaron, no continuar.
                    if (pollerRunnable != null) { // Solo si no fue detenido explícitamente por stopUrlPoller
                        stopUrlPoller(PollerResult.NONE)
                        onPollerComplete(PollerResult.NONE)
                    }
                    return
                }

                pollCount++ // Incrementar contador
                val actualUrl = webView.url // Obtener URL actual del WebView
                Log.d("WebViewDebug", "  --> Poll #$pollCount/$MAX_POLLS: URL actual del WebView: $actualUrl")

                // Verificar si la URL actual es la de éxito de Stripe
                if (actualUrl?.contains("/c/pay/") == true) { // Path específico de éxito
                    Log.i("WebViewDebug", "    --> Éxito detectado por Poller!")
                    // No llamar a stopUrlPoller aquí, procesarLogicaDeExito lo hará
                    procesarLogicaDeExito(dialog) { success ->
                        onPollerComplete(if (success) PollerResult.SUCCESS else PollerResult.NONE)
                    }
                }
                // Verificar si se alcanzó el máximo de polls (timeout)
                else if (pollCount >= MAX_POLLS) {
                    Log.w("WebViewDebug", "    --> Poller alcanzó MAX_POLLS sin éxito. URL: $actualUrl. Considerado TIMEOUT.")
                    stopUrlPoller(PollerResult.TIMEOUT) // Detener el poller indicando timeout
                    onPollerComplete(PollerResult.TIMEOUT) // Informar timeout
                }
                // Si no es éxito ni timeout, programar el siguiente poll
                else {
                    mHandler.postDelayed(this, POLL_INTERVAL)
                }
            }
        }
        // Programar la primera ejecución del poller
        mHandler.postDelayed(pollerRunnable!!, POLL_INTERVAL)
    }

    /**
     * Detiene el poller de URL si está activo.
     * @param reason El motivo por el que se detiene el poller (opcional, para logging).
     */
    private fun stopUrlPoller(reason: PollerResult = PollerResult.NONE) {
        pollerRunnable?.let { // Si el runnable existe
            mHandler.removeCallbacks(it) // Removerlo de la cola del handler
            Log.d("WebViewDebug", "stopUrlPoller: Runnable del poller removido. Razón: $reason")
        }
        pollerRunnable = null // Marcar como nulo para permitir que se inicie de nuevo
    }

    /**
     * Redirige al usuario a la InicioActivity para mostrar el QR.
     * Se llama después de un pago exitoso.
     */
    private fun redirigirAQR() {
        Log.i("WebViewDebug", "redirigirAQR: Iniciando Intent a InicioActivity.")
        val intent = Intent(this, InicioActivity::class.java).apply {
            putExtra("navigate_to_wallet", true) // Extra para indicar a InicioActivity que navegue al wallet
            // Flags para limpiar la pila de actividades y empezar InicioActivity como nueva tarea
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        Log.i("WebViewDebug", "redirigirAQR: startActivity llamado.")
        finish() // Finalizar esta actividad (SistemaPagosActivity)
        Log.i("WebViewDebug", "redirigirAQR: finish() llamado.")
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

    /**
     * Se llama cuando la actividad está siendo destruida.
     * Limpia recursos como el poller, callbacks del handler y el WebView.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("WebViewDebug", "SistemaPagosActivity onDestroy.")
        stopUrlPoller(PollerResult.NONE) // Detener el poller
        mHandler.removeCallbacksAndMessages(null) // Limpiar todos los callbacks del handler principal

        // Destruir el WebView para liberar recursos y evitar memory leaks
        if (::webView.isInitialized) {
            Log.d("WebViewDebug", "Destruyendo WebView...")
            val parent = webView.parent
            if (parent is ViewGroup) {
                parent.removeView(webView) // Quitar WebView de su layout padre
            }
            webView.stopLoading() // Detener cualquier carga en progreso
            webView.settings.javaScriptEnabled = false // Deshabilitar JS
            webView.clearHistory() // Limpiar historial
            webView.loadUrl("about:blank") // Cargar página en blanco para liberar recursos
            webView.removeAllViews() // Remover vistas hijas del WebView
            webView.destroy() // Destruir la instancia del WebView
            Log.d("WebViewDebug", "WebView destruido.")
        }
        transaccionPendiente = null // Limpiar transacción pendiente
    }
}