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
 * Clase de datos que encapsula la información de un tipo de ticket seleccionable por el usuario.
 * <p>
 * Contiene la URL de la pasarela de pago de Stripe para este ticket específico,
 * su tipo (urbano o interurbano), la duración o validez (día, semana, mes, o por viaje),
 * el costo y la fecha de creación del `TicketInfo` (que se usa como fecha de compra si se adquiere).
 * </p>
 * @property url La URL de Stripe para iniciar el proceso de pago de este ticket.
 * @property tipo El tipo de servicio del ticket, ej: "urbano" o "interurbano".
 * @property duracion La validez del ticket, ej: "dia", "semana", "mes", "viaje".
 * @property costo El precio del ticket.
 * @property fecha La fecha y hora (en formato ISO String) en que se generó esta información de ticket,
 *                 que se utilizará como fecha de compra al momento de la transacción.
 */
data class TicketInfo(
    val url: String,
    val tipo: String,
    val duracion: String,
    val costo: Double,
    val fecha: String
)

/**
 * Clase de datos que representa una transacción de compra de un ticket.
 * <p>
 * Esta clase es serializable para ser almacenada en la base de datos Supabase.
 * Contiene todos los detalles de la compra, incluyendo un código QR único,
 * el ID del usuario, y los detalles del ticket adquirido.
 * </p>
 * @property codigo_qr Un identificador único (UUID) generado para este ticket/QR.
 * @property usuario_id El ID del usuario que realizó la compra.
 * @property tipo_qr El tipo de servicio del ticket comprado (ej: "urbano").
 * @property duracion_qr La validez del ticket comprado (ej: "dia").
 * @property costo_qr El precio pagado por el ticket.
 * @property fecha_compra La fecha y hora (en formato ISO String) en que se realizó la compra.
 *                        Mapea a la columna 'fechaCompra' en la base de datos Supabase.
 */
@Serializable
data class TransaccionData(
    val codigo_qr: String,
    val usuario_id: String,
    val tipo_qr: String,
    val duracion_qr: String,
    val costo_qr: Double,
    @SerialName("fechaCompra") // Mapea el nombre del campo en Kotlin a la columna 'fechaCompra' en Supabase.
    val fecha_compra: String
)

/**
 * Actividad que gestiona el sistema de pagos para la compra de tickets.
 * <p>
 * Permite al usuario seleccionar diferentes tipos de tickets (urbanos e interurbanos)
 * mediante [RadioGroup]. Al seleccionar un ticket y presionar "Pagar", se verifica si el
 * usuario ya tiene un ticket válido. Si no, se muestra un [BottomSheetDialog] con un
 * [WebView] que carga la pasarela de pago de Stripe.
 * </p>
 * <p>
 * La actividad implementa un "poller" para monitorear la URL del [WebView] y detectar
 * el éxito o cancelación del pago. Si el pago es exitoso, se registra la transacción en
 * Supabase y se redirige al usuario a [InicioActivity] para ver su QR.
 * </p>
 * <p>
 * Utiliza [SharedPreferences] para obtener el ID del usuario y [SupabaseClient] para
 * interactuar con la base de datos.
 * </p>
 *
 * @property btnPagar Botón para iniciar el proceso de pago.
 * @property selectedTicketInfo Almacena la información del [TicketInfo] actualmente seleccionado por el usuario.
 * @property stripePublishableKey Clave publicable de Stripe para inicializar el SDK.
 * @property webView Instancia de [WebView] utilizada para mostrar la pasarela de pago.
 * @property mHandler [Handler] asociado al hilo principal para operaciones de UI.
 * @property transaccionPendiente Almacena los datos de [TransaccionData] antes de confirmar el pago,
 *                                para ser guardados en Supabase si el pago es exitoso.
 * @property sharedPreferences Instancia de [SharedPreferences].
 * @property supabaseClient Instancia del cliente de Supabase.
 * @property pollerRunnable [Runnable] para la tarea de polling de URL del WebView.
 * @property pollCount Contador de ejecuciones del poller.
 * @property MAX_POLLS Límite máximo de ejecuciones del poller.
 * @property POLL_INTERVAL Intervalo en milisegundos entre chequeos del poller.
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see TicketInfo
 * @see TransaccionData
 * @see Constantes
 * @see InicioActivity
 * @see SupabaseClient
 * @see TicketUtils
 */
class SistemaPagosActivity : AppCompatActivity() {
    // Componentes de la UI
    private lateinit var btnPagar: Button
    private var selectedTicketInfo: TicketInfo? = null // Información del ticket que el usuario ha seleccionado

    // Configuración de Stripe
    private val stripePublishableKey = "pk_test_51R5VpuLlYPVHsiF1QcQnwjIuiXdv8hZ0l1uJe3dvVyzVplOBn22XXClKKYeyl7JHQu9oqPJ2wwXiO0AVPa0wQdFi00WMA5kqln" // TODO: Considerar mover a un lugar más seguro que hardcodeado.

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
    private val MAX_POLLS = 300  // Límite de ejecuciones del poller (300 polls * 2 segs/poll = 600 segs = 10 minutos)
    private val POLL_INTERVAL = 2000L // Intervalo entre chequeos del poller (2 segundos)

    /**
     * Se llama cuando la actividad está siendo creada.
     * <p>
     * Inicializa:
     * <ul>
     *   <li>[SharedPreferences] y el cliente de Supabase.</li>
     *   <li>La [Toolbar] con un botón de regreso.</li>
     *   <li>La configuración de Stripe con la clave publicable.</li>
     *   <li>Los componentes de la UI (botón de pagar, grupos de radio).</li>
     *   <li>Un mapa de [TicketInfo] para cada opción de ticket.</li>
     *   <li>Los listeners para los [RadioGroup] y el botón de pagar.</li>
     * </ul>
     * </p>
     * @param savedInstanceState Si la actividad se está re-inicializando, este Bundle contiene
     *                           los datos más recientes suministrados en [onSaveInstanceState].
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sistema_pagos) // Asume que R.layout.activity_sistema_pagos existe
        Log.d("WebViewDebug", "SistemaPagosActivity onCreate")

        // Inicialización de SharedPreferences y Supabase Client
        sharedPreferences = getSharedPreferences(Constantes.PREFS_NAME, MODE_PRIVATE)
        supabaseClient = getClient()

        // Configuración de la Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar2) // Asume que R.id.toolbar2 existe
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Habilita el botón de regreso en la ActionBar
        supportActionBar?.title = "Sistema de Pagos"    // Título de la ActionBar

        // Inicialización de la librería de Stripe
        PaymentConfiguration.init(this, stripePublishableKey)

        // Inicialización de componentes de la UI y mapeo de tickets
        btnPagar = findViewById(R.id.btnPagar) // Asume que R.id.btnPagar existe
        btnPagar.isEnabled = false // El botón de pagar está deshabilitado hasta que se seleccione un ticket
        val grupoInterurbano = findViewById<RadioGroup>(R.id.grupoInterurbano) // Asume que R.id.grupoInterurbano existe
        val grupoUrbano = findViewById<RadioGroup>(R.id.grupoUrbano)         // Asume que R.id.grupoUrbano existe
        val currentDate = Instant.now().toString() // Obtiene la fecha y hora actual en formato ISO String

        // Mapa que relaciona los IDs de los RadioButton con la información del TicketInfo correspondiente
        // Esto centraliza la configuración de los tickets y sus URLs de Stripe.
        val ticketInfoMap = mapOf(
            R.id.diaUrbano to TicketInfo("https://buy.stripe.com/test_fZe3gj2qceQi7DibIJ", "urbano", "dia", 30.0, currentDate),
            // TODO: Añadir el resto de tus tickets aquí con sus respectivos IDs de RadioButton y URLs de Stripe.
            // Ejemplo: R.id.semanaUrbano to TicketInfo("URL_STRIPE_SEMANA_URBANO", "urbano", "semana", PRECIO, currentDate),
            R.id.viajeInterurbano to TicketInfo("https://buy.stripe.com/test_5kAg35d4Q0ZsbTy9AH", "interurbano", "viaje", 60.0, currentDate)
            // Asegúrate de que los IDs como R.id.diaUrbano, R.id.viajeInterurbano, etc., existan en tu layout.
        )

        // Listener para el grupo de RadioButtons de tickets interurbanos
        grupoInterurbano.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) { // Si se selecciona una opción en este grupo
                grupoUrbano.clearCheck() // Desmarca cualquier selección en el otro grupo (urbano)
                selectedTicketInfo = ticketInfoMap[checkedId] // Actualiza el ticket seleccionado
                btnPagar.isEnabled = true // Habilita el botón de pagar
            } else {
                // Si se desmarca esta opción (checkedId == -1) y el otro grupo también está desmarcado,
                // entonces ningún ticket está seleccionado.
                if (grupoUrbano.checkedRadioButtonId == -1) {
                    selectedTicketInfo = null
                    btnPagar.isEnabled = false // Deshabilita el botón de pagar
                }
            }
        }

        // Listener para el grupo de RadioButtons de tickets urbanos
        grupoUrbano.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) { // Si se selecciona una opción en este grupo
                grupoInterurbano.clearCheck() // Desmarca cualquier selección en el otro grupo (interurbano)
                selectedTicketInfo = ticketInfoMap[checkedId] // Actualiza el ticket seleccionado
                btnPagar.isEnabled = true // Habilita el botón de pagar
            } else {
                // Si se desmarca esta opción y el otro grupo también está desmarcado.
                if (grupoInterurbano.checkedRadioButtonId == -1) {
                    selectedTicketInfo = null
                    btnPagar.isEnabled = false // Deshabilita el botón de pagar
                }
            }
        }

        // Listener para el botón de Pagar
        btnPagar.setOnClickListener {
            Log.d("WebViewDebug", "btnPagar Clicked")
            val currentTicketInfo = selectedTicketInfo // Copia local para evitar problemas de concurrencia (aunque aquí es UI thread)
            val userId = getLoggedInUserId()

            if (currentTicketInfo != null && userId != null) {
                // Inicia una corutina para operaciones asíncronas (verificación de ticket existente).
                lifecycleScope.launch {
                    val hasValidTicket = ticketValido(userId) // Verifica en Supabase si ya existe un ticket válido.
                    // Cambia al hilo principal para actualizar la UI o mostrar el BottomSheet.
                    withContext(Dispatchers.Main) {
                        if (hasValidTicket) {
                            Log.d("WebViewDebug", "Usuario ya tiene un boleto vigente.")
                            Toast.makeText(this@SistemaPagosActivity, "Ya tienes un boleto vigente. No puedes comprar otro hasta que expire.", Toast.LENGTH_LONG).show()
                            return@withContext // Sale de la corutina si ya tiene un ticket.
                        }

                        // Si no tiene un ticket válido, crea la transacción pendiente.
                        transaccionPendiente = TransaccionData(
                            codigo_qr = UUID.randomUUID().toString(), // Genera un ID único para el QR.
                            usuario_id = userId,
                            tipo_qr = currentTicketInfo.tipo,
                            duracion_qr = currentTicketInfo.duracion,
                            costo_qr = currentTicketInfo.costo,
                            fecha_compra = currentTicketInfo.fecha // Usa la fecha guardada en TicketInfo.
                        )
                        Log.d("WebViewDebug", "Transacción pendiente creada: ${transaccionPendiente?.codigo_qr}")
                        Toast.makeText(this@SistemaPagosActivity, "Procediendo al pago...", Toast.LENGTH_SHORT).show()
                        // Muestra el BottomSheet con el WebView para la pasarela de Stripe.
                        mostrarBottomSheet(currentTicketInfo.url)
                    }
                }
            } else if (userId == null) {
                Log.w("WebViewDebug", "Error: User ID es null.")
                Toast.makeText(this, "Error: No se pudo obtener el ID de usuario. Intenta reiniciar sesión.", Toast.LENGTH_LONG).show()
            } else { // currentTicketInfo es null, lo que significa que no se seleccionó ningún ticket.
                Log.w("WebViewDebug", "Error: No se ha seleccionado un boleto.")
                Toast.makeText(this, "Selecciona una opción de boleto primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Verifica si el usuario especificado ya posee un ticket válido y activo.
     * <p>
     * Consulta la tabla 'transaccion' en Supabase para la última transacción del usuario.
     * Si es un ticket de tipo "viaje", comprueba el número de escaneos y la expiración por tiempo.
     * Para otros tipos de tickets, solo comprueba la expiración por tiempo usando [TicketUtils.transaccionExpirada].
     * </p>
     * @param userId El ID del usuario a verificar.
     * @return `true` si el usuario tiene un ticket válido, `false` en caso contrario o si ocurre un error.
     * @see TicketUtils.transaccionExpirada
     * @see numeroEscaneos
     * @see reinicioEscaneo
     */
    private suspend fun ticketValido(userId: String): Boolean {
        Log.d("WebViewDebug", "ticketValido: Verificando para usuario $userId")
        return try {
            // Consulta a Supabase para obtener la última transacción del usuario.
            // Asume que InfoTransaccionWallet es una clase de datos similar a TransaccionData pero para lectura.
            // Si no existe, debes definirla o usar TransaccionData si los campos coinciden.
            val latestTransaction: InfoTransaccionWallet? = supabaseClient.postgrest["transaccion"]
                .select(Columns.list("usuario_id", "tipo_qr", "duracion_qr", "codigo_qr", "fechaCompra")) {
                    filter { eq("usuario_id", userId) }
                    order("fechaCompra", Order.DESCENDING) // Obtener la más reciente.
                    limit(1)                               // Solo una transacción.
                }
                .decodeSingleOrNull<InfoTransaccionWallet>() // Asegúrate que InfoTransaccionWallet está definida y es Serializable.

            if (latestTransaction == null) {
                Log.d("WebViewDebug", "ticketValido: No hay transacciones para $userId")
                return false // No hay transacciones, por lo tanto no hay ticket válido.
            }
            Log.d("WebViewDebug", "ticketValido: Última transacción encontrada ${latestTransaction.codigo_qr}")

            // Lógica específica para tickets de tipo "viaje"
            if (latestTransaction.duracion_qr == "viaje") {
                val scanCount = numeroEscaneos(latestTransaction.codigo_qr) // Obtener conteo de escaneos desde SharedPreferences.
                val isExpiredByTime = TicketUtils.transaccionExpirada(latestTransaction) // Verificar expiración por tiempo.
                Log.d("WebViewDebug", "ticketValido: Boleto de viaje ${latestTransaction.codigo_qr}. Escaneos: $scanCount, Expirado por tiempo: $isExpiredByTime")
                if (scanCount >= 2 || isExpiredByTime) { // Si se usó 2 veces o expiró por tiempo.
                    if (scanCount >= 2) reinicioEscaneo(latestTransaction.codigo_qr) // Reiniciar conteo si se usaron los escaneos.
                    return false // El ticket de viaje ya no es válido.
                }
                return true // El ticket de viaje es válido.
            }

            // Para otros tipos de tickets (día, semana, mes), solo verificar la expiración por tiempo.
            val isExpired = TicketUtils.transaccionExpirada(latestTransaction)
            Log.d("WebViewDebug", "ticketValido: Boleto no-viaje ${latestTransaction.codigo_qr}. Expirado: $isExpired")
            return !isExpired // El ticket es válido si NO ha expirado.
        } catch (e: Exception) {
            Log.e("WebViewDebug", "ticketValido: Error al verificar para $userId: ${e.message}", e)
            return false // En caso de error, asumir que no hay ticket válido para seguridad.
        }
    }

    /**
     * Obtiene el número de veces que un ticket de tipo "viaje" ha sido escaneado.
     * Lee el contador desde [SharedPreferences], específico para el `codigoQr` dado.
     * @param codigoQr El código QR único del ticket.
     * @return El número de escaneos registrados para ese ticket, o 0 si no hay registro.
     */
    private fun numeroEscaneos(codigoQr: String): Int {
        val contador = sharedPreferences.getInt("scan_count_$codigoQr", 0)
        // Log.d("WebViewDebug", "numeroEscaneos: Conteo para $codigoQr es $contador") // Log opcional
        return contador
    }

    /**
     * Reinicia el contador de escaneos para un ticket de tipo "viaje" en [SharedPreferences].
     * Se llama cuando un ticket de viaje ha sido utilizado sus 2 veces.
     * @param codigoQr El código QR único del ticket cuyo contador se reiniciará.
     */
    private fun reinicioEscaneo(codigoQr: String) {
        sharedPreferences.edit().remove("scan_count_$codigoQr").apply()
        Log.d("WebViewDebug", "reinicioEscaneo: Contador limpiado para $codigoQr")
    }

    /**
     * Obtiene el ID del usuario que ha iniciado sesión desde [SharedPreferences].
     * @return El ID del usuario como [String], o `null` si no se encuentra o no hay sesión.
     * @see Constantes.KEY_USER_ID
     */
    private fun getLoggedInUserId(): String? {
        val userId = sharedPreferences.getString(Constantes.KEY_USER_ID, null)
        // Log.d("WebViewDebug", "getLoggedInUserId: User ID es $userId") // Log opcional
        return userId
    }

    /**
     * Crea y retorna una instancia configurada del cliente de Supabase.
     * <p>
     * <b>ADVERTENCIA DE SEGURIDAD:</b> Las credenciales de Supabase (URL y clave anónima)
     * están hardcodeadas. Esto es una mala práctica. Considere almacenarlas de forma más segura.
     * </p>
     * @return Una instancia de [SupabaseClient].
     */
    private fun getClient(): SupabaseClient {
        // ¡¡¡MALA PRÁCTICA DE SEGURIDAD!!! NO INCRUSTAR CLAVES DE API EN EL CÓDIGO FUENTE.
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc"
        ) {
            install(Postgrest) // Instala el plugin Postgrest para interactuar con la base de datos.
        }
    }

    /**
     * Enumeración para representar los posibles resultados de la operación del poller de URL.
     * Ayuda a gestionar el estado de finalización del poller.
     * @property SUCCESS El poller detectó una URL de éxito.
     * @property TIMEOUT El poller alcanzó el número máximo de intentos sin éxito.
     * @property CANCELLED_BY_NAVIGATION El poller fue detenido porque el WebView navegó a una URL diferente.
     * @property NONE Estado inicial o si el poller fue detenido por otra razón no específica.
     */
    enum class PollerResult { SUCCESS, TIMEOUT, CANCELLED_BY_NAVIGATION, NONE }

    /**
     * Muestra un [BottomSheetDialog] que contiene un [WebView] para cargar la URL de pago de Stripe.
     * <p>
     * Configura el [WebViewClient] para monitorear la carga de páginas y detectar URLs de
     * éxito o cancelación. Inicia un "poller" ([startUrlPoller]) que verifica periódicamente la URL del
     * WebView como mecanismo de respaldo para detectar el éxito del pago si los callbacks
     * de `onPageFinished` no son suficientes o se retrasan.
     * </p>
     * <p>
     * Maneja el cierre del diálogo para procesar el resultado del pago (exitoso, cancelado, timeout).
     * </p>
     * @param urlToLoad La URL de la pasarela de pago de Stripe que se cargará en el WebView.
     * @see startUrlPoller
     * @see stopUrlPoller
     * @see procesarLogicaDeExito
     * @see redirigirAQR
     */
    private fun mostrarBottomSheet(urlToLoad: String) {
        Log.d("WebViewDebug", "mostrarBottomSheet: Preparando para URL: $urlToLoad")
        val bottomSheetDialog = BottomSheetDialog(this)
        // Recrear o limpiar WebView para asegurar un estado limpio
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
        webView.settings.domStorageEnabled = true // Necesario para algunas páginas de pago de Stripe
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE // No usar caché para la página de pago

        webView.isFocusable = true // Para permitir interacción
        webView.isFocusableInTouchMode = true

        // Variables locales al contexto de este BottomSheet para rastrear el estado del pago
        var pagoExitoso = false
        var cierreManual = true // Asumir cierre manual hasta que se confirme éxito, cancelación o timeout
        var isInitialLoad = true // Flag para la primera carga de la URL de pago
        var operacionCanceladaPorTimeout = false // Flag para saber si el poller causó el cierre
        val originalUrlForWebView = urlToLoad // Guardar la URL original para comparaciones posteriores

        Log.d("WebViewDebug", "mostrarBottomSheet: Vars init. pagoExitoso=$pagoExitoso, cierreManual=$cierreManual, isInitialLoad=$isInitialLoad")

        // Configuración del WebViewClient para manejar eventos de carga de página
        webView.webViewClient = object : WebViewClient() {
            /**
             * Se llama cuando el WebView comienza a cargar una URL.
             * Útil para detectar si el usuario navega fuera de la página de pago original.
             * @param view El WebView que está cargando la URL.
             * @param currentUrl La URL que se está comenzando a cargar.
             * @param favicon El favicon de la página, si está disponible.
             */
            override fun onPageStarted(view: WebView?, currentUrl: String?, favicon: Bitmap?) {
                super.onPageStarted(view, currentUrl, favicon)
                Log.d("WebViewDebug", "onPageStarted: $currentUrl, isInitialLoad: $isInitialLoad")
                // Si la página navega a una URL diferente de la original y el poller está activo,
                // se detiene el poller, ya que el flujo de pago podría haber cambiado.
                if (currentUrl != originalUrlForWebView && pollerRunnable != null) {
                    Log.d("WebViewDebug", "onPageStarted: Navegación a nueva URL ($currentUrl) detectada, deteniendo poller.")
                    stopUrlPoller(PollerResult.CANCELLED_BY_NAVIGATION)
                }
            }

            /**
             * Se llama cuando el WebView ha terminado de cargar una URL.
             * <p>
             * Aquí se implementa la lógica principal de detección de éxito/cancelación
             * basándose en la URL final. Si es la carga inicial de la URL de pago,
             * se inicia el poller de URL como respaldo.
             * </p>
             * @param view El WebView que ha terminado de cargar.
             * @param currentUrl La URL que se ha terminado de cargar.
             */
            override fun onPageFinished(view: WebView?, currentUrl: String?) {
                super.onPageFinished(view, currentUrl)
                Log.d("WebViewDebug", "onPageFinished - URL: $currentUrl, isInitialLoad: $isInitialLoad, pagoExitoso: $pagoExitoso, originalUrl: $originalUrlForWebView")

                // Path específico en la URL de Stripe que indica éxito (ej: /c/pay/ después del dominio)
                val specificSuccessPath = "/c/pay/"
                val cancelUrlKeyword = "cancel" // Palabra clave para URLs de cancelación de Stripe

                // Si es la primera carga de la URL original de pago
                if (isInitialLoad && currentUrl == originalUrlForWebView) {
                    Log.d("WebViewDebug", "  -> Carga inicial de '$currentUrl'. Marcando isInitialLoad = false.")
                    isInitialLoad = false
                    // Si el pago aún no se ha marcado como exitoso, iniciar el poller de URL.
                    // El poller verificará periódicamente si la URL del WebView cambia a la de éxito.
                    if (!pagoExitoso) {
                        Log.d("WebViewDebug", "  -> Carga inicial completa, iniciando poller.")
                        startUrlPoller(bottomSheetDialog, originalUrlForWebView) { detectedResult ->
                            // Callback del poller (se ejecuta cuando el poller termina o detecta algo)
                            when (detectedResult) {
                                PollerResult.SUCCESS -> { // El poller detectó la URL de éxito
                                    if (!pagoExitoso) { // Procesar solo una vez para evitar múltiples llamadas
                                        pagoExitoso = true
                                        cierreManual = false // El cierre fue programático debido al éxito
                                    }
                                }
                                PollerResult.TIMEOUT -> { // El poller alcanzó el tiempo límite sin detectar éxito
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
                            // Mostrar Toast y cerrar diálogo después de un breve delay para que el usuario vea el mensaje
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
                        // Si el poller no está activo (ya terminó o fue detenido), y no hay pago exitoso, reiniciarlo.
                        if(pollerRunnable == null && !pagoExitoso){
                            Log.d("WebViewDebug", "    --> Reiniciando poller porque estamos en URL original y poller no activo.")
                            startUrlPoller(bottomSheetDialog, originalUrlForWebView) { detectedResult ->
                                // Callback del poller (reintento)
                                when (detectedResult) {
                                    PollerResult.SUCCESS -> { if (!pagoExitoso) { pagoExitoso = true; cierreManual = false } }
                                    PollerResult.TIMEOUT -> {
                                        if (!pagoExitoso && bottomSheetDialog.isShowing) {
                                            operacionCanceladaPorTimeout = true; cierreManual = false
                                            Toast.makeText(this@SistemaPagosActivity, "Operación cancelada por tiempo límite (reintento).", Toast.LENGTH_LONG).show()
                                            bottomSheetDialog.dismiss()
                                        }
                                    }
                                    else -> {} // No hacer nada para otros casos
                                }
                            }
                        }
                    } else {
                        Log.d("WebViewDebug", "    --> URL ($currentUrl) no coincide con patrones conocidos de éxito/cancelación.")
                    }
                } else if (currentUrl == null) {
                    Log.w("WebViewDebug", "  -> onPageFinished: currentUrl es null.")
                } else { // Caso: isInitialLoad == true PERO currentUrl != originalUrlForWebView (redirección temprana no esperada)
                    Log.d("WebViewDebug", "  -> onPageFinished: Aún en carga inicial (isInitialLoad=true), pero URL ($currentUrl) es diferente de la original ($originalUrlForWebView).")
                    isInitialLoad = false // Marcar como no inicial, la carga principal ha terminado o redirigido.
                    // El poller, si se inicia, manejará la URL actual.
                }
            }

            /**
             * Se llama si ocurre un error al cargar una URL en el WebView.
             * Muestra un mensaje de error al usuario.
             * @param view El WebView que encontró el error.
             * @param request La solicitud web que causó el error.
             * @param error El error específico que ocurrió.
             */
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val errorMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    "Error: ${error?.errorCode} ${error?.description} en ${request?.url}"
                } else {
                    "Error cargando ${request?.url}"
                }
                Log.e("WebViewError", errorMessage)
                Toast.makeText(this@SistemaPagosActivity, "Error cargando página de pago. Verifica tu conexión.", Toast.LENGTH_LONG).show()
                // Considerar cerrar el BottomSheet aquí si el error es crítico y no recuperable.
                // if (bottomSheetDialog.isShowing) bottomSheetDialog.dismiss()
            }
        }

        // Listener para cuando el BottomSheetDialog se cierra (por cualquier motivo: swipe, botón atrás, dismiss programático).
        bottomSheetDialog.setOnDismissListener {
            Log.d("WebViewDebug", "BottomSheet DISMISS Listener. pagoExitoso: $pagoExitoso, cierreManual: $cierreManual, timeout: $operacionCanceladaPorTimeout")
            stopUrlPoller(PollerResult.NONE) // Detener el poller para limpiar recursos.
            mHandler.removeCallbacksAndMessages(null) // Limpiar todos los callbacks del handler principal asociados a este diálogo.

            if (pagoExitoso) {
                Log.i("WebViewDebug", "  --> Redirigiendo a QR porque pagoExitoso es true.")
                redirigirAQR() // Ir a la pantalla del QR si el pago fue exitoso.
            } else if (operacionCanceladaPorTimeout) {
                // El mensaje de timeout ya se mostró desde el poller, no hacer nada más aquí.
                Log.w("WebViewDebug", "  --> Operación cancelada por timeout, no se muestra Toast adicional en dismiss.")
            } else if (cierreManual) {
                // Si el usuario cerró manualmente el diálogo antes de completar/cancelar el pago.
                Log.w("WebViewDebug", "  --> Transacción no completada (cierre manual).")
                Toast.makeText(this@SistemaPagosActivity, "Transacción no completada.", Toast.LENGTH_LONG).show()
            } else {
                // Otro caso: pago no exitoso, no cierre manual, no timeout (ej: cancelación explícita desde Stripe).
                // El mensaje de "Pago cancelado" ya se mostró en onPageFinished si fue una cancelación de Stripe.
                Log.i("WebViewDebug", "  --> Pago no exitoso (ej. cancelación explícita), no cierre manual, no timeout.")
            }
            transaccionPendiente = null // Limpiar la transacción pendiente en cualquier caso de cierre.
        }

        // Cargar la URL inicial en el WebView y mostrar el BottomSheet.
        Log.d("WebViewDebug", "loadUrl: $urlToLoad")
        webView.loadUrl(urlToLoad)
        bottomSheetDialog.setContentView(webView)
        bottomSheetDialog.show()
    }

    /**
     * Procesa la lógica de un pago exitoso detectado.
     * <p>
     * Intenta guardar la [transaccionPendiente] en la base de datos Supabase.
     * Detiene el poller de URL.
     * Invoca el callback `onResult` con `true` si la inserción en Supabase es exitosa, `false` si no.
     * Si la inserción es exitosa, muestra un Toast y cierra el diálogo después de un breve retraso.
     * </p>
     * @param dialog El [BottomSheetDialog] actual.
     * @param onResult Callback que se invoca con el resultado de la operación de guardado en Supabase.
     */
    private fun procesarLogicaDeExito(dialog: BottomSheetDialog, onResult: (Boolean) -> Unit) {
        Log.i("WebViewDebug", "procesarLogicaDeExito: Procesando...")
        stopUrlPoller(PollerResult.SUCCESS) // Detener el poller porque se detectó éxito.

        lifecycleScope.launch { // Corutina para la operación de base de datos.
            var successInDb = false
            try {
                transaccionPendiente?.let { transaccion ->
                    supabaseClient.postgrest["transaccion"].insert(transaccion) // Insertar en Supabase.
                    Log.i("SupabaseInsert", "Transacción registrada exitosamente en Supabase: ${transaccion.codigo_qr}")
                    successInDb = true
                } ?: run {
                    Log.w("SupabaseInsert", "Transacción pendiente era null al intentar registrar el éxito. No se puede guardar en DB.")
                    successInDb = false // No se puede registrar si no hay transacción pendiente.
                }
            } catch (e: Exception) {
                Log.e("SupabaseInsert", "Error al registrar transacción en Supabase: ${e.message}", e)
                successInDb = false
            }
            // Volver al hilo principal para actualizar UI y manejar el resultado.
            withContext(Dispatchers.Main) {
                onResult(successInDb) // Informar al llamador (onPageFinished/poller) si la DB fue exitosa.
                if (successInDb) {
                    // Mostrar Toast y cerrar el diálogo después de un breve delay para que el usuario vea el mensaje.
                    mHandler.postDelayed({
                        if (dialog.isShowing) { // Comprobar si el diálogo aún se muestra.
                            Toast.makeText(this@SistemaPagosActivity, "Pago exitoso. Redirigiendo...", Toast.LENGTH_SHORT).show()
                            dialog.dismiss() // Cierra el diálogo, lo que activará su onDismissListener.
                        }
                    }, 500) // 500ms delay.
                } else {
                    // Si la inserción en Supabase falló.
                    Toast.makeText(this@SistemaPagosActivity, "Error al confirmar la transacción con la base de datos. Por favor, contacta a soporte.", Toast.LENGTH_LONG).show()
                    // Podrías decidir cerrar el diálogo aquí también o dejarlo abierto para que el usuario vea el error.
                    // Si se cierra, el onDismissListener se encargará de la lógica de no éxito.
                }
            }
        }
    }

    /**
     * Inicia un "poller" que verifica periódicamente la URL actual del [WebView].
     * <p>
     * Este mecanismo se utiliza como respaldo si el callback `onPageFinished` del [WebViewClient]
     * no detecta la URL de éxito de Stripe de manera fiable o inmediata (por ejemplo, debido a
     * redirecciones complejas o iframes). El poller comprueba la URL a intervalos regulares
     * ([POLL_INTERVAL]) hasta un número máximo de intentos ([MAX_POLLS]).
     * </p>
     * <p>
     * Si detecta la URL de éxito, invoca [procesarLogicaDeExito]. Si alcanza el timeout,
     * considera el pago como fallido por tiempo límite.
     * </p>
     * @param dialog El [BottomSheetDialog] que contiene el WebView.
     * @param originalUrl La URL original que se cargó en el WebView, para referencia.
     * @param onPollerComplete Callback que se invoca cuando el poller termina (por éxito, timeout,
     *                         o cancelación), pasando un [PollerResult].
     */
    private fun startUrlPoller(dialog: BottomSheetDialog, originalUrl: String, onPollerComplete: (PollerResult) -> Unit) {
        if (pollerRunnable != null || !::webView.isInitialized) {
            Log.d("WebViewDebug", "startUrlPoller: Poller ya activo o WebView no lista. No se inicia. PollerRunnable null? ${pollerRunnable == null}")
            return // Evitar iniciar múltiples pollers o si webView no está lista.
        }
        if (!dialog.isShowing) {
            Log.d("WebViewDebug", "startUrlPoller: Dialogo no se muestra, no se puede iniciar poller.")
            onPollerComplete(PollerResult.NONE) // No se puede ejecutar si el diálogo no está visible.
            return
        }

        Log.d("WebViewDebug", "startUrlPoller: Iniciando para URL original: $originalUrl. MAX_POLLS=$MAX_POLLS, INTERVAL=$POLL_INTERVAL")
        pollCount = 0 // Resetear contador de polls para esta nueva instancia del poller.

        // Crear el Runnable para el poller.
        pollerRunnable = object : Runnable {
            override fun run() {
                // Condiciones para detener el poller prematuramente.
                if (!dialog.isShowing || !::webView.isInitialized || pollerRunnable == null) {
                    // Si el poller fue detenido (pollerRunnable == null) o las condiciones cambiaron, no continuar.
                    if (pollerRunnable != null) { // Solo si no fue detenido explícitamente por stopUrlPoller.
                        stopUrlPoller(PollerResult.NONE) // Detener formalmente.
                        onPollerComplete(PollerResult.NONE) // Informar que no hubo resultado concluyente.
                    }
                    return
                }

                pollCount++ // Incrementar contador de intentos.
                val actualUrl = webView.url // Obtener URL actual del WebView.
                Log.d("WebViewDebug", "  --> Poll #$pollCount/$MAX_POLLS: URL actual del WebView: $actualUrl")

                // Verificar si la URL actual es la de éxito de Stripe (ej: contiene "/c/pay/").
                if (actualUrl?.contains("/c/pay/") == true) { // Ajustar este path si es diferente.
                    Log.i("WebViewDebug", "    --> Éxito detectado por Poller en URL: $actualUrl")
                    // No llamar a stopUrlPoller aquí directamente, procesarLogicaDeExito lo hará.
                    procesarLogicaDeExito(dialog) { successDb ->
                        // El callback onPollerComplete se llama aquí para reflejar el resultado de la DB.
                        onPollerComplete(if (successDb) PollerResult.SUCCESS else PollerResult.NONE)
                    }
                }
                // Verificar si se alcanzó el máximo de polls (timeout).
                else if (pollCount >= MAX_POLLS) {
                    Log.w("WebViewDebug", "    --> Poller alcanzó MAX_POLLS sin éxito. URL actual: $actualUrl. Considerado TIMEOUT.")
                    stopUrlPoller(PollerResult.TIMEOUT) // Detener el poller indicando timeout.
                    onPollerComplete(PollerResult.TIMEOUT) // Informar timeout al llamador.
                }
                // Si no es éxito ni timeout, y el poller no ha sido detenido, programar el siguiente poll.
                else {
                    if (pollerRunnable != null) { // Asegurarse que no se haya detenido mientras tanto.
                        mHandler.postDelayed(this, POLL_INTERVAL)
                    }
                }
            }
        }
        // Programar la primera ejecución del poller.
        mHandler.postDelayed(pollerRunnable!!, POLL_INTERVAL)
    }

    /**
     * Detiene el poller de URL si está activo.
     * Remueve el `pollerRunnable` de la cola de mensajes del `mHandler` y establece `pollerRunnable` a `null`.
     * @param reason El [PollerResult] que indica el motivo por el que se detiene el poller (opcional, para logging).
     */
    private fun stopUrlPoller(reason: PollerResult = PollerResult.NONE) {
        pollerRunnable?.let { // Si el runnable existe (el poller está activo).
            mHandler.removeCallbacks(it) // Removerlo de la cola del handler.
            Log.d("WebViewDebug", "stopUrlPoller: Runnable del poller removido. Razón: $reason")
        }
        pollerRunnable = null // Marcar como nulo para permitir que se inicie de nuevo si es necesario.
    }

    /**
     * Redirige al usuario a [InicioActivity] para mostrar el código QR del ticket comprado.
     * Se llama después de un proceso de pago exitoso y la confirmación de la transacción.
     * Pasa un extra "navigate_to_wallet" para que [InicioActivity] sepa que debe
     * navegar directamente al fragmento del wallet/QR.
     * Limpia la pila de actividades para que el usuario no pueda volver a la pantalla de pagos.
     */
    private fun redirigirAQR() {
        Log.i("WebViewDebug", "redirigirAQR: Iniciando Intent a InicioActivity.")
        val intent = Intent(this, InicioActivity::class.java).apply {
            putExtra("navigate_to_wallet", true) // Extra para indicar a InicioActivity que navegue al wallet.
            // Flags para limpiar la pila de actividades y empezar InicioActivity como nueva tarea.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        Log.i("WebViewDebug", "redirigirAQR: startActivity llamado.")
        finish() // Finalizar esta actividad (SistemaPagosActivity).
        Log.i("WebViewDebug", "redirigirAQR: finish() llamado.")
    }

    /**
     * Maneja la selección de ítems en el menú de la ActionBar.
     * Específicamente, gestiona la acción del botón de "home" (flecha de regreso)
     * para finalizar la actividad actual.
     * @param item El [MenuItem] que fue seleccionado.
     * @return `true` si el evento fue manejado, `false` en caso contrario.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { // Si se presiona el botón de "home" (flecha de regreso en la ActionBar).
                Log.d("WebViewDebug", "onOptionsItemSelected: Botón Home presionado.")
                finish() // Finalizar la actividad actual, regresando a la anterior en la pila.
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Se llama cuando la actividad está siendo destruida.
     * <p>
     * Es crucial limpiar recursos aquí para evitar fugas de memoria, especialmente
     * el [WebView], el poller de URL y cualquier callback pendiente en el [mHandler].
     * </p>
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("WebViewDebug", "SistemaPagosActivity onDestroy.")
        stopUrlPoller(PollerResult.NONE) // Asegura que el poller esté detenido.
        mHandler.removeCallbacksAndMessages(null) // Limpiar todos los callbacks del handler principal.

        // Destruir el WebView para liberar recursos y evitar memory leaks.
        // Es importante hacerlo correctamente.
        if (::webView.isInitialized) {
            Log.d("WebViewDebug", "Destruyendo WebView...")
            val parent = webView.parent
            if (parent is ViewGroup) {
                parent.removeView(webView) // Quitar WebView de su layout padre.
            }
            webView.stopLoading() // Detener cualquier carga en progreso.
            webView.settings.javaScriptEnabled = false // Deshabilitar JS para seguridad y limpieza.
            webView.clearHistory() // Limpiar historial del WebView.
            webView.loadUrl("about:blank") // Cargar página en blanco para ayudar a liberar recursos.
            webView.removeAllViews() // Remover vistas hijas del WebView (si las tuviera).
            webView.destroy() // Destruir la instancia del WebView.
            Log.d("WebViewDebug", "WebView destruido.")
        }
        transaccionPendiente = null // Limpiar la referencia a la transacción pendiente.
    }
}