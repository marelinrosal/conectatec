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

/**
 * Fragmento que muestra un mapa de Google Maps con marcadores para diferentes estaciones de ruta.
 * <p>
 * Implementa [OnMapReadyCallback] para manejar la inicialización del mapa.
 * Permite al usuario seleccionar diferentes estaciones a través de un [RadioGroup],
 * y el mapa se actualiza para centrarse en la estación seleccionada, mostrando un marcador.
 * Por defecto, se muestra la estación "Zinancantepec".
 * </p>
 *
 * @property mMap Instancia de [GoogleMap]. Es opcional (`?`) ya que se inicializa
 *                de forma asíncrona en [onMapReady].
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see R.layout.activity_mapa
 * @see SupportMapFragment
 * @see GoogleMap
 */
class RutasFragment : Fragment(), OnMapReadyCallback {
    private var mMap: GoogleMap? = null // Instancia del mapa, opcional hasta que onMapReady es llamado.

    /**
     * Se llama para que el fragmento instancie su vista de interfaz de usuario.
     * Infla el layout `R.layout.activity_mapa` que debe contener el [SupportMapFragment].
     *
     * @param inflater El [LayoutInflater] para inflar vistas.
     * @param container El [ViewGroup] padre al que se adjuntará la UI del fragmento.
     * @param savedInstanceState Estado previamente guardado, si existe.
     * @return La [View] raíz para la UI del fragmento.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Infla el layout que contiene el contenedor para el SupportMapFragment.
        // El nombre del layout "activity_mapa" podría ser confuso si es un fragmento,
        // considerar renombrarlo a "fragment_rutas" o similar si es posible.
        return inflater.inflate(R.layout.activity_mapa, container, false)
    }

    /**
     * Se llama inmediatamente después de que [onCreateView] ha retornado.
     * <p>
     * Inicializa el [SupportMapFragment] (obteniéndolo del layout o creándolo si no existe)
     * y solicita el mapa de forma asíncrona ([getMapAsync]).
     * Configura el [RadioGroup] y sus [RadioButton] para permitir al usuario cambiar
     * la ubicación mostrada en el mapa. Establece "Zinancantepec" como la estación
     * seleccionada por defecto.
     * </p>
     *
     * @param view La [View] devuelta por [onCreateView].
     * @param savedInstanceState Estado previamente guardado, si existe.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Obtiene o crea el SupportMapFragment.
        // Es importante usar childFragmentManager para fragmentos anidados.
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapa) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also { // Si no existe, crea uno nuevo.
                childFragmentManager.beginTransaction()
                    .replace(R.id.mapa, it) // Reemplaza el contenedor R.id.mapa con el fragmento del mapa.
                    .commit()
            }
        // Registra este fragmento para recibir el callback cuando el mapa esté listo.
        mapFragment.getMapAsync(this)

        // Inicialización del RadioGroup y sus botones.
        val grupo: RadioGroup = view.findViewById(R.id.Grupo) // Asume que R.id.Grupo existe.
        val zinancantepecRadio: RadioButton = view.findViewById(R.id.zinancantepec) // Asume que R.id.zinancantepec existe.

        // Selecciona "Zinancantepec" por defecto al iniciar el fragmento.
        zinancantepecRadio.isChecked = true

        // Listener para cambios en la selección del RadioGroup.
        grupo.setOnCheckedChangeListener { _, checkedId ->
            // Cambia la ubicación en el mapa según el RadioButton seleccionado.
            when (checkedId) {
                R.id.zinancantepec -> cambiarUbicacion(LatLng(19.2802083, -99.694791666667), "Estación: Zinancantepec")
                R.id.toluca -> cambiarUbicacion(LatLng(19.270283333333, -99.641791666667), "Estación: Toluca Centro")
                R.id.metepec -> cambiarUbicacion(LatLng(19.2775611, -99.573475), "Estación: Metepec")
                R.id.lerma -> cambiarUbicacion(LatLng(19.2784889, -99.514975), "Estación: Lerma")
                R.id.santaFe -> cambiarUbicacion(LatLng(19.364475, -99.2682), "Estación: Santa Fe")
            }
        }
    }

    /**
     * Callback que se invoca cuando el mapa está listo para ser usado.
     * <p>
     * Almacena la instancia de [GoogleMap] en [mMap] y establece la ubicación
     * inicial del mapa en "Zinancantepec".
     * </p>
     * @param googleMap Una instancia no nula de [GoogleMap].
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        // Establece la ubicación inicial del mapa.
        // Esto se llama después de que el RadioButton de Zinancantepec ya está marcado,
        // por lo que podría ser redundante si el listener del RadioGroup ya lo hizo,
        // pero asegura que el mapa se inicialice correctamente si onMapReady se llama antes que el listener.
        cambiarUbicacion(LatLng(19.2802083, -99.694791666667), "Estación: Zinancantepec")
    }

    /**
     * Cambia la vista del mapa a una nueva ubicación y título.
     * <p>
     * Limpia cualquier marcador previo del mapa, añade un nuevo marcador en la `ubicacion`
     * especificada con el `titulo` dado, y anima la cámara para centrarse en esta
     * ubicación con un zoom predefinido. Se aplica un pequeño desplazamiento vertical
     * a la cámara para que el marcador no quede exactamente en el centro, mejorando
     * la visualización del título del marcador.
     * </p>
     * <p>
     * Solo actúa si [mMap] (la instancia de GoogleMap) no es nula.
     * </p>
     * @param ubicacion Las coordenadas [LatLng] de la nueva ubicación a mostrar.
     * @param titulo El título para el marcador en la nueva ubicación.
     */
    private fun cambiarUbicacion(ubicacion: LatLng, titulo: String) {
        mMap?.let { map -> // Ejecuta solo si mMap no es nulo.
            map.clear() // Limpia marcadores anteriores.

            // Calcula un pequeño desplazamiento hacia el sur para la cámara,
            // de modo que el marcador no quede exactamente en el centro y su InfoWindow sea más visible.
            val desplazamientoVertical = 0.005 // Ajustar este valor según sea necesario.
            val camaraUbicacion = LatLng(ubicacion.latitude - desplazamientoVertical, ubicacion.longitude)

            // Añade un nuevo marcador en la ubicación deseada.
            map.addMarker(MarkerOptions().position(ubicacion).title(titulo))
            // Anima la cámara a la nueva ubicación con un nivel de zoom de 15f.
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(camaraUbicacion, 15f))
        }
    }
}