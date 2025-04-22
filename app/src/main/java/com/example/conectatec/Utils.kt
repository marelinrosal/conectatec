package com.example.conectatec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant


//Clase encargada del boleto.

@Serializable
//Informacion del ticket
data class InfoTransaccionWallet(
    val codigo_qr: String,
    val usuario_id: String? = null,
    val tipo_qr: String,
    val duracion_qr: String,
    @SerialName("fechaCompra")
    val fecha_compra: String?
)


object TicketUtils {
    //Variable con el tiempo de expiracion
    val duracionBoleto = mapOf(
        "viaje" to 60_000L,  // 5 min (para pruebas)
        "dia" to 90_000L,   // 7 min (para pruebas)
        "semana" to 120_000L, // 9 min (para pruebas)
        "mes" to 150_000L     // 11 min (para pruebas)
    )

    //Funcion encargada de verificar si expiro la transaccion.
    fun transaccionExpirada(transaction: InfoTransaccionWallet): Boolean {
        val fechaCompra = transaction.fecha_compra ?: return true
        return try {
            val creacion = Instant.parse(fechaCompra)
            val duracionMs = duracionBoleto[transaction.duracion_qr] ?: return true
            val tiempoExpiracion = creacion.toEpochMilli() + duracionMs
            val tiempoActual = System.currentTimeMillis()
            tiempoActual > tiempoExpiracion
        } catch (e: Exception) {
            true
        }
    }
}