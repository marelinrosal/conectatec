package com.example.conectatec

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * Fragmento encargado de mostrar la interfaz de usuario para el historial.
 * <p>
 * Actualmente, este fragmento solo infla el layout correspondiente al historial,
 * pero podría extenderse para cargar y mostrar datos de historial específicos.
 * </p>
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 */
class HistorialFragment : Fragment() {

    /**
     * Se llama para que el fragmento instancie su vista de interfaz de usuario.
     * <p>
     * Este método infla el layout definido en `R.layout.fragment_historial`
     * y lo devuelve como la vista raíz del fragmento.
     * </p>
     *
     * @param inflater El [LayoutInflater] objeto que se puede usar para inflar
     * cualquier vista en el fragmento.
     * @param container Si no es nulo, esta es la vista padre a la que se adjuntará
     * la interfaz de usuario del fragmento después de ser inflada. El fragmento no debe
     * agregar la vista por sí mismo, pero esto se puede usar para generar los
     * LayoutParams de la vista.
     * @param savedInstanceState Si no es nulo, este fragmento se está reconstruyendo
     * a partir de un estado guardado previamente como se indica aquí.
     * @return Devuelve la [View] para la interfaz de usuario del fragmento, o `null`.
     *         En este caso, la vista inflada desde `R.layout.fragment_historial`.
     * @see R.layout.fragment_historial
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Infla el layout para este fragmento
        return inflater.inflate(R.layout.fragment_historial, container, false)
    }
}