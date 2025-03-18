package com.example.conectatec

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

class QrFragment : Fragment() {

    private lateinit var imagenQR: ImageView
    private lateinit var botonGeneradorQR: Button
    private var qrGenerado: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val dialogoEscaneado = Runnable { mostrarDialogoEscaneado() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_qr, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imagenQR = view.findViewById(R.id.imagenQR)
        botonGeneradorQR = view.findViewById(R.id.generadorQR)


        mostrarNotificacion("No se ha comprado algún boleto")

        botonGeneradorQR.setOnClickListener {
            generarQR("Boleto Tren #12345")
            qrGenerado = true
            handler.postDelayed(dialogoEscaneado, 8000)

        }


    }

    private fun generarQR(data: String) {
        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.encodeBitmap(data, BarcodeFormat.QR_CODE, 400, 400)
            imagenQR.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun mostrarDialogoEscaneado() {
        requireActivity().runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setTitle("Código Escaneado")
                .setMessage("Has escaneado el QR con tu celular.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    ocultarQR()
                }
                .show()
        }
    }

    private fun ocultarQR() {
        requireActivity().runOnUiThread {
            imagenQR.setImageDrawable(null) // Borra el QR
            qrGenerado = false // Se marca como no generado


        }
    }


    private fun mostrarNotificacion(mensaje: String) {
        requireActivity().runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setTitle("Información")
                .setMessage(mensaje)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
}
