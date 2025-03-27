package com.example.conectatec

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class RutasFragment : Fragment(), OnMapReadyCallback {
    private var mMap: GoogleMap? = null // Cambiar a variable opcional para evitar errores

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_mapa, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapa) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.mapa, it)
                    .commit()
            }

        mapFragment.getMapAsync(this)

        val grupo: RadioGroup = view.findViewById(R.id.Grupo)
        val zinancantepecRadio: RadioButton = view.findViewById(R.id.zinancantepec)

        // Seleccionar Zinancantepec por defecto
        zinancantepecRadio.isChecked = true

        grupo.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.zinancantepec -> cambiarUbicacion(LatLng(19.2802083, -99.694791666667), "Estación: Zinancantepec")
                R.id.toluca -> cambiarUbicacion(LatLng(19.270283333333, -99.641791666667), "Estación: Toluca Centro")
                R.id.metepec -> cambiarUbicacion(LatLng(19.2775611, -99.573475), "Estación: Metepec")
                R.id.lerma -> cambiarUbicacion(LatLng(19.2784889, -99.514975), "Estación: Lerma")
                R.id.santaFe -> cambiarUbicacion(LatLng(19.364475, -99.2682), "Estación: Santa Fe")
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        cambiarUbicacion(LatLng(19.2802083, -99.694791666667), "Estación: Zinancantepec") // Ubicación inicial
    }

    private fun cambiarUbicacion(ubicacion: LatLng, titulo: String) {
        mMap?.let { map -> // Verifica que el mapa está inicializado
            map.clear()

            val desplazamiento = 0.005
            val nuevaUbicacion = LatLng(ubicacion.latitude - desplazamiento, ubicacion.longitude)

            map.addMarker(MarkerOptions().position(ubicacion).title(titulo))
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(nuevaUbicacion, 15f))
        }
    }
}
