package com.example.conectatec

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

/**
 * Fragmento encargado de mostrar los horarios de servicio, permitiendo al usuario
 * seleccionar entre diferentes días o rangos de días (Lunes a Viernes, Sábado, Domingo).
 * <p>
 * Utiliza [LinearLayout] para agrupar las tablas de horarios de cada categoría
 * y [Button] para que el usuario pueda cambiar la visibilidad de estas tablas.
 * Al iniciar, muestra por defecto los horarios de Lunes a Viernes.
 * </p>
 *
 * @property lunesViernesLayout Contenedor para la tabla de horarios de Lunes a Viernes.
 * @property sabadoLayout Contenedor para la tabla de horarios del Sábado.
 * @property domingoLayout Contenedor para la tabla de horarios del Domingo.
 * @property buttonLunesViernes Botón para mostrar los horarios de Lunes a Viernes.
 * @property buttonSabado Botón para mostrar los horarios del Sábado.
 * @property buttonDomingo Botón para mostrar los horarios del Domingo.
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see R.layout.fragment_horarios
 */
class HorariosFragment : Fragment() {

    private lateinit var lunesViernesLayout: LinearLayout
    private lateinit var sabadoLayout: LinearLayout
    private lateinit var domingoLayout: LinearLayout
    private lateinit var buttonLunesViernes: MaterialButton
    private lateinit var buttonSabado: MaterialButton
    private lateinit var buttonDomingo: MaterialButton

    /**
     * Se llama para que el fragmento instancie su vista de interfaz de usuario.
     * Infla el layout definido en `R.layout.fragment_horarios`.
     *
     * @param inflater El [LayoutInflater] que se puede usar para inflar vistas.
     * @param container El [ViewGroup] padre al que se adjuntará la UI del fragmento.
     * @param savedInstanceState Estado previamente guardado, si existe.
     * @return La [View] raíz para la UI del fragmento.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Infla el layout para este fragmento
        return inflater.inflate(R.layout.fragment_horarios, container, false)
    }
    /**
     * Se llama inmediatamente después de que [onCreateView] ha retornado.
     * <p>
     * Inicializa los componentes de la UI ([LinearLayout] y [Button]),
     * establece el estado inicial mostrando los horarios de Lunes a Viernes por defecto,
     * y configura los `OnClickListener` para los botones que controlan la visibilidad
     * de las diferentes tablas de horarios.
     * </p>
     *
     * @param view La [View] devuelta por [onCreateView].
     * @param savedInstanceState Estado previamente guardado, si existe.
     */

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializa los componentes de la UI
        lunesViernesLayout = view.findViewById(R.id.layout_lunes_viernes)
        sabadoLayout = view.findViewById(R.id.layout_sabado)
        domingoLayout = view.findViewById(R.id.layout_domingo)
        buttonLunesViernes = view.findViewById(R.id.lunes_viernes)
        buttonSabado = view.findViewById(R.id.btn_sabado)
        buttonDomingo = view.findViewById(R.id.btn_domingo)

        // Establece el estado por defecto - Muestra solo las tablas de Lunes a Viernes
        showLunesViernes()

        // Configura los listeners para los botones de selección de día
        buttonLunesViernes.setOnClickListener {
            showLunesViernes()
        }

        buttonSabado.setOnClickListener {
            showSabado()
        }

        buttonDomingo.setOnClickListener {
            showDomingo()
        }
    }

    /**
     * Muestra la tabla de horarios correspondiente a Lunes a Viernes.
     * Oculta las tablas de Sábado y Domingo.
     * Actualiza el estado visual de los botones para reflejar esta selección.
     * @see updateButtonStates
     */
    private fun showLunesViernes() {
        lunesViernesLayout.visibility = View.VISIBLE
        sabadoLayout.visibility = View.GONE
        domingoLayout.visibility = View.GONE

        // Actualiza el estado de los botones para indicar la selección
        updateButtonStates(buttonLunesViernes)
    }

    /**
     * Muestra la tabla de horarios correspondiente al Sábado.
     * Oculta las tablas de Lunes a Viernes y Domingo.
     * Actualiza el estado visual de los botones para reflejar esta selección.
     * @see updateButtonStates
     */
    private fun showSabado() {
        lunesViernesLayout.visibility = View.GONE
        sabadoLayout.visibility = View.VISIBLE
        domingoLayout.visibility = View.GONE

        // Actualiza el estado de los botones para indicar la selección
        updateButtonStates(buttonSabado)
    }

    /**
     * Muestra la tabla de horarios correspondiente al Domingo.
     * Oculta las tablas de Lunes a Viernes y Sábado.
     * Actualiza el estado visual de los botones para reflejar esta selección.
     * @see updateButtonStates
     */
    private fun showDomingo() {
        lunesViernesLayout.visibility = View.GONE
        sabadoLayout.visibility = View.GONE
        domingoLayout.visibility = View.VISIBLE

        // Actualiza el estado de los botones para indicar la selección
        updateButtonStates(buttonDomingo)
    }

    /**
     * Actualiza el estado visual de los botones de selección de día/rango de día.
     * <p>
     * Restablece la apariencia de todos los botones a un estado no seleccionado
     * (fondo por defecto y color de texto negro) y luego resalta el botón
     * proporcionado como `selectedButton` (fondo naranja y color de texto blanco).
     * </p>
     *
     * @param selectedButton El [Button] que debe ser resaltado como seleccionado.
     * @see R.color.orange
     * @see R.color.white
     */
    private fun updateButtonStates(selectedButton: Button) {

        //Color de seleccion de botones
        val colorTextoSeleccionado = resources.getColor(R.color.white, requireContext().theme)
        val colorTextoNoSeleccionado = resources.getColor(R.color.black, requireContext().theme)

        // Restablece el color del texto de todos los botones
        buttonLunesViernes.setTextColor(colorTextoNoSeleccionado)
        buttonSabado.setTextColor(colorTextoNoSeleccionado)
        buttonDomingo.setTextColor(colorTextoNoSeleccionado)


        //  Resalta el botón seleccionado
        selectedButton.setTextColor(colorTextoSeleccionado)

    }
}