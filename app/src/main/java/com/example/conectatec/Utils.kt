package com.example.conectatec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import android.util.Log // Importado para logging en TicketUtils si es necesario

/**
 * Clase de datos que representa la información de una transacción de ticket,
 * especialmente diseñada para ser leída desde la base de datos (Supabase) y
 * utilizada en la sección "Wallet" o para verificar la validez de un ticket.
 * <p>
 * Es serializable para facilitar la decodificación desde respuestas JSON de Supabase.
 * Los campos `usuario_id` y `fecha_compra` son anulables para manejar casos donde
 * esta información podría no estar presente o ser opcional en ciertos contextos de lectura.
 * </p>
 *
 * @property codigo_qr El identificador único (UUID) del ticket/QR.
 * @property usuario_id El ID del usuario asociado a esta transacción. Puede ser nulo.
 * @property tipo_qr El tipo de servicio del ticket (ej: "urbano", "interurbano").
 * @property duracion_qr La validez o duración del ticket (ej: "dia", "semana", "mes", "viaje").
 * @property fecha_compra La fecha y hora (en formato ISO String) en que se realizó la compra.
 *                        Mapea a la columna 'fechaCompra' en la base de datos. Puede ser nulo.
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 */
@Serializable
data class InfoTransaccionWallet(
    val codigo_qr: String,
    val usuario_id: String? = null, // ID del usuario, puede ser nulo si no se necesita en todos los contextos de lectura
    val tipo_qr: String,
    val duracion_qr: String,
    @SerialName("fechaCompra") // Mapea el nombre del campo en Kotlin a la columna 'fechaCompra' en Supabase.
    val fecha_compra: String? // Fecha de compra, puede ser nula si no está disponible.
)

/**
 * Objeto singleton que proporciona utilidades relacionadas con la lógica de los tickets,
 * como la duración de su validez y la verificación de su expiración.
 *
 * @property duracionBoleto Un mapa que define la duración en milisegundos para cada tipo de
 *                          duración de ticket (ej: "viaje", "dia", "semana", "mes").
 *                          Actualmente configurado con tiempos cortos para pruebas.
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see InfoTransaccionWallet
 */
object TicketUtils {
    // TAG para logging, opcional si no se añaden logs aquí.
    // private const val TAG = "TicketUtils"

    /**
     * Define la duración de validez en milisegundos para diferentes tipos de tickets.
     * Las claves son los identificadores de duración (ej: "viaje", "dia") y los valores
     * son la duración en milisegundos.
     * <p>
     * <b>Nota:</b> Los valores actuales están configurados para pruebas con duraciones cortas.
     * En un entorno de producción, estos valores deberían reflejar las duraciones reales
     * (ej: 1 día = 24 * 60 * 60 * 1000 ms).
     * </p>
     * <ul>
     *   <li>"viaje": 120,000 ms (2 minuto para pruebas, usualmente sería más, ej. 2 horas = 7,200,000 ms)</li>
     *   <li>"dia": 150,000 ms (2.5 minutos para pruebas, usualmente sería 1 día)</li>
     *   <li>"semana": 180,000 ms (3 minutos para pruebas, usualmente sería 7 días)</li>
     *   <li>"mes": 210,000 ms (3.5 minutos para pruebas, usualmente sería ~30 días)</li>
     * </ul>
     */
    val duracionBoleto = mapOf(
        "viaje" to 120_000L,  // 2 minuto (para pruebas) -> En producción: ej. 2 horas = 2 * 60 * 60 * 1000 = 7_200_000L
        "dia" to 150_000L,   // 2.5 minutos (para pruebas) -> En producción: 1 día = 24 * 60 * 60 * 1000 = 86_400_000L
        "semana" to 180_000L, // 3 minutos (para pruebas) -> En producción: 7 días = 7 * 86_400_000L
        "mes" to 210_000L     // 3.5 minutos (para pruebas) -> En producción: ej. 30 días = 30 * 86_400_000L
    )

    /**
     * Verifica si una transacción de ticket ha expirado basándose en su fecha de compra
     * y el tipo de duración del ticket.
     * <p>
     * Si la `fecha_compra` es nula o no se puede parsear, o si `duracion_qr` no
     * se encuentra en [duracionBoleto], la función considerará el ticket como expirado
     * por seguridad.
     * </p>
     *
     * @param transaction La [InfoTransaccionWallet] cuya expiración se va a verificar.
     * @return `true` si el ticket ha expirado o si hay un error al procesar los datos,
     *         `false` si el ticket aún es válido.
     */
    fun transaccionExpirada(transaction: InfoTransaccionWallet): Boolean {
        // Si la fecha de compra es nula, se considera expirado por defecto.
        val fechaCompraStr = transaction.fecha_compra ?: run {
            // Log.w(TAG, "transaccionExpirada: fecha_compra es null para QR ${transaction.codigo_qr}. Considerado expirado.")
            return true
        }

        return try {
            val instanteCompra = Instant.parse(fechaCompraStr) // Parsea la fecha de compra a un objeto Instant.
            // Obtiene la duración en milisegundos para el tipo de duración del ticket.
            // Si el tipo de duración no está en el mapa, se considera expirado.
            val duracionMs = duracionBoleto[transaction.duracion_qr] ?: run {
                // Log.w(TAG, "transaccionExpirada: duracion_qr '${transaction.duracion_qr}' no encontrada en duracionBoleto para QR ${transaction.codigo_qr}. Considerado expirado.")
                return true
            }

            val tiempoExpiracionEpochMs = instanteCompra.toEpochMilli() + duracionMs // Calcula el momento exacto de expiración en milisegundos desde la época.
            val tiempoActualEpochMs = System.currentTimeMillis() // Obtiene el tiempo actual en milisegundos desde la época.

            // Compara si el tiempo actual ha superado el tiempo de expiración.
            val expirado = tiempoActualEpochMs > tiempoExpiracionEpochMs
            // if (expirado) {
            //     Log.d(TAG, "transaccionExpirada: QR ${transaction.codigo_qr} HA EXPIRADO. Actual: $tiempoActualEpochMs, Expiración: $tiempoExpiracionEpochMs")
            // } else {
            //     Log.d(TAG, "transaccionExpirada: QR ${transaction.codigo_qr} AÚN ES VÁLIDO. Actual: $tiempoActualEpochMs, Expiración: $tiempoExpiracionEpochMs")
            // }
            return expirado
        } catch (e: Exception) {
            // Si ocurre cualquier error al parsear la fecha o durante los cálculos (ej. formato de fecha incorrecto),
            // se considera el ticket como expirado por seguridad.
            // Log.e(TAG, "transaccionExpirada: Error al procesar la expiración para QR ${transaction.codigo_qr}. Fecha: $fechaCompraStr, Duración: ${transaction.duracion_qr}", e)
            true
        }
    }
}