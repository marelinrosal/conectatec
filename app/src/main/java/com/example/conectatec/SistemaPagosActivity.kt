package com.example.conectatec

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
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


//Clase de datos para la información del ticket.
data class TicketInfo(
    val url: String,
    val tipo: String, // "urbano" o "interurbano"
    val duracion: String, // "dia", "semana", "mes", "viaje"
    val costo: Double,
    val fecha: String
)

//Clase de datos de la transacción, enfocada en la base de datos
//El serializable es porque en la base de datos es fechaCompra y
//en el código es fecha_compra
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


class SistemaPagosActivity : AppCompatActivity() {
    private lateinit var btnPagar: Button
    private var selectedTicketInfo: TicketInfo? = null
    private val stripePublishableKey = "pk_test_51R5VpuLlYPVHsiF1QcQnwjIuiXdv8hZ0l1uJe3dvVyzVplOBn22XXClKKYeyl7JHQu9oqPJ2wwXiO0AVPa0wQdFi00WMA5kqln"
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var usuarioInteractuando = false
    private var transaccionPendiente: TransaccionData? = null // Almacena la transacción pendiente

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var supabaseClient: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sistema_pagos)

        sharedPreferences = getSharedPreferences(Constantes.PREFS_NAME, MODE_PRIVATE)
        supabaseClient = getClient()

        val toolbar = findViewById<Toolbar>(R.id.toolbar2)

        //Se hace una tool bar para poder regresarse
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sistema de Pagos"

        //Se inicializa el libreria de Stripe.
        PaymentConfiguration.init(this, stripePublishableKey)

        //Inicialización de componentes gráficos.
        btnPagar = findViewById(R.id.btnPagar)
        btnPagar.isEnabled = false
        val grupoInterurbano = findViewById<RadioGroup>(R.id.grupoInterurbano)
        val grupoUrbano = findViewById<RadioGroup>(R.id.grupoUrbano)
        val currentDate = Instant.now().toString()

        //Lista de precios.
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


        //Activación de grupos de botones
        grupoInterurbano.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                grupoUrbano.clearCheck()
                selectedTicketInfo = ticketInfoMap[checkedId]
                btnPagar.isEnabled = true
            } else {
                if (grupoUrbano.checkedRadioButtonId == -1) {
                    selectedTicketInfo = null
                    btnPagar.isEnabled = false
                }
            }
        }

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

        //Rutina de btnPagar, en esta rutina hace que se despliegue el bottom sheet, el cual desplegara
        //el servicio de pago y la base de datos para que se almacene la transacción. Al igual de que enviara
        //Notificaciones para averiguar lo que pasa dentro de la transacción.
        btnPagar.setOnClickListener {
            val currentTicketInfo = selectedTicketInfo
            val userId = getLoggedInUserId()

            if (currentTicketInfo != null && userId != null) {
                lifecycleScope.launch {
                    // Verificar si hay un boleto vigente
                    val hasValidTicket = ticketValido(userId)
                    withContext(Dispatchers.Main) {
                        if (hasValidTicket) {
                            Toast.makeText(this@SistemaPagosActivity, "Ya tienes un boleto vigente. No puedes comprar otro hasta que expire.", Toast.LENGTH_LONG).show()
                            return@withContext
                        }

                        // Crear transacción pendiente
                        transaccionPendiente = TransaccionData(
                            codigo_qr = UUID.randomUUID().toString(),
                            usuario_id = userId,
                            tipo_qr = currentTicketInfo.tipo,
                            duracion_qr = currentTicketInfo.duracion,
                            costo_qr = currentTicketInfo.costo,
                            fecha_compra = currentTicketInfo.fecha
                        )

                        Toast.makeText(this@SistemaPagosActivity, "Procediendo al pago...", Toast.LENGTH_SHORT).show()
                        mostrarBottomSheet(currentTicketInfo.url)
                    }
                }
            } else if (userId == null) {
                Toast.makeText(this, "Error: No se pudo obtener el ID de usuario. Intenta reiniciar sesión.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Selecciona una opción de boleto primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //Función que verifica si se compro anteriormente un ticket, si se compro anteriormente
    //Se bloquea la opción de comprar un boleto. En el caso de un viaje solo permite 2 escaneos
    //El cual este méotodo nos notificara si el boleto ya se uso o no para poder comprar otros.
    private suspend fun ticketValido(userId: String): Boolean {
        return try {
            val latestTransaction: InfoTransaccionWallet? = supabaseClient.postgrest["transaccion"]
                .select(Columns.list("usuario_id", "tipo_qr", "duracion_qr", "codigo_qr", "fechaCompra")) {
                    filter {
                        eq("usuario_id", userId)
                    }
                    order("fechaCompra", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull()

            if (latestTransaction == null) {
                Log.d("SistemaPagosActivity2", "No transactions found for user $userId")
                return false
            }

            // Para boletos "viaje", verificar expiración y número de escaneos
            if (latestTransaction.duracion_qr == "viaje") {
                val scanCount = numeroEscaneos(latestTransaction.codigo_qr)
                Log.d("SistemaPagosActivity2", "Checking viaje ticket ${latestTransaction.codigo_qr}: scanCount=$scanCount, expired=${TicketUtils.transaccionExpirada(latestTransaction)}")
                if (scanCount >= 2 || TicketUtils.transaccionExpirada(latestTransaction)) {
                    Log.d("SistemaPagosActivity2", "Viaje ticket ${latestTransaction.codigo_qr} invalidated due to scan count >= 2 or expired")
                    reinicioEscaneo(latestTransaction.codigo_qr)
                    return false
                }
                return true
            }

            // Para otros boletos, solo verificar expiración
            val isExpired = TicketUtils.transaccionExpirada(latestTransaction)
            Log.d("SistemaPagosActivity2", "Checking non-viaje ticket ${latestTransaction.codigo_qr}: expired=$isExpired")
            return !isExpired
        } catch (e: Exception) {
            Log.e("SistemaPagosActivity2", "Error checking valid ticket: ${e.message}", e)
            return false
        }
    }

    //Esta funcion se encarga de contar los escaneos que hubo dentro del viaje.
    private fun numeroEscaneos(codigoQr: String): Int {
        val contador = sharedPreferences.getInt("scan_count_$codigoQr", 0)
        Log.d("SistemaPagosActivity2", "Retrieved scan count for $codigoQr: $contador")
        return contador
    }

    //Esta funcion reinicia el numero de escaneos
    private fun reinicioEscaneo(codigoQr: String) {
        sharedPreferences.edit().remove("scan_count_$codigoQr").apply()
        Log.d("SistemaPagosActivity2", "Cleared scan count for $codigoQr")
    }

    //Loggeo de user
    private fun getLoggedInUserId(): String? {
        return sharedPreferences.getString(Constantes.KEY_USER_ID, null)
    }

    //Acceso a los servicios de suopabase
    private fun getClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc"
        ) {
            install(Postgrest)
        }
    }

    //Función que despliega el bottom sheet que tiene el servicio de pagos.
    private fun mostrarBottomSheet(url: String) {
        val bottomSheetDialog = BottomSheetDialog(this)
        if (::webView.isInitialized && webView.parent != null) {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
        webView = WebView(this)

        webView.clearCache(true)
        webView.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        var pagoExitoso = false
        var cierreManual = true
        var isInitialLoad = true

        // Variable que detecta los movimientos del usuario

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            //Las siguientes funciones detectan el movimiento, cuando no detecta movimiento alrededor de cierto tiempo,
            //se refresca la pantalla.
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


        //Deteccion de movimientos en el web view, mas especializados en la web, para evitar
        // Que se se refresque al tiempo incorrecto.
        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
        webView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                usuarioInteractuando = true
                Log.d("WebViewInteraction", "onFocusChange detectado (hasFocus=true)")
            }
        }
        webView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                usuarioInteractuando = true
                Log.d("WebViewInteraction", "Tecla presionada: $keyCode")
            }
            false
        }
        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun onInputFocus() {
                usuarioInteractuando = true
                Log.d("WebViewInteraction", "Campo de entrada recibió foco (probable uso de teclado)")
            }
        }, "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("WebView", "Iniciando carga de: $url")
                usuarioInteractuando = false
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("WebView", "Página cargada: $url")

                view?.evaluateJavascript(
                    """
                    (function() {
                        var inputs = document.querySelectorAll('input, textarea, select');
                        inputs.forEach(function(input) {
                            input.addEventListener('focus', function() {
                                Android.onInputFocus();
                            });
                        });
                    })();
                    """.trimIndent(),
                    null
                )


                //Variables que nos ayudan a identificar el estado del pago
                val successUrlPattern = "pay"
                val cancelUrlPattern = "cancel"


                //Aqui se checa la url en la que se encuentra el web page
                if (url != null && !isInitialLoad) {
                    if (url.contains(successUrlPattern, ignoreCase = true)) {
                        Log.i("WebView", "URL de éxito detectada: $url")
                        lifecycleScope.launch {
                            try {
                                transaccionPendiente?.let { transaccion ->
                                    supabaseClient.postgrest["transaccion"].insert(transaccion)
                                    Log.i("SupabaseInsert", "Transacción registrada exitosamente con código: ${transaccion.codigo_qr}")
                                    withContext(Dispatchers.Main) {
                                        pagoExitoso = true
                                        cierreManual = false
                                        handler.postDelayed({
                                            if (bottomSheetDialog.isShowing) {
                                                Toast.makeText(this@SistemaPagosActivity, "Pago exitoso. Redirigiendo...", Toast.LENGTH_SHORT).show()
                                                bottomSheetDialog.dismiss()
                                            }
                                        }, 500)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("SupabaseInsert", "Error al registrar transacción: ${e.message}", e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@SistemaPagosActivity, "Error al confirmar la transacción.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else if (url.contains(cancelUrlPattern, ignoreCase = true)) {
                        Log.w("WebView", "URL de cancelación detectada: $url")
                        pagoExitoso = false
                        cierreManual = false
                        handler.postDelayed({
                            if (bottomSheetDialog.isShowing) {
                                Toast.makeText(this@SistemaPagosActivity, "Pago cancelado.", Toast.LENGTH_SHORT).show()
                                bottomSheetDialog.dismiss()
                            }
                        }, 500)
                    }
                } else if (isInitialLoad) {
                    Log.d("WebView", "Ignorando carga inicial: $url")
                    isInitialLoad = false
                }
            }


            //Funcion que se encarga en los errores que sucedan en el web page
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.e("WebViewError", "Error: ${error?.errorCode} ${error?.description} en ${request?.url}")
                } else {
                    Log.e("WebViewError", "Error cargando ${request?.url}")
                }
            }
        }


        //Cierre para el bottom sheet, dependiendo del exito de la transaccion
        bottomSheetDialog.setOnDismissListener {
            Log.d("BottomSheet", "Cerrado. Exitoso: $pagoExitoso, Cierre Manual: $cierreManual")
            handler.removeCallbacksAndMessages(null)

            if (pagoExitoso) {
                redirigirAQR()
            } else if (cierreManual) {
                Toast.makeText(this@SistemaPagosActivity, "Transacción no completada.", Toast.LENGTH_LONG).show()
            }
            transaccionPendiente = null // Limpiar transacción pendiente
        }

        Log.d("WebView", "Cargando URL inicial: $url")
        webView.loadUrl(url)
        bottomSheetDialog.setContentView(webView)
        bottomSheetDialog.show()

        usuarioInteractuando = false
        iniciarRecargaAutomatica(bottomSheetDialog)
    }

    //Funcion de la recarga automatica del bottomSheet
    private fun iniciarRecargaAutomatica(bottomSheetDialog: BottomSheetDialog) {
        handler.removeCallbacksAndMessages(null)

        handler.postDelayed(object : Runnable {
            override fun run() {
                if (bottomSheetDialog.isShowing && ::webView.isInitialized && webView.parent != null) {
                    if (!usuarioInteractuando) {
                        Log.d("WebViewReload", "Recargando WebView...")
                        webView.reload()
                    } else {
                        Log.d("WebViewReload", "Omitiendo recarga (usuario interactuando)")
                    }
                    usuarioInteractuando = false
                    handler.postDelayed(this, 7000)
                } else {
                    Log.d("WebViewReload", "Deteniendo recarga (BottomSheet no visible o WebView no listo)")
                }
            }
        }, 7000)
    }

    //Funcion que te dirige al QR
    private fun redirigirAQR() {
        val intent = Intent(this, InicioActivity::class.java).apply {
            putExtra("navigate_to_wallet", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    //Funcion para el bar tool, para regresar al home
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    //Destuye el web view despues de ser usado
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) {
            webView.destroy()
        }
        transaccionPendiente = null // Limpiar transacción pendiente
        Log.d("SistemaPagosActivity", "onDestroy llamado")
    }
}