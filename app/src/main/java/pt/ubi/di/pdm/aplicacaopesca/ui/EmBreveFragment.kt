package pt.ubi.di.pdm.aplicacaopesca.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import pt.ubi.di.pdm.aplicacaopesca.R

/** Ecrã provisório para separadores ainda em desenvolvimento (Peixedex, Capturas, Estatísticas). */
class EmBreveFragment : Fragment(R.layout.fragment_em_breve) {

    companion object {
        fun novo(titulo: String, icone: Int) = EmBreveFragment().apply {
            arguments = Bundle().apply { putString("titulo", titulo); putInt("icone", icone) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.txtTitulo).text = arguments?.getString("titulo") ?: ""
        view.findViewById<ImageView>(R.id.ico).setImageResource(arguments?.getInt("icone") ?: R.drawable.ic_peixe)
    }
}
