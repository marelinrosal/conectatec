package com.example.conectatec

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.sidesheet.SideSheetDialog
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale // Importado para SimpleDateFormat

/**
 * Fragmento que gestiona la "Cartera" o "Wallet" del usuario.
 * <p>
 * Muestra el código QR del último boleto vigente del usuario, junto con sus detalles
 * (código, tipo, duración, fecha de compra). Permite ver un historial de transacciones
 * en un [SideSheetDialog] y redirige a [SistemaPagosActivity] si no hay un boleto vigente.
 * </p>
 * <p>
 * Implementa lógica para:
 * <ul>
 *   <li>Validar y mostrar el QR del último boleto no expirado.</li>
 *   <li>Manejar la expiración automática del QR visible.</li>
 *   <li>Simular el escaneo del QR (incrementando un contador para boletos de tipo "viaje").</li>
 *   <li>Obtener y mostrar el historial de transacciones desde Supabase.</li>
 * </ul>
 * Utiliza [SharedPreferences] para el ID de usuario y el conteo de escaneos, y [SupabaseClient]
 * para interactuar con la base de datos.
 * </p>
 *
 * @property imagenQR [ImageView] para mostrar el código QR generado.
 * @property btnVerHistorial [TextView] (actuando como botón) para abrir el historial de transacciones.
 * @property lblCodigo [TextView] para mostrar el código del boleto.
 * @property lblTipo [TextView] para mostrar el tipo de boleto.
 * @property lblDuracion [TextView] para mostrar la duración del boleto.
 * @property lblFechaCompra [TextView] para mostrar la fecha de compra del boleto.
 * @property noBoletoMessage [TextView] que se muestra si no hay un boleto vigente.
 * @property btnPagos [Button] para redirigir a la pantalla de compra de boletos.
 * @property qrGenerado Booleano que indica si un QR está actualmente generado y visible.
 * @property handler [Handler] para programar la ocultación del QR por expiración.
 * @property ocultarQrRunnable [Runnable] que ejecuta la lógica de ocultar el QR.
 * @property sharedPreferences Instancia de [SharedPreferences].
 * @property supabaseClient Instancia del cliente de Supabase.
 * @property currentUserId ID del usuario actualmente logueado.
 * @property currentTransaction Almacena la [InfoTransaccionWallet] del boleto actualmente visible.
 * @property formateoFecha [SimpleDateFormat] para formatear las fechas (patrón "dd/MM/yyyy").
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see InfoTransaccionWallet
 * @see TicketUtils
 * @see SistemaPagosActivity
 * @see Constantes
 */
class walletFragment : Fragment() {

    // Declaración de variables para los componentes de la UI
    private lateinit var imagenQR: ImageView
    private lateinit var btnVerHistorial: TextView
    private lateinit var lblCodigo: TextView
    private lateinit var lblTipo: TextView
    private lateinit var lblDuracion: TextView
    private lateinit var lblFechaCompra: TextView
    private lateinit var noBoletoMessage: TextView
    private lateinit var btnPagos: Button
    private var qrGenerado: Boolean = false // Flag para saber si hay un QR visible

    // Handler y Runnable para la expiración automática del QR
    private val handler = Handler(Looper.getMainLooper())
    private val ocultarQrRunnable = Runnable {
        Log.d("WalletFragment2", "ocultarQrRunnable executed due to ticket expiration")
        ocultarQR(true) // true indica que se oculta por expiración de tiempo
    }

    // SharedPreferences y Supabase Client
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var supabaseClient: SupabaseClient
    private var currentUserId: String? = null // ID del usuario logueado
    private var currentTransaction: InfoTransaccionWallet? = null // Transacción del QR actualmente visible

    // Formateador de fecha. Es buena práctica incluir Locale.getDefault() o un Locale específico.
    private val formateoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    /**
     * Se llama para que el fragmento instancie su vista de interfaz de usuario.
     * Infla el layout `R.layout.fragment_wallet`.
     * @param inflater El [LayoutInflater] para inflar vistas.
     * @param container El [ViewGroup] padre al que se adjuntará la UI del fragmento.
     * @param savedInstanceState Estado previamente guardado, si existe.
     * @return La [View] raíz para la UI del fragmento.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Infla el layout para este fragmento.
        return inflater.inflate(R.layout.fragment_wallet, container, false)
    }

    /**
     * Se llama inmediatamente después de que [onCreateView] ha retornado.
     * <p>
     * Inicializa los componentes de la UI, [SharedPreferences], [SupabaseClient],
     * obtiene el ID del usuario y configura los listeners para los botones.
     * Llama a [validacionTransaccionQR] para cargar el QR del boleto vigente, si existe.
     * </p>
     * @param view La [View] devuelta por [onCreateView].
     * @param savedInstanceState Estado previamente guardado, si existe.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicialización de vistas
        imagenQR = view.findViewById(R.id.imagenQR)
        btnVerHistorial = view.findViewById(R.id.btnHistorial)
        lblCodigo = view.findViewById(R.id.lblCodigo)
        lblTipo = view.findViewById(R.id.lblTipo)
        lblDuracion = view.findViewById(R.id.lblDuracion)
        lblFechaCompra = view.findViewById(R.id.lblFechaCompra)
        noBoletoMessage = view.findViewById(R.id.noBoletoMessage)
        btnPagos = view.findViewById(R.id.BotonCompra)

        // Ocultar el botón de "Comprar Boletos" inicialmente; se mostrará si no hay boleto vigente.
        btnPagos.visibility = View.GONE

        // Inicialización de SharedPreferences y Supabase Client
        sharedPreferences = requireActivity().getSharedPreferences(Constantes.PREFS_NAME, Context.MODE_PRIVATE)
        supabaseClient = getClient()
        currentUserId = getLoggedInUserId()

        // Verifica si se pudo obtener el ID del usuario.
        if (currentUserId == null) {
            Log.e("WalletFragment", "No se encontro el usuario.") // Log existente
            Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_LONG).show()
            ocultarQR(false) // Asegura que la UI refleje que no hay boleto.
            return // No continuar si no hay usuario.
        }

        // Listener para el botón de "Comprar Boletos".
        btnPagos.setOnClickListener {
            val intent = Intent(requireContext(), SistemaPagosActivity::class.java)
            startActivity(intent)
        }

        // Listener para el TextView "Ver Historial".
        btnVerHistorial.setOnClickListener {
            currentUserId?.let { userId -> // Asegura que currentUserId no sea nulo.
                sideSheetTransaccion(userId) // Muestra el historial de transacciones.
            }
        }

        // Listener para la imagen QR (simula escaneo).
        imagenQR.setOnClickListener {
            if (qrGenerado) { // Solo si hay un QR visible.
                mostrarDialogoEscaneado()
            }
        }

        // Valida y muestra el QR del último boleto vigente del usuario.
        currentUserId?.let { userId ->
            validacionTransaccionQR(userId)
        }
    }

    /**
     * Muestra un [SideSheetDialog] con el historial de transacciones del usuario.
     * Obtiene las transacciones desde Supabase y las formatea para mostrarlas.
     * @param userId El ID del usuario cuyo historial se va a mostrar.
     */
    private fun sideSheetTransaccion(userId: String) {
        val sideSheetDialog = SideSheetDialog(requireContext())
        // Infla el layout para el contenido del SideSheet.
        val sideSheetView = layoutInflater.inflate(R.layout.sidesheet_transacciones, null) // Asume que este layout existe.

        val transactionsTextView: TextView = sideSheetView.findViewById(R.id.sideSheetTransactionsTextView) // Asume este ID.
        val btnCerrar: Button = sideSheetView.findViewById(R.id.btnCerrarSideSheet) // Asume este ID.

        btnCerrar.setOnClickListener {
            sideSheetDialog.dismiss() // Cierra el SideSheet.
        }

        // Lanza una corutina para obtener las transacciones de Supabase.
        lifecycleScope.launch {
            try {
                Log.d("WalletFragment", "Transacciones del usarui: $userId") // Log existente
                val transactions: List<InfoTransaccionWallet> = supabaseClient.postgrest["transaccion"]
                    .select(Columns.list("codigo_qr", "tipo_qr", "duracion_qr", "fechaCompra")) { // Selecciona las columnas necesarias.
                        filter { eq("usuario_id", userId) } // Filtra por el ID del usuario.
                        order("fechaCompra", Order.DESCENDING) // Ordena por fecha de compra descendente (más recientes primero).
                    }
                    .decodeList() // Decodifica la respuesta en una lista de InfoTransaccionWallet.

                Log.d("WalletFragment", "Encontrado ${transactions.size} transactions.") // Log existente
                // Actualiza la UI en el hilo principal.
                withContext(Dispatchers.Main) {
                    if (transactions.isEmpty()) {
                        transactionsTextView.text = "No tienes boletos comprados recientemente."
                    } else {
                        // Formatea cada transacción y las une con saltos de línea.
                        val displayText = transactions.joinToString(separator = "\n\n") { tx ->
                            "Código: ${tx.codigo_qr}\nTipo: ${tx.tipo_qr}\nDuración: ${tx.duracion_qr}"
                        }
                        transactionsTextView.text = displayText
                    }
                }
            } catch (e: Exception) {
                Log.e("WalletFragment2", "Error fetching transactions: ${e.message}", e) // Log existente
                withContext(Dispatchers.Main) {
                    if (isAdded) { // Comprueba si el fragmento todavía está adjunto a la actividad.
                        transactionsTextView.text = "Error al cargar el historial de boletos: ${e.message}" // Mensaje de error en el SideSheet.
                        Toast.makeText(context, "No se pudo cargar el historial: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        sideSheetDialog.setContentView(sideSheetView) // Establece la vista del SideSheet.
        sideSheetDialog.show() // Muestra el SideSheet.
    }

    /**
     * Valida la última transacción del usuario y, si es vigente, genera y muestra el código QR.
     * <p>
     * Consulta Supabase para la última transacción. Si existe y no ha expirado (según [TicketUtils]),
     * y si es un boleto de "viaje" aún no ha alcanzado el límite de escaneos,
     * muestra los detalles del boleto, genera el QR y programa su ocultación automática.
     * Si no hay boleto vigente, actualiza la UI para reflejarlo y muestra el botón de compra.
     * </p>
     * @param userId El ID del usuario.
     * @see TicketUtils.transaccionExpirada
     * @see contadorScaner
     * @see generarQR
     * @see calculaTiempoSobrante
     * @see ocultarQR
     */
    private fun validacionTransaccionQR(userId: String) {
        lifecycleScope.launch { // Corutina para operaciones de red/DB.
            try {
                Log.d("WalletFragment2", "Transaccion del Usuario: $userId") // Log existente
                // Obtiene la última transacción del usuario desde Supabase.
                val ultimaTransaccion: InfoTransaccionWallet? = supabaseClient.postgrest["transaccion"]
                    .select(Columns.list("usuario_id", "tipo_qr", "duracion_qr", "codigo_qr", "fechaCompra")) {
                        filter { eq("usuario_id", userId) }
                        order("fechaCompra", Order.DESCENDING) // Más reciente primero.
                        limit(1)                              // Solo la última.
                    }
                    .decodeSingleOrNull() // Decodifica como un solo objeto o null si no hay resultado.

                // Cambia al hilo principal para actualizar la UI.
                withContext(Dispatchers.Main) {
                    if (isAdded) { // Asegura que el fragmento esté adjunto.
                        if (ultimaTransaccion != null && !TicketUtils.transaccionExpirada(ultimaTransaccion)) {
                            // Verifica si es un boleto de "viaje" y si aún tiene escaneos disponibles.
                            val esValidoParaQR = if (ultimaTransaccion.duracion_qr == "viaje") {
                                val scanCount = contadorScaner(ultimaTransaccion.codigo_qr)
                                scanCount < 2 // Válido si ha sido escaneado menos de 2 veces.
                            } else {
                                true // Otros tipos de boletos son válidos si no han expirado por tiempo.
                            }

                            if (esValidoParaQR) {
                                // Boleto vigente y válido para mostrar QR.
                                currentTransaction = ultimaTransaccion // Guarda la transacción actual.

                                // Muestra los detalles del boleto.
                                lblCodigo.text = "Código: ${ultimaTransaccion.codigo_qr}"
                                lblTipo.text = "Tipo: ${ultimaTransaccion.tipo_qr}"
                                lblDuracion.text = "Duración: ${ultimaTransaccion.duracion_qr}"
                                lblFechaCompra.text = ultimaTransaccion.fecha_compra?.let { fc ->
                                    try {
                                        val instant = Instant.parse(fc)
                                        "Fecha De Compra: ${formateoFecha.format(Date(instant.toEpochMilli()))}"
                                    } catch (e: Exception) {
                                        Log.e("WalletFragment2", "Error al parsear fecha de compra: $fc", e) // Log existente
                                        "Fecha De Compra: (Fecha inválida)"
                                    }
                                } ?: "Fecha De Compra: N/A"

                                noBoletoMessage.visibility = View.GONE // Oculta mensaje de "sin boleto".
                                btnPagos.visibility = View.GONE       // Oculta botón de compra.


                                // Prepara los datos para el QR con la URL de la web
                                val qrData = "https://v0-qr-code-android-studio.vercel.app/?userId=${ultimaTransaccion.usuario_id ?: userId}," +
                                        "tipo=${ultimaTransaccion.tipo_qr}," +
                                        "duracion=${ultimaTransaccion.duracion_qr}," +
                                        "codigo=${ultimaTransaccion.codigo_qr}"
                                generarQR(qrData) // Genera y muestra la imagen QR.
                                qrGenerado = true   // Marca que hay un QR visible.

                                // Calcula el tiempo restante y programa la ocultación del QR.
                                val tiempoRestanteMs = calculaTiempoSobrante(ultimaTransaccion)
                                if (tiempoRestanteMs > 0) {
                                    handler.removeCallbacks(ocultarQrRunnable) // Limpia callbacks anteriores.
                                    handler.postDelayed(ocultarQrRunnable, tiempoRestanteMs) // Programa nuevo callback.
                                } else {
                                    ocultarQR(true) // Si el tiempo es 0 o negativo, ocultar inmediatamente.
                                }
                            } else {
                                // Boleto de viaje ya usado o no hay boleto válido por otras razones.
                                Toast.makeText(context, "No hay boletos vigentes para mostrar un QR.", Toast.LENGTH_LONG).show()
                                ocultarQR(false) // false indica que no es por expiración de tiempo sino por lógica de uso.
                            }
                        } else {
                            // No hay transacciones o la última ha expirado.
                            Toast.makeText(context, "No hay boletos vigentes para mostrar un QR.", Toast.LENGTH_LONG).show()
                            ocultarQR(false)
                            btnPagos.visibility = View.VISIBLE // Muestra botón de compra.
                        }
                    }
                }
            } catch (e: Exception) {
                // Error al obtener datos de Supabase.
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(context, "Error al verificar boletos vigentes: ${e.message}", Toast.LENGTH_LONG).show()
                        ocultarQR(false)
                        btnPagos.visibility = View.VISIBLE // Muestra botón de compra en caso de error.
                    }
                }
                Log.e("WalletFragment2", "Error en validacionTransaccionQR: ${e.message}", e) // Log existente
            }
        }
    }

    /**
     * Calcula el tiempo restante en milisegundos para la validez de una transacción.
     * @param transaction La [InfoTransaccionWallet] para la cual calcular el tiempo restante.
     * @return El tiempo restante en milisegundos, o 0L si ya expiró o hay un error.
     * @see TicketUtils.duracionBoleto
     */
    private fun calculaTiempoSobrante(transaction: InfoTransaccionWallet): Long {
        val fechaCompraStr = transaction.fecha_compra ?: return 0L // Si no hay fecha de compra, no hay tiempo restante.
        return try {
            val instanteCompra = Instant.parse(fechaCompraStr)
            val duracionMs = TicketUtils.duracionBoleto[transaction.duracion_qr] ?: return 0L // Si no se encuentra duración, no hay tiempo.
            val tiempoExpiracionEpochMs = instanteCompra.toEpochMilli() + duracionMs
            val tiempoActualEpochMs = System.currentTimeMillis()

            return if (tiempoExpiracionEpochMs > tiempoActualEpochMs) {
                tiempoExpiracionEpochMs - tiempoActualEpochMs // Tiempo restante positivo.
            } else {
                0L // Ya expiró o está en el momento exacto de expiración.
            }
        } catch (e: Exception) {
            Log.e("WalletFragment2", "Error parsing fecha_compra: ${e.message}", e) // Log existente
            0L // En caso de error, devuelve 0.
        }
    }

    /**
     * Genera una imagen de código QR a partir de los datos proporcionados y la muestra en `imagenQR`.
     * @param data La cadena de texto que se codificará en el QR.
     */
    private fun generarQR(data: String) {
        try {
            val barcodeEncoder = BarcodeEncoder()
            // Genera el bitmap del QR con un tamaño de 500x500 píxeles.
            val bitmap: Bitmap = barcodeEncoder.encodeBitmap(data, BarcodeFormat.QR_CODE, 500, 500)
            imagenQR.setImageBitmap(bitmap) // Establece el bitmap en el ImageView.
        } catch (e: Exception) {
            Log.e("WalletFragment2", "Error  QR ", e) // Log existente
            if (isAdded) Toast.makeText(context, "Error al generar imagen QR", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Obtiene el contador de escaneos para un código QR específico desde [SharedPreferences].
     * @param codigoQr El código QR del boleto.
     * @return El número de veces que el QR ha sido escaneado, o 0 si no hay registro.
     */
    private fun contadorScaner(codigoQr: String): Int {
        // La clave en SharedPreferences es "scan_count_" seguido del código QR.
        val count = sharedPreferences.getInt("scan_count_$codigoQr", 0)
        Log.d("WalletFragment", "Retrieved scan count for $codigoQr: $count") // Log existente
        return count
    }

    /**
     * Incrementa el contador de escaneos para un código QR específico en [SharedPreferences].
     * @param codigoQr El código QR del boleto.
     */
    private fun incrementoEscaneos(codigoQr: String) {
        val currentCount = contadorScaner(codigoQr)
        val newCount = currentCount + 1
        sharedPreferences.edit().putInt("scan_count_$codigoQr", newCount).apply()
        Log.d("WalletFragment2", "Incremented scan count for $codigoQr to $newCount") // Log existente
    }

    /**
     * Reinicia (elimina) el contador de escaneos para un código QR específico en [SharedPreferences].
     * Se usa cuando un boleto de viaje expira por tiempo, para que un futuro boleto con el
     * mismo código (si fuera posible, aunque improbable con UUIDs) no herede el conteo.
     * @param codigoQr El código QR del boleto.
     */
    private fun reinicioEscaneo(codigoQr: String) {
        sharedPreferences.edit().remove("scan_count_$codigoQr").apply()
        Log.d("WalletFragment2", "Cleared scan count for $codigoQr") // Log existente
    }

    /**
     * Simula el proceso de escaneo de un QR cuando el usuario toca la imagen del QR.
     * <p>
     * Muestra un Toast de validación. Si el boleto es de tipo "viaje", incrementa
     * su contador de escaneos. Si alcanza el límite de 2 escaneos, oculta el QR.
     * </p>
     */
    private fun mostrarDialogoEscaneado() {
        // Asegura que el fragmento esté adjunto, el contexto no sea nulo, haya un QR generado y una transacción actual.
        if (isAdded && context != null && qrGenerado && currentTransaction != null) {
            val transaction = currentTransaction!! // Non-null assertion, ya que se comprobó qrGenerado y currentTransaction.

            Toast.makeText(context, "El QR ha sido validado", Toast.LENGTH_SHORT).show()

            // Lógica específica para boletos de tipo "viaje".
            if (transaction.duracion_qr == "viaje") {
                val scanCount = contadorScaner(transaction.codigo_qr)
                Log.d("WalletFragment2", "Processing scan for viaje QR ${transaction.codigo_qr}, current count: $scanCount") // Log existente
                if (scanCount < 2) { // Si aún no ha alcanzado el límite de 2 escaneos.
                    incrementoEscaneos(transaction.codigo_qr)
                    val newScanCount = contadorScaner(transaction.codigo_qr) // Obtener el nuevo conteo.
                    if (newScanCount >= 2) { // Si después de incrementar se alcanza el límite.
                        Log.d("WalletFragment2", "Scan limit reached for viaje QR ${transaction.codigo_qr}") // Log existente
                        ocultarQR(false) // Oculta el QR, 'false' indica que no es por expiración de tiempo.
                        Toast.makeText(context, "No hay boletos vigentes para mostrar un QR.", Toast.LENGTH_LONG).show()
                    }
                } else { // Si ya había alcanzado el límite antes de este "escaneo".
                    Log.w("WalletFragment2", "Scan limit already reached for viaje QR ${transaction.codigo_qr}") // Log existente
                    ocultarQR(false)
                    Toast.makeText(context, "No hay boletos vigentes para mostrar un QR.", Toast.LENGTH_LONG).show()
                }
            } else {
                // Para boletos que no son de "viaje", el "escaneo" solo muestra el Toast.
                Log.d("WalletFragment2", "Scan for non-viaje QR ${transaction.codigo_qr} (duracion_qr: ${transaction.duracion_qr})") // Log existente
            }
        }
    }

    /**
     * Oculta la información del QR (imagen y detalles del boleto) y actualiza la UI.
     * <p>
     * Limpia la imagen QR, resetea los textos de detalles, establece `qrGenerado` a `false`,
     * detiene el `ocultarQrRunnable` pendiente y muestra el mensaje de "sin boleto"
     * junto con el botón de compra.
     * </p>
     * <p>
     * Si el boleto es de tipo "viaje" y se oculta debido a expiración por tiempo (`isExpiration` es `true`),
     * también reinicia su contador de escaneos.
     * </p>
     * @param isExpiration `true` si el QR se oculta debido a expiración por tiempo,
     *                     `false` si es por otra razón (ej. límite de escaneos).
     */
    private fun ocultarQR(isExpiration: Boolean) {
        if (isAdded) { // Asegura que el fragmento esté adjunto.
            currentTransaction?.let { transaction ->
                // Si es un boleto de viaje y la ocultación es por expiración de tiempo,
                // reinicia el contador de escaneos.
                if (transaction.duracion_qr == "viaje") {
                    if (isExpiration) {
                        reinicioEscaneo(transaction.codigo_qr)
                        Log.d("WalletFragment2", "Cleared scanCount for ${transaction.codigo_qr} due to expiration") // Log existente
                    } else {
                        // Si se oculta por límite de escaneos, el contador se mantiene como está (ya en 2 o más).
                        Log.d("WalletFragment2", "Preserving scanCount for ${transaction.codigo_qr} in ocultarQR (scan limit)") // Log existente
                    }
                }
            }
            currentTransaction = null // Limpia la transacción actual.

            // Actualiza la UI para reflejar que no hay QR.
            imagenQR.setImageDrawable(null) // Limpia la imagen QR.
            qrGenerado = false
            handler.removeCallbacks(ocultarQrRunnable) // Detiene el callback de auto-ocultación.
            noBoletoMessage.visibility = View.VISIBLE // Muestra mensaje de "sin boleto".
            btnPagos.visibility = View.VISIBLE       // Muestra botón de compra.
            lblCodigo.text = ""
            lblTipo.text = ""
            lblDuracion.text = ""
            lblFechaCompra.text = ""
            Log.d("WalletFragment2", "QR hidden and transaction cleared (isExpiration: $isExpiration)") // Log existente
        }
    }

    /**
     * Obtiene el ID del usuario logueado desde [SharedPreferences].
     * @return El ID del usuario, o `null` si no se encuentra o [SharedPreferences] no está inicializada.
     */
    private fun getLoggedInUserId(): String? {
        // Verifica si sharedPreferences ha sido inicializada antes de usarla.
        if (!::sharedPreferences.isInitialized) {
            Log.e("WalletFragment2", "SharedPreferences not initialized in getLoggedInUserId") // Log existente
            return null
        }
        return sharedPreferences.getString(Constantes.KEY_USER_ID, null)
    }

    /**
     * Crea y configura una instancia del cliente Supabase.
     * <p>
     * <b>ADVERTENCIA DE SEGURIDAD:</b> Las credenciales de Supabase están hardcodeadas.
     * En un entorno de producción, estas deben almacenarse de forma más segura.
     * </p>
     * @return Una instancia de [SupabaseClient].
     */
    private fun getClient(): SupabaseClient {
        // ¡¡¡MALA PRÁCTICA DE SEGURIDAD!!! NO INCRUSTAR CLAVES DE API EN EL CÓDIGO FUENTE.
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc",
        ) {
            install(Postgrest)
        }
    }

    /**
     * Se llama cuando la vista asociada con el fragmento está siendo destruida.
     * Limpia los callbacks pendientes en el `handler` para evitar fugas de memoria
     * y operaciones en vistas que ya no existen.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        // Remueve todos los callbacks y mensajes del handler para prevenir memory leaks
        // y operaciones en vistas destruidas.
        handler.removeCallbacksAndMessages(null)
        Log.d("WalletFragment2", "onDestroyView called, handlers removed.") // Log existente
    }

    /**
     * Se llama cuando el fragmento se vuelve visible para el usuario.
     * Vuelve a validar la transacción QR por si el estado del boleto ha cambiado
     * mientras el fragmento no estaba visible (ej. expiró o se usó un viaje).
     */
    override fun onResume() {
        super.onResume()
        // Vuelve a validar el QR cuando el fragmento se reanuda,
        // por si el boleto expiró o fue utilizado mientras estaba en segundo plano.
        currentUserId?.let { userId ->
            validacionTransaccionQR(userId)
        }
    }
}