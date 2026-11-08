package pt.ubi.di.pdm.aplicacaopesca.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import pt.ubi.di.pdm.aplicacaopesca.MainActivity
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Especie
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.util.dpi

/**
 * Ficha da espécie.
 * Mostra a ilustração (silhueta se ainda não desbloqueada), nome, latim,
 * habitat, época, tamanho, iscos e sinais de identificação. Também lista
 * os pesqueiros onde a espécie existe e permite partilhar ou abrir a
 * câmara para desbloquear.
 */
class FichaEspecieActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "especie_id"
        /** true = vem da Peixedex (respeita silhueta se ainda não desbloqueada). */
        const val EXTRA_PEIXEDEX = "peixedex"
    }

    private lateinit var e: Especie

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ficha_especie)
        Repositorio.iniciar(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top, bottom = s.bottom)
            insets
        }

        val id = intent.getStringExtra(EXTRA_ID)
        val existente = id?.let { runCatching { Catalogo.especie(it) }.getOrNull() }
        if (existente == null) { finish(); return }
        e = existente

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (!::e.isInitialized) return
        desenhar()
    }

    private fun desenhar() {
        // Peixedex = modo desafio (silhueta até desbloquear); caso contrário revela tudo (enciclopédia).
        val modoPeixedex = intent.getBooleanExtra(EXTRA_PEIXEDEX, false)
        val desbloqueadas = if (modoPeixedex) Repositorio.desbloqueadas()
            else Catalogo.especies.map { it.id }.toSet()
        val temEsta = e.id in desbloqueadas

        val chipsSalto = findViewById<ChipGroup>(R.id.chipsSalto)
        chipsSalto.removeAllViews()
        Catalogo.especies.forEach { esp ->
            val tem = esp.id in desbloqueadas
            val nome = if (tem) esp.nome else "???"
            val chip = Chip(this).apply {
                text = getString(R.string.ficha_chip_numero, esp.numero, nome)
                isCheckable = true
                isChecked = esp.id == e.id
                setOnClickListener {
                    if (esp.id != e.id) {
                        startActivity(Intent(this@FichaEspecieActivity, FichaEspecieActivity::class.java)
                            .putExtra(EXTRA_ID, esp.id)
                            .putExtra(EXTRA_PEIXEDEX, intent.getBooleanExtra(EXTRA_PEIXEDEX, false)))
                        finish()
                    }
                }
            }
            chipsSalto.addView(chip)
        }

        // Ilustração · fotografia real cobre-a quando a espécie está desbloqueada
        val pv = findViewById<PeixeView>(R.id.peixe)
        val img = findViewById<ImageView>(R.id.imgFoto)
        pv.especieId = e.id
        pv.proporcao = 9f / 16f
        pv.raio = dpi(16).toFloat()
        pv.modo = if (temEsta) PeixeView.Modo.NORMAL else PeixeView.Modo.SILHUETA
        val foto = Vistas.fotoRealDe(this, e.id)
        if (temEsta && foto != 0) {
            pv.visibility = View.GONE
            img.visibility = View.VISIBLE
            img.setImageResource(foto)
        } else {
            pv.visibility = View.VISIBLE
            img.visibility = View.GONE
        }

        findViewById<TextView>(R.id.txtNome).text =
            if (temEsta) e.nome else getString(R.string.ficha_por_descobrir)
        findViewById<TextView>(R.id.txtLatim).text =
            if (temEsta) e.latim else getString(R.string.ficha_por_descobrir_sub, String.format("%03d", e.numero))

        val cartao = findViewById<LinearLayout>(R.id.cartaoInfo)
        cartao.removeAllViews()
        Vistas.linhaInfo(layoutInflater, cartao, R.drawable.ic_agua,
            if (temEsta) getString(R.string.ficha_habitat) else getString(R.string.ficha_habitat_pista),
            e.habitat)
        linha(cartao, R.drawable.ic_calendario, getString(R.string.ficha_epoca), e.epoca, !temEsta)
        linha(cartao, R.drawable.ic_regua, getString(R.string.ficha_tamanho), e.tamanho, !temEsta)
        linha(cartao, R.drawable.ic_anzol, getString(R.string.ficha_iscos), e.iscos.joinToString(" · "), !temEsta)
        if (temEsta) {
            Vistas.linhaInfo(layoutInflater, cartao, R.drawable.ic_olho, getString(R.string.ficha_identificar),
                e.identificar.joinToString("\n") { "• $it" })
        }

        val onde = findViewById<ChipGroup>(R.id.chipsOnde)
        val titulo = findViewById<TextView>(R.id.tituloOnde)
        onde.removeAllViews()
        val zonas = Repositorio.zonasDe(e.id)
        if (zonas.isEmpty()) {
            titulo.visibility = View.GONE
            onde.visibility = View.GONE
        } else {
            titulo.visibility = View.VISIBLE
            onde.visibility = View.VISIBLE
            zonas.forEach { p ->
                val chip = Chip(this).apply {
                    text = p.curto
                    isCheckable = false
                    setOnClickListener {
                        startActivity(Intent(this@FichaEspecieActivity, LocalActivity::class.java)
                            .putExtra(LocalActivity.EXTRA_ID, p.id))
                    }
                }
                onde.addView(chip)
            }
        }

        findViewById<MaterialButton>(R.id.btnPartilhar).setOnClickListener {
            Partilha.partilhar(this, Partilha.textoEspecie(this, e), getString(R.string.ficha_partilhar))
        }
        val btnFoto = findViewById<MaterialButton>(R.id.btnFotografar)
        btnFoto.text = getString(if (temEsta) R.string.ficha_registar else R.string.ficha_fotografar)
        btnFoto.setOnClickListener {
            val zona = zonas.firstOrNull()?.id
            MainActivity.abrirCaptura(this, pesqueiro = zona, especie = e.id)
        }
    }

    private fun linha(pai: LinearLayout, icone: Int, rotulo: String, valor: String, esbater: Boolean) {
        val v = Vistas.linhaInfo(layoutInflater, pai, icone, rotulo, valor)
        if (esbater) {
            v.findViewById<TextView>(R.id.txtValor).alpha = 0.55f
        }
    }
}
