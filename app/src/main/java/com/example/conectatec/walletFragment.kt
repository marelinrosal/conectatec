package com.example.conectatec
import android.content.Context
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

class walletFragment : Fragment() {

    //Declaracion de variables
    private lateinit var imagenQR: ImageView
    private lateinit var btnVerHistorial: TextView
    private lateinit var lblCodigo: TextView
    private lateinit var lblTipo: TextView
    private lateinit var lblDuracion: TextView
    private lateinit var lblFechaCompra: TextView
    private lateinit var noBoletoMessage: TextView
    private var qrGenerado: Boolean = false

    //Declaracion de ejecutables.
    private val handler = Handler(Looper.getMainLooper())
    private val ocultarQrRunnable = Runnable {
        Log.d("WalletFragment2", "ocultarQrRunnable executed due to ticket expiration")
        ocultarQR(true) // true indica que se oculta por expiración
    }

    //Inicializacion de supabase
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var supabaseClient: SupabaseClient
    private var currentUserId: String? = null
    private var currentTransaction: InfoTransaccionWallet? = null

    private val formateoFecha = SimpleDateFormat("dd/MM/yyyy")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wallet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imagenQR = view.findViewById(R.id.imagenQR)
        btnVerHistorial = view.findViewById(R.id.btnHistorial)
        lblCodigo = view.findViewById(R.id.lblCodigo)
        lblTipo = view.findViewById(R.id.lblTipo)
        lblDuracion = view.findViewById(R.id.lblDuracion)
        lblFechaCompra = view.findViewById(R.id.lblFechaCompra)
        noBoletoMessage = view.findViewById(R.id.noBoletoMessage)

        sharedPreferences = requireActivity().getSharedPreferences(Constantes.PREFS_NAME, Context.MODE_PRIVATE)
        supabaseClient = getClient()
        currentUserId = getLoggedInUserId()

        //Busca si esta el usuario.
        if (currentUserId == null) {
            Log.e("WalletFragment", "No se encontro el usuario.")
            Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_LONG).show()
            ocultarQR(false)
            return
        }

        btnVerHistorial.setOnClickListener {
            sideSheetTransaccion(currentUserId!!)
        }

        imagenQR.setOnClickListener {
            if (qrGenerado) {
                mostrarDialogoEscaneado()
            }
        }

        validacionTransaccionQR(currentUserId!!)
    }

    //Se encarga de mostrar el historial de transacciones.
    private fun sideSheetTransaccion(userId: String) {
        val sideSheetDialog = SideSheetDialog(requireContext())
        val sideSheetView = layoutInflater.inflate(R.layout.sidesheet_transacciones, null)

        val transactionsTextView: TextView = sideSheetView.findViewById(R.id.sideSheetTransactionsTextView)
        val btnCerrar: Button = sideSheetView.findViewById(R.id.btnCerrarSideSheet)

        btnCerrar.setOnClickListener {
            sideSheetDialog.dismiss()
        }


        //Accede a la base de datos para extraer los datos.
        lifecycleScope.launch {
            try {
                Log.d("WalletFragment", "Transacciones del usarui: $userId")
                val transactions: List<InfoTransaccionWallet> = supabaseClient.postgrest["transaccion"]
                    .select(Columns.list("codigo_qr", "tipo_qr", "duracion_qr", "fechaCompra")) {
                        filter {
                            eq("usuario_id", userId)
                        }
                        order("fechaCompra", Order.DESCENDING)
                    }
                    .decodeList()

                Log.d("WalletFragment", "Encontrado ${transactions.size} transactions.")
                withContext(Dispatchers.Main) {
                    if (transactions.isEmpty()) {
                        transactionsTextView.text = "No tienes boletos comprados recientemente."
                    } else {
                        val displayText = transactions.joinToString(separator = "\n\n") { tx ->
                            "Código: ${tx.codigo_qr}\nTipo: ${tx.tipo_qr}\nDuración: ${tx.duracion_qr}"
                        }
                        transactionsTextView.text = displayText
                    }
                }
            } catch (e: Exception) {
                Log.e("WalletFragment2", "Error fetching transactions: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        transactionsTextView.text = "Error al cargar el historial de boletos: ${e.message}"
                        Toast.makeText(context, "No se pudo cargar el historial: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        sideSheetDialog.setContentView(sideSheetView)
        sideSheetDialog.show()
    }

    //Funcion que checa que este validada la transaccion para desplegar el QR
    private fun validacionTransaccionQR(userId: String) {
        lifecycleScope.launch {
            try {
                Log.d("WalletFragment2", "Transaccion del Usuario: $userId")
                val ultimaTransaccion: InfoTransaccionWallet? = supabaseClient.postgrest["transaccion"]
                    .select(Columns.list("usuario_id", "tipo_qr", "duracion_qr", "codigo_qr", "fechaCompra")) {
                        filter {
                            eq("usuario_id", userId)
                        }
                        order("fechaCompra", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeSingleOrNull()

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        if (ultimaTransaccion != null && !TicketUtils.transaccionExpirada(ultimaTransaccion)) {
                            val isValidForQr = if (ultimaTransaccion.duracion_qr == "viaje") {
                                val scanCount = contadorScaner(ultimaTransaccion.codigo_qr)

                                scanCount < 2
                            } else {
                                true
                            }

                            if (isValidForQr) {
                                currentTransaction = ultimaTransaccion

                                lblCodigo.text = "Código: ${ultimaTransaccion.codigo_qr}"
                                lblTipo.text = "Tipo: ${ultimaTransaccion.tipo_qr}"
                                lblDuracion.text = "Duración: ${ultimaTransaccion.duracion_qr}"
                                lblFechaCompra.text = ultimaTransaccion.fecha_compra?.let {
                                    val instant = Instant.parse(it)
                                    "Fecha De Compra: ${formateoFecha.format(Date(instant.toEpochMilli()))}"
                                } ?: "Fecha De Compra: N/A"

                                noBoletoMessage.visibility = View.GONE

                                val qrData = "userId=${ultimaTransaccion.usuario_id ?: userId}," +
                                        "tipo=${ultimaTransaccion.tipo_qr}," +
                                        "duracion=${ultimaTransaccion.duracion_qr}," +
                                        "codigo=${ultimaTransaccion.codigo_qr}"
                                generarQR(qrData)
                                qrGenerado = true

                                val timeRemaining = calculaTiempoSobrante(ultimaTransaccion)
                                if (timeRemaining > 0) {
                                    handler.removeCallbacks(ocultarQrRunnable)
                                    handler.postDelayed(ocultarQrRunnable, timeRemaining)
                                } else {
                                    ocultarQR(true)
                                }
                            } else {
                                 Toast.makeText(context, "No hay boletos vigentes para mostrar un QR.", Toast.LENGTH_LONG).show()
                                ocultarQR(false)
                            }
                        } else {
                           Toast.makeText(context, "No hay boletos vigentes para mostrar un QR.", Toast.LENGTH_LONG).show()
                            ocultarQR(false)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(context, "Error al verificar boletos vigentes: ${e.message}", Toast.LENGTH_LONG).show()
                        ocultarQR(false)
                    }
                }
            }
        }
    }

    //Funcion que se encarga de calcular el tiempo sobrante
    private fun calculaTiempoSobrante(transaction: InfoTransaccionWallet): Long {
        val fechaCompra = transaction.fecha_compra ?: return 0L
        return try {
            val createdAt = Instant.parse(fechaCompra)
            val durationMs = TicketUtils.duracionBoleto[transaction.duracion_qr] ?: return 0L
            val expirationTime = createdAt.toEpochMilli() + durationMs
            val currentTime = System.currentTimeMillis()
            if (expirationTime > currentTime) expirationTime - currentTime else 0L
        } catch (e: Exception) {
            Log.e("WalletFragment2", "Error parsing fecha_compra: ${e.message}", e)
            0L
        }
    }

    //Genera el qr
    private fun generarQR(data: String) {
        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.encodeBitmap(data, BarcodeFormat.QR_CODE, 500, 500)
            imagenQR.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("WalletFragment2", "Error  QR ", e)
            Toast.makeText(context, "Error al generar imagen QR", Toast.LENGTH_SHORT).show()
        }
    }

    //Cuenta los escaneos del qr
    private fun contadorScaner(codigoQr: String): Int {
        val count = sharedPreferences.getInt("scan_count_$codigoQr", 0)
        Log.d("WalletFragment", "Retrieved scan count for $codigoQr: $count")
        return count
    }

    //Incrementa el contador.
    private fun incrementoEscaneos(codigoQr: String) {
        val currentCount = contadorScaner(codigoQr)
        val newCount = currentCount + 1
        sharedPreferences.edit().putInt("scan_count_$codigoQr", newCount).apply()
        Log.d("WalletFragment2", "Incremented scan count for $codigoQr to $newCount")
    }

    //Reinicia la cuenta de escaneos
    private fun reinicioEscaneo(codigoQr: String) {
        sharedPreferences.edit().remove("scan_count_$codigoQr").apply()
        Log.d("WalletFragment2", "Cleared scan count for $codigoQr")
    }

    //Simula el escaneo del QR
    private fun mostrarDialogoEscaneado() {
        if (isAdded && context != null && qrGenerado && currentTransaction != null) {
            val transaction = currentTransaction!!

            Toast.makeText(context, "El QR ha sido validado", Toast.LENGTH_SHORT).show()

            if (transaction.duracion_qr == "viaje") {
                val scanCount = contadorScaner(transaction.codigo_qr)
                Log.d("WalletFragment2", "Processing scan for viaje QR ${transaction.codigo_qr}, current count: $scanCount")
                if (scanCount < 2) {
                    incrementoEscaneos(transaction.codigo_qr)
                    val newScanCount = contadorScaner(transaction.codigo_qr)
                    if (newScanCount >= 2) {
                        Log.d("WalletFragment2", "Scan limit reached for viaje QR ${transaction.codigo_qr}")
                        ocultarQR(false) // false indica que se oculta por límite de escaneos
                        Toast.makeText(context, "No hay boletos vigentes para mostrar un QR.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.w("WalletFragment2", "Scan limit already reached for viaje QR ${transaction.codigo_qr}")
                    ocultarQR(false)
                    Toast.makeText(context, "No hay boletos vigentes para mostrar un QR.", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.d("WalletFragment2", "Scan for non-viaje QR ${transaction.codigo_qr} (duracion_qr: ${transaction.duracion_qr})")
            }
        }
    }

    //Oculta el QR, para cuando ya no se utilice
    private fun ocultarQR(isExpiration: Boolean) {
        if (isAdded) {
            currentTransaction?.let {
                if (it.duracion_qr == "viaje") {
                    if (isExpiration) {
                        // Limpiar scanCount solo si el boleto expira por tiempo
                        reinicioEscaneo(it.codigo_qr)
                        Log.d("WalletFragment2", "Cleared scanCount for ${it.codigo_qr} due to expiration")
                    } else {
                        // Preservar scanCount si se oculta por límite de escaneos
                        Log.d("WalletFragment2", "Preserving scanCount for ${it.codigo_qr} in ocultarQR (scan limit)")
                    }
                }
            }
            currentTransaction = null

            imagenQR.setImageDrawable(null)
            qrGenerado = false
            handler.removeCallbacks(ocultarQrRunnable)
            noBoletoMessage.visibility = View.VISIBLE
            lblCodigo.text = ""
            lblTipo.text = ""
            lblDuracion.text = ""
            lblFechaCompra.text = ""
            Log.d("WalletFragment2", "QR hidden and transaction cleared (isExpiration: $isExpiration)")
        }
    }

    //Loggeo del user supabase
    private fun getLoggedInUserId(): String? {
        if (!::sharedPreferences.isInitialized) {
            Log.e("WalletFragment2", "SharedPreferences not initialized in getLoggedInUserId")
            return null
        }
        return sharedPreferences.getString(Constantes.KEY_USER_ID, null)
    }

    //Ingreso a Supabase
    private fun getClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc",
        ) {
            install(Postgrest)
        }
    }

    //Destruye los handlers para cuando no se esten utilizando
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        Log.d("WalletFragment2", "onDestroyView called, handlers removed.")
    }

    //Genera la lista del historial
    override fun onResume() {
        super.onResume()
        currentUserId?.let { validacionTransaccionQR(it) }
    }
}