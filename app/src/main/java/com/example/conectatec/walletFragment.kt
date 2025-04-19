package com.example.conectatec

import android.content.Context // Añadido
import android.content.SharedPreferences // Añadido
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log // Añadido
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView // Añadido
import android.widget.Toast // Añadido
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope // Añadido
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import io.github.jan.supabase.SupabaseClient // Añadido
import io.github.jan.supabase.createSupabaseClient // Añadido
import io.github.jan.supabase.postgrest.Postgrest // Añadido
import io.github.jan.supabase.postgrest.postgrest // Añadido
import io.github.jan.supabase.postgrest.query.Columns // Añadido
import io.github.jan.supabase.postgrest.query.Order // Añadido
import kotlinx.coroutines.Dispatchers // Añadido
import kotlinx.coroutines.launch // Añadido
import kotlinx.coroutines.withContext // Añadido
import kotlinx.serialization.Serializable // Añadido

// --- NUEVO: Data class para representar la información necesaria de la transacción ---
@Serializable
data class WalletTransactionInfo(
    val codigo_qr: String,
    val usuario_id: String? = null, // Nullable si no siempre se selecciona
    val tipo_qr: String,
    val duracion_qr: String
    // Añade aquí 'created_at' o similar si tienes una columna de timestamp para ordenar
    // val created_at: String? = null // Ejemplo si tuvieras timestamp
)

class walletFragment : Fragment() {

    private lateinit var imagenQR: ImageView
    private lateinit var botonGeneradorQR: Button
    private lateinit var transactionsTextView: TextView // NUEVO: Para mostrar la lista

    private var qrGenerado: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val dialogoEscaneado = Runnable { mostrarDialogoEscaneado() }

    // --- NUEVO: Variables para Supabase y SharedPreferences ---
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var supabaseClient: SupabaseClient
    private var currentUserId: String? = null // Para guardar el userId

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflar el layout que AHORA debe contener el TextView transactions_list_textview
        return inflater.inflate(R.layout.fragment_wallet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imagenQR = view.findViewById(R.id.imagenQR)
        botonGeneradorQR = view.findViewById(R.id.generadorQR)
        transactionsTextView = view.findViewById(R.id.transactions_list_textview) // Asegúrate de que este ID existe en tu XML

        // --- NUEVO: Inicialización ---
        sharedPreferences = requireActivity().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        supabaseClient = getClient()
        currentUserId = getLoggedInUserId()

        if (currentUserId == null) {
            Log.e("WalletFragment", "User ID not found. Cannot fetch data or generate QR.")
            Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_LONG).show()
            botonGeneradorQR.isEnabled = false // Deshabilitar botón si no hay usuario
            transactionsTextView.text = "No se pudo obtener la información del usuario."
            // Considera redirigir al login o mostrar un mensaje más prominente
            return // Salir si no hay usuario
        }

        // --- NUEVO: Cargar historial de transacciones ---
        fetchAndDisplayTransactions(currentUserId!!)

        // --- MODIFICADO: Listener del botón ---
        botonGeneradorQR.setOnClickListener {
            if (currentUserId != null) {
                generateQrFromLatestTransaction(currentUserId!!)
            } else {
                Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_SHORT).show()
            }
        }

        // Quitar la notificación inicial hardcodeada si la carga de transacciones la reemplaza
        // mostrarNotificacion("No se ha comprado algún boleto") // Comentado/Eliminado
    }

    // --- NUEVO: Función para obtener y mostrar transacciones ---
    private fun fetchAndDisplayTransactions(userId: String) {
        lifecycleScope.launch {
            try {
                Log.d("WalletFragment", "Fetching transactions for user: $userId")
                val transactions: List<WalletTransactionInfo> = supabaseClient.postgrest["transaccion"]
                    .select(Columns.list("codigo_qr", "tipo_qr", "duracion_qr")) { // Solo columnas necesarias para mostrar
                        filter {
                            eq("usuario_id", userId)
                        }
                        // Opcional: Ordenar por fecha si existe, o por codigo_qr si contiene timestamp
                        order("codigo_qr", Order.DESCENDING) // Asumiendo que codigo_qr ordena por fecha
                    }
                    .decodeList()

                Log.d("WalletFragment", "Found ${transactions.size} transactions.")
                // Actualizar UI en el hilo principal
                withContext(Dispatchers.Main) {
                    if (transactions.isEmpty()) {
                        transactionsTextView.text = "No tienes boletos comprados recientemente."
                        // Opcional: mostrar la notificación inicial aquí
                        // mostrarNotificacion("No se ha comprado algún boleto")
                    } else {
                        // Formatear la lista para mostrarla en el TextView
                        val displayText = transactions.joinToString(separator = "\n\n") { tx ->
                            "Código: ${tx.codigo_qr}\nTipo: ${tx.tipo_qr}\nDuración: ${tx.duracion_qr}"
                        }
                        transactionsTextView.text = displayText
                    }
                }
            } catch (e: Exception) {
                Log.e("WalletFragment", "Error fetching transactions: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) { // Verificar si el fragmento está adjunto
                        transactionsTextView.text = "Error al cargar el historial de boletos."
                        Toast.makeText(context, "No se pudo cargar el historial.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // --- NUEVO: Función para obtener la última transacción y generar QR ---
    private fun generateQrFromLatestTransaction(userId: String) {
        lifecycleScope.launch {
            try {
                Log.d("WalletFragment", "Fetching latest transaction for user: $userId")
                // Seleccionar las columnas necesarias para el QR y ordenar para obtener la última
                val latestTransaction: WalletTransactionInfo? = supabaseClient.postgrest["transaccion"]
                    .select(Columns.list("usuario_id", "tipo_qr", "duracion_qr", "codigo_qr")) { // Incluye codigo_qr para ordenar si es necesario
                        filter {
                            eq("usuario_id", userId)
                        }
                        // Ordenar para obtener la más reciente. ¡Ajusta 'codigo_qr' si tienes una columna de fecha!
                        order("codigo_qr", Order.DESCENDING)
                        limit(1) // Solo queremos la más reciente
                    }
                    .decodeSingleOrNull() // Obtener solo un resultado o null

                withContext(Dispatchers.Main) {
                    if (isAdded) { // Verificar si el fragmento está adjunto
                        if (latestTransaction != null) {
                            // --- CONSTRUIR CADENA PARA EL QR ---
                            // Puedes usar JSON o un formato simple. Ejemplo: Clave=Valor,Clave=Valor
                            val qrData = "userId=${latestTransaction.usuario_id ?: userId}," +
                                    "tipo=${latestTransaction.tipo_qr}," +
                                    "duracion=${latestTransaction.duracion_qr}"

                            Log.d("WalletFragment", "Generating QR with data: $qrData")
                            generarQR(qrData) // Generar el QR con los datos de la transacción
                            qrGenerado = true
                            handler.removeCallbacks(dialogoEscaneado) // Remover callbacks previos si existen
                            handler.postDelayed(dialogoEscaneado, 8000) // Iniciar temporizador para diálogo
                        } else {
                            Log.w("WalletFragment", "No transaction found for user $userId to generate QR.")
                            Toast.makeText(context, "No se encontró un boleto reciente para generar el QR.", Toast.LENGTH_LONG).show()
                            ocultarQR() // Asegurarse que no quede un QR viejo
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WalletFragment", "Error fetching latest transaction or generating QR: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(context, "Error al obtener datos para el QR.", Toast.LENGTH_SHORT).show()
                        ocultarQR()
                    }
                }
            }
        }
    }


    // --- SIN CAMBIOS ---
    private fun generarQR(data: String) {
        try {
            val barcodeEncoder = BarcodeEncoder()
            // Aumentar un poco la resolución si es necesario
            val bitmap: Bitmap = barcodeEncoder.encodeBitmap(data, BarcodeFormat.QR_CODE, 500, 500)
            imagenQR.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("WalletFragment", "Error encoding QR code", e)
            Toast.makeText(context, "Error al generar imagen QR", Toast.LENGTH_SHORT).show()
        }
    }

    // --- SIN CAMBIOS ---
    private fun mostrarDialogoEscaneado() {
        if (isAdded && context != null && qrGenerado) { // Solo mostrar si se generó un QR
            // Cancelar cualquier diálogo pendiente si se presiona el botón de nuevo
            handler.removeCallbacks(dialogoEscaneado)

            AlertDialog.Builder(requireContext())
                .setTitle("Simulación de Escaneo") // Título más claro
                .setMessage("El QR ha sido validado (simulado).")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    ocultarQR() // Ocultar QR después de aceptar
                }
                .setCancelable(false) // Evitar que se cierre tocando fuera
                .show()
        }
    }

    // --- SIN CAMBIOS ---
    private fun ocultarQR() {
        if (isAdded) {
            imagenQR.setImageDrawable(null) // Limpiar la imagen
            qrGenerado = false
            handler.removeCallbacks(dialogoEscaneado) // Detener temporizador si se oculta antes
        }
    }

    // --- SIN CAMBIOS (o eliminar si ya no se usa) ---
    private fun mostrarNotificacion(mensaje: String) {
        if (isAdded && context != null) {
            AlertDialog.Builder(requireContext())
                .setTitle("Información")
                .setMessage(mensaje)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    // --- NUEVO: Helper para obtener User ID ---
    private fun getLoggedInUserId(): String? {
        // Asegurarse que sharedPreferences está inicializado
        if (!::sharedPreferences.isInitialized) {
            Log.e("WalletFragment", "SharedPreferences not initialized in getLoggedInUserId")
            return null
        }
        return sharedPreferences.getString(Constants.KEY_USER_ID, null)
    }

    // --- NUEVO: Helper para obtener Cliente Supabase ---
    private fun getClient(): SupabaseClient {
        // ¡¡¡MALA PRÁCTICA!!! NO INCRUSTAR CLAVES EN EL CÓDIGO.
        return createSupabaseClient(
            supabaseUrl = "https://pxtwcujdospzspdcmlzx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB4dHdjdWpkb3NwenNwZGNtbHp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDI2MTUyMTIsImV4cCI6MjA1ODE5MTIxMn0.ADmBU41kcmoi1JaCettGUGeyUAlK_fvyx9Dj8xF7INc",
        ) {
            install(Postgrest)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Limpiar el handler para evitar fugas de memoria
        handler.removeCallbacksAndMessages(null)
        Log.d("WalletFragment", "onDestroyView called, handlers removed.")
    }
}