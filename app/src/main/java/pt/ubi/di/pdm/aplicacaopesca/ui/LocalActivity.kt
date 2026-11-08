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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import pt.ubi.di.pdm.aplicacaopesca.MainActivity
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Especie
import pt.ubi.di.pdm.aplicacaopesca.data.Estado
import pt.ubi.di.pdm.aplicacaopesca.data.Pesqueiro
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.util.comoSinal
import pt.ubi.di.pdm.aplicacaopesca.util.cor
import pt.ubi.di.pdm.aplicacaopesca.util.corForte
import pt.ubi.di.pdm.aplicacaopesca.util.corFundo
import pt.ubi.di.pdm.aplicacaopesca.util.corTexto
import pt.ubi.di.pdm.aplicacaopesca.util.fundoArredondado
import pt.ubi.di.pdm.aplicacaopesca.util.icone
import java.util.Locale
import kotlin.math.abs

/**
 * Informação do local (abre ao tocar numa bola do mapa ou num cartão):
 * - separadores «O que pode pescar» e «Peixedex» do local (desbloquear vale em todos os locais);
 * - onde fica (distrito, concelho, bacia, ponto de referência, coordenadas);
 * - licenças necessárias com sinal verde / laranja / vermelho e botão para adicionar.
 */
class LocalActivity : AppCompatActivity() {

    companion object { const val EXTRA_ID = "pesqueiro_id" }

    private lateinit var p: Pesqueiro
    private var separadorPeixedex = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_local)
        Repositorio.iniciar(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top, bottom = s.bottom)
            insets
        }

        val id = intent.getStringExtra(EXTRA_ID)
        val encontrado = id?.let { Repositorio.pesqueiro(it) }
        if (encontrado == null) { finish(); return }
        p = encontrado
        separadorPeixedex = savedInstanceState?.getBoolean("peixedex") ?: false

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<View>(R.id.btnEditarLocal).apply {
            if (p.doUtilizador) {
                visibility = View.VISIBLE
                setOnClickListener {
                    startActivity(Intent(this@LocalActivity, NovoPesqueiroActivity::class.java)
                        .putExtra(NovoPesqueiroActivity.EXTRA_ID, p.id))
                }
            }
        }
        findViewById<MaterialButtonToggleGroup>(R.id.grupoTab).apply {
            check(if (separadorPeixedex) R.id.tabPeixedex else R.id.tabEspecies)
            addOnButtonCheckedListener { _, bid, marcado ->
                if (marcado) { separadorPeixedex = bid == R.id.tabPeixedex; desenharEspecies() }
            }
        }
        findViewById<MaterialButton>(R.id.btnVerMapa).setOnClickListener { MainActivity.abrirNoMapa(this, p.id) }
        findViewById<MaterialButton>(R.id.btnRegistar).setOnClickListener {
            MainActivity.abrirCaptura(this, pesqueiro = p.id)
        }
        desenharCabecalhoEOndeFica()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("peixedex", separadorPeixedex)
    }

    override fun onResume() {
        super.onResume()
        if (!::p.isInitialized) return
        desenharEspecies()
        desenharLicencas()
    }

    private fun desenharCabecalhoEOndeFica() {
        findViewById<TextView>(R.id.txtTipo).setText(p.tipo.rotulo)
        findViewById<TextView>(R.id.txtAgua).text = getString(p.agua.rotulo)
        findViewById<TextView>(R.id.txtNome).text = p.nome
        val partes = p.regiao.split(" · ")
        val distrito = if (partes.size > 1) partes[0] else Catalogo.distritoDe(p.lat, p.lon).nome
        val concelho = if (partes.size > 1) partes[1] else p.regiao
        findViewById<TextView>(R.id.txtMeta).text = getString(R.string.local_meta, concelho, distrito)

        val linhas = findViewById<LinearLayout>(R.id.linhasOndeFica)
        linhas.removeAllViews()
        Vistas.linhaInfo(layoutInflater, linhas, R.drawable.ic_pin, getString(R.string.local_distrito_concelho), "$distrito · $concelho")
        Vistas.linhaInfo(layoutInflater, linhas, R.drawable.ic_agua, getString(R.string.local_rio_bacia), p.bacia ?: "—")
        p.referencia?.let { Vistas.linhaInfo(layoutInflater, linhas, R.drawable.ic_pin, getString(R.string.local_ponto_referencia), it) }
        val coords = String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon) + "\n" +
            gms(p.lat, getString(R.string.pt_norte), getString(R.string.pt_sul)) + " · " +
            gms(p.lon, getString(R.string.pt_este), getString(R.string.pt_oeste))
        Vistas.linhaInfo(layoutInflater, linhas, R.drawable.ic_mapa,
            getString(if (p.aproximada) R.string.local_coordenadas_aprox else R.string.local_coordenadas), coords)
    }

    /** 39.5428 → 39°32′34.1″ N */
    private fun gms(v: Double, pos: String, neg: String): String {
        val a = abs(v); val g = a.toInt(); val mFull = (a - g) * 60; val m = mFull.toInt(); val s = (mFull - m) * 60
        return String.format(Locale.US, "%d°%02d′%04.1f″ %s", g, m, s, if (v >= 0) pos else neg)
    }

    private fun desenharEspecies() {
        val esp = p.especies.map { Catalogo.especie(it) }
        val desbloqueadas = Repositorio.desbloqueadas()
        val n = esp.count { it.id in desbloqueadas }
        findViewById<MaterialButton>(R.id.tabPeixedex).text = getString(R.string.local_tab_peixedex, n, esp.size)
        val resumo = findViewById<TextView>(R.id.txtResumoEspecies)
        val barra = findViewById<LinearProgressIndicator>(R.id.barraDex)
        val dica = findViewById<View>(R.id.txtDicaDex)
        if (separadorPeixedex) {
            resumo.text = getString(R.string.local_desbloqueadas_aqui, n, esp.size)
            barra.visibility = View.VISIBLE; dica.visibility = View.VISIBLE
            barra.progress = if (esp.isEmpty()) 0 else n * 100 / esp.size
        } else {
            val peixes = esp.count { !it.crustaceo }; val crust = esp.size - peixes
            val contagens = mutableListOf(resources.getQuantityString(R.plurals.n_peixes, peixes, peixes))
            if (crust > 0) contagens += resources.getQuantityString(R.plurals.n_crustaceos, crust, crust)
            resumo.text = getString(R.string.local_especies_resumo, contagens.joinToString(" · "))
            barra.visibility = View.GONE; dica.visibility = View.GONE
        }
        Vistas.grelhaEspecies(layoutInflater, findViewById(R.id.grelhaEspecies), esp, desbloqueadas, separadorPeixedex) { e -> mostrarEspecie(e) }
        findViewById<View>(R.id.txtNotaLagostim).visibility =
            if (!separadorPeixedex && "lagostim" in p.especies) View.VISIBLE else View.GONE
    }

    private fun mostrarEspecie(e: Especie) {
        startActivity(Intent(this, FichaEspecieActivity::class.java)
            .putExtra(FichaEspecieActivity.EXTRA_ID, e.id))
    }

    private fun desenharLicencas() {
        val est = Repositorio.estadoPesqueiro(p)
        val n = est.itens.size
        findViewById<TextView>(R.id.txtEmOrdem).text = getString(R.string.local_em_ordem, n - est.emFalta, n)

        val (titulo, texto) = when (est.estado) {
            Estado.OK -> getString(R.string.local_pode_pescar) to resources.getQuantityString(R.plurals.local_licencas_validas, n, n)
            Estado.AVISO -> getString(R.string.local_pode_atencao) to getString(R.string.local_licenca_a_expirar)
            Estado.ERRO -> resources.getQuantityString(R.plurals.local_licencas_em_falta, est.emFalta, est.emFalta) to
                getString(R.string.local_nao_pode_pescar)
        }
        findViewById<View>(R.id.alerta).fundoArredondado(est.estado.corFundo(), 16f, est.estado.corForte(), 1.5f)
        findViewById<ImageView>(R.id.icoAlerta).apply { setImageResource(est.estado.icone()); setColorFilter(cor(est.estado.corTexto())) }
        findViewById<TextView>(R.id.txtAlertaTitulo).apply { text = titulo; setTextColor(cor(est.estado.corTexto())) }
        findViewById<TextView>(R.id.txtAlertaTexto).apply { text = texto; setTextColor(cor(est.estado.corTexto())) }
        findViewById<View>(R.id.sinalTopo).comoSinal(est.estado)
        findViewById<ImageView>(R.id.icoSinalTopo).setImageResource(est.estado.icone())

        val lista = findViewById<LinearLayout>(R.id.listaExigencias)
        lista.removeAllViews()
        est.itens.forEach { ex ->
            Vistas.exigencia(layoutInflater, lista, ex,
                aoAdicionar = { tipo -> LicencaDialogo.mostrar(this, tipoInicial = tipo) { desenharLicencas() } },
                aoEditar = { idLic ->
                    Repositorio.licencas().firstOrNull { it.id == idLic }?.let { l ->
                        LicencaDialogo.mostrar(this, existente = l) { desenharLicencas() }
                    }
                })
        }
        // registar captura só com todas as licenças em ordem
        findViewById<MaterialButton>(R.id.btnRegistar).apply {
            isEnabled = est.estado != Estado.ERRO
            alpha = if (isEnabled) 1f else 0.45f
        }
    }
}
