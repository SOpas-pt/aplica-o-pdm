package pt.ubi.di.pdm.aplicacaopesca.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Conquista
import pt.ubi.di.pdm.aplicacaopesca.data.DadosConquistas
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio

/** Conquistas: quantas já tens e o progresso de cada uma (feitas primeiro). */
class ConquistasActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_conquistas)
        Repositorio.iniciar(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top, bottom = s.bottom)
            insets
        }
        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        val dados = DadosConquistas(Repositorio.capturas(), Repositorio.desbloqueadas().size, Catalogo.especies.size)
        val progressos = Conquista.todas(dados).sortedByDescending { it.feita }
        val feitas = progressos.count { it.feita }
        findViewById<TextView>(R.id.txtResumo).text = getString(R.string.conq_progresso, feitas, progressos.size)
        findViewById<CircularProgressIndicator>(R.id.anel).progress = feitas * 100 / progressos.size

        val lista = findViewById<LinearLayout>(R.id.listaConquistas)
        lista.removeAllViews()
        progressos.forEach { p ->
            val item = layoutInflater.inflate(R.layout.item_conquista, lista, false)
            item.findViewById<TextView>(R.id.txtTitulo).setText(p.conquista.titulo)
            item.findViewById<TextView>(R.id.txtDescricao).setText(p.conquista.descricao)
            item.findViewById<TextView>(R.id.txtValor).text = if (p.feita) getString(R.string.conq_feita) else "${p.atual}/${p.meta}"
            item.findViewById<LinearProgressIndicator>(R.id.barra).progress = p.atual * 100 / p.meta
            item.alpha = if (p.feita) 1f else 0.6f
            item.findViewById<View>(R.id.icone).alpha = if (p.feita) 1f else 0.3f
            lista.addView(item)
        }
    }
}
