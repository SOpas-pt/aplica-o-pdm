package pt.ubi.di.pdm.aplicacaopesca.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Agua
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Especie
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio

/**
 * Guia de espécies.
 * Mostra todas as espécies do catálogo, com filtros por tipo de água
 * (doce, salgada, salobra) e um filtro separado para crustáceos.
 */
class GuiaActivity : AppCompatActivity() {

    private enum class Filtro(@StringRes val etiqueta: Int) {
        TODAS(R.string.guia_todas), DOCE(R.string.agua_tipo_doce), SALGADA(R.string.agua_tipo_salgada),
        SALOBRA(R.string.agua_salobra), CRUST(R.string.guia_crustaceos)
    }

    private var filtro = Filtro.TODAS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_guia)
        Repositorio.iniciar(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top, bottom = s.bottom)
            insets
        }
        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }

        val chips = findViewById<ChipGroup>(R.id.chipsFiltro)
        Filtro.entries.forEach { f ->
            val c = Chip(this).apply {
                setText(f.etiqueta)
                isCheckable = true
                isChecked = f == filtro
                setOnClickListener { filtro = f; isChecked = true; atualizar() }
            }
            chips.addView(c)
        }
        atualizar()
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun atualizar() {
        val especies = Catalogo.especies.filter { e ->
            when (filtro) {
                Filtro.TODAS -> true
                Filtro.DOCE -> e.agua == Agua.DOCE && !e.crustaceo
                Filtro.SALGADA -> e.agua == Agua.SALGADA
                Filtro.SALOBRA -> e.agua == Agua.SALOBRA
                Filtro.CRUST -> e.crustaceo
            }
        }
        findViewById<TextView>(R.id.txtContagem).text =
            resources.getQuantityString(R.plurals.n_especies, especies.size, especies.size)
        // Guia = enciclopédia: todas as espécies aparecem sempre desbloqueadas (sem cadeado).
        Vistas.grelhaEspecies(
            layoutInflater,
            findViewById(R.id.grelha),
            especies,
            Catalogo.especies.map { it.id }.toSet(),
            peixedex = false,
            comFotoReal = true
        ) { e -> abrirFicha(e) }
    }

    private fun abrirFicha(e: Especie) {
        startActivity(Intent(this, FichaEspecieActivity::class.java)
            .putExtra(FichaEspecieActivity.EXTRA_ID, e.id))
    }
}
