package pt.ubi.di.pdm.aplicacaopesca.ui

import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import pt.ubi.di.pdm.aplicacaopesca.R
import pt.ubi.di.pdm.aplicacaopesca.data.Catalogo
import pt.ubi.di.pdm.aplicacaopesca.data.Estado
import pt.ubi.di.pdm.aplicacaopesca.data.EstadoPesqueiro
import pt.ubi.di.pdm.aplicacaopesca.data.Pesqueiro
import pt.ubi.di.pdm.aplicacaopesca.data.Repositorio
import pt.ubi.di.pdm.aplicacaopesca.util.comoEtiqueta
import pt.ubi.di.pdm.aplicacaopesca.util.cor
import java.text.Normalizer

/**
 * Pesqueiros: pesquisa por nome/espécie, filtros (Licença válida, A expirar),
 * ordenação, lista em páginas de 3 (sem scroll infinito) ou mapa de Portugal.
 */
class PesqueirosFragment : Fragment(R.layout.fragment_pesqueiros) {

    private enum class Ordem(@StringRes val rotulo: Int) {
        NOME(R.string.ordem_nome), LICENCA(R.string.ordem_licenca), VISITAS(R.string.ordem_visitas)
    }

    private val POR_PAGINA = 3

    // estado do ecrã (sobrevive à rotação através de onSaveInstanceState)
    private var pesquisa = ""
    private var ordem = Ordem.NOME
    private var pagina = 0
    private var vistaMapa = false
    private var focarPendente: String? = null

    private lateinit var txtContagem: TextView
    private lateinit var lista: LinearLayout
    private lateinit var paginacao: LinearLayout
    private lateinit var txtPagina: TextView
    private lateinit var zonaLista: View
    private lateinit var zonaMapa: View
    private lateinit var vazio: View
    private lateinit var mapa: MapaPortugalView
    private var fValida = false
    private var fExpirar = false
    private lateinit var btnFiltros: MaterialButton
    private lateinit var rolagem: NestedScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            pesquisa = it.getString("pesquisa", "")
            ordem = Ordem.entries[it.getInt("ordem", 0)]
            pagina = it.getInt("pagina", 0)
            vistaMapa = it.getBoolean("mapa", false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pesquisa", pesquisa)
        outState.putInt("ordem", ordem.ordinal)
        outState.putInt("pagina", pagina)
        outState.putBoolean("mapa", vistaMapa)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rolagem = view.findViewById(R.id.rolagem)
        txtContagem = view.findViewById(R.id.txtContagem)
        lista = view.findViewById(R.id.lista)
        paginacao = view.findViewById(R.id.paginacao)
        txtPagina = view.findViewById(R.id.txtPagina)
        zonaLista = view.findViewById(R.id.zonaLista)
        zonaMapa = view.findViewById(R.id.zonaMapa)
        vazio = view.findViewById(R.id.vazio)
        mapa = view.findViewById(R.id.mapa)
        btnFiltros = view.findViewById(R.id.btnFiltros)

        view.findViewById<TextInputEditText>(R.id.edtPesquisa).apply {
            setText(pesquisa)
            doAfterTextChanged { pesquisa = it?.toString() ?: ""; pagina = 0; atualizar() }
        }
        btnFiltros.setOnClickListener { b ->
            PopupMenu(requireContext(), b).apply {
                menu.add(0, 2, 2, getString(R.string.chip_valida)).apply { isCheckable = true; isChecked = fValida }
                menu.add(0, 3, 3, getString(R.string.chip_expirar)).apply { isCheckable = true; isChecked = fExpirar }
                setOnMenuItemClickListener { item ->
                    when (item.itemId) { 2 -> fValida = !fValida; 3 -> fExpirar = !fExpirar }
                    pagina = 0; atualizar(); atualizarFiltros(); true
                }
                show()
            }
        }
        atualizarFiltros()
        view.findViewById<MaterialButton>(R.id.btnOrdenar).setOnClickListener { b ->
            PopupMenu(requireContext(), b).apply {
                Ordem.entries.forEachIndexed { i, o -> menu.add(0, i, i, (if (o == ordem) "✓ " else "") + getString(o.rotulo)) }
                setOnMenuItemClickListener { ordem = Ordem.entries[it.itemId]; pagina = 0; atualizar(); true }
                show()
            }
        }
        view.findViewById<MaterialButtonToggleGroup>(R.id.grupoVista).apply {
            check(if (vistaMapa) R.id.btnMapa else R.id.btnLista)
            addOnButtonCheckedListener { _, id, marcado ->
                if (marcado) { vistaMapa = id == R.id.btnMapa; pagina = 0; atualizar() }
            }
        }
        view.findViewById<Button>(R.id.btnLimpar).setOnClickListener {
            view.findViewById<TextInputEditText>(R.id.edtPesquisa).setText("")
            fValida = false; fExpirar = false
            atualizarFiltros(); atualizar()
        }

        mapa.aoTocarPesqueiro = { p -> abrirLocal(p) }
        view.findViewById<ImageButton>(R.id.btnZoomMais).setOnClickListener { mapa.aproximar() }
        view.findViewById<ImageButton>(R.id.btnZoomMenos).setOnClickListener { mapa.afastar() }
        view.findViewById<ImageButton>(R.id.btnZoomRepor).setOnClickListener { mapa.repor(); mapa.selecionado = null }
        view.findViewById<TextView>(R.id.txtLegenda).text = legenda()
    }

    override fun onResume() {
        super.onResume()
        mapa.mostrarDistritos = Repositorio.mostrarDistritos
        atualizar()    // os dados podem ter mudado (ex.: adicionou uma licença noutro ecrã)
        focarPendente?.let { focarAgora(it) }
    }

    fun focarNoMapa(id: String) {
        focarPendente = id
        if (isResumed) focarAgora(id)
    }

    private fun focarAgora(id: String) {
        focarPendente = null
        val p = Repositorio.pesqueiro(id) ?: return
        if (!vistaMapa) view?.findViewById<MaterialButtonToggleGroup>(R.id.grupoVista)?.check(R.id.btnMapa)
        vistaMapa = true
        atualizar()
        mapa.focar(p)
        rolagem.post { rolagem.smoothScrollTo(0, zonaMapa.top) }
    }

    private fun normalizar(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    private fun visiveis(estados: Map<String, EstadoPesqueiro>): List<Pesqueiro> {
        val q = normalizar(pesquisa.trim())
        val visitas = Repositorio.capturas().groupBy { it.pesqueiro }.mapValues { (_, l) -> l.map { it.data }.toSet().size }
        var l = Repositorio.pesqueiros().filter { p ->
            val est = estados.getValue(p.id).estado
            (q.isEmpty() || normalizar(p.nome + " " + p.regiao + " " + getString(p.tipo.rotulo) + " " +
                p.especies.joinToString(" ") { Catalogo.especie(it).nome }).contains(q)) &&
                (!(fValida || fExpirar) ||
                    (fValida && est == Estado.OK) || (fExpirar && est == Estado.AVISO))
        }
        l = when (ordem) {
            Ordem.NOME -> l.sortedBy { normalizar(it.nome) }
            Ordem.LICENCA -> l.sortedWith(compareBy({ estados.getValue(it.id).estado.peso() }, { estados.getValue(it.id).menorPrazo ?: 0L }))
            Ordem.VISITAS -> l.sortedWith(compareByDescending<Pesqueiro> { visitas[it.id] ?: 0 }.thenBy { normalizar(it.nome) })
        }
        return l
    }

    private fun Estado.peso() = when (this) { Estado.ERRO -> 0; Estado.AVISO -> 1; Estado.OK -> 2 }

    private fun atualizarFiltros() {
        val n = listOf(fValida, fExpirar).count { it }
        if (!::btnFiltros.isInitialized) return
        btnFiltros.text = if (n > 0) n.toString() else ""
        val cor = if (n > 0) R.color.cp_primaria else R.color.cp_texto
        btnFiltros.setIconTintResource(cor)
        btnFiltros.setTextColor(requireContext().cor(cor))
    }

    private fun atualizar() {
        if (view == null) return
        val licencas = Repositorio.licencas()
        val estados = Repositorio.pesqueiros().associate { it.id to Repositorio.estadoPesqueiro(it, licencas) }
        val vs = visiveis(estados)
        txtContagem.text = resources.getQuantityString(R.plurals.n_pesqueiros, vs.size, vs.size) + " · " + getString(ordem.rotulo)
        vazio.visibility = if (vs.isEmpty()) View.VISIBLE else View.GONE
        zonaLista.visibility = if (!vistaMapa && vs.isNotEmpty()) View.VISIBLE else View.GONE
        zonaMapa.visibility = if (vistaMapa && vs.isNotEmpty()) View.VISIBLE else View.GONE
        if (vistaMapa) mapa.definir(vs.map { MapaPortugalView.Bola(it, estados.getValue(it.id).estado) })
        else desenharLista(vs, estados)
    }

    private fun desenharLista(vs: List<Pesqueiro>, estados: Map<String, EstadoPesqueiro>) {
        val paginas = maxOf(1, (vs.size + POR_PAGINA - 1) / POR_PAGINA)
        pagina = pagina.coerceIn(0, paginas - 1)
        val inicio = pagina * POR_PAGINA
        val itens = vs.subList(inicio, minOf(vs.size, inicio + POR_PAGINA))

        lista.removeAllViews()
        val inf = layoutInflater
        itens.forEach { p -> lista.addView(cartao(inf, p, estados.getValue(p.id))) }

        // botões ‹ 1 2 3 ›
        paginacao.removeAllViews()
        if (paginas > 1) {
            fun botao(texto: String, icone: Int?, destino: Int, ativo: Boolean, atual: Boolean) {
                val b = inf.inflate(R.layout.item_pagina, paginacao, false) as MaterialButton
                b.text = texto
                if (icone != null) { b.setIconResource(icone); b.iconPadding = 0; b.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START }
                b.isEnabled = ativo
                b.alpha = if (ativo) 1f else 0.35f
                if (atual) {
                    b.setBackgroundColor(requireContext().cor(R.color.cp_primaria))
                    b.setTextColor(requireContext().cor(R.color.white))
                }
                b.setOnClickListener { irPara(destino) }
                paginacao.addView(b)
            }
            botao("", R.drawable.ic_voltar, pagina - 1, pagina > 0, false)
            for (i in 0 until paginas) botao("${i + 1}", null, i, true, i == pagina)
            botao("", R.drawable.ic_seguinte, pagina + 1, pagina < paginas - 1, false)
            txtPagina.text = getString(R.string.pagina_info, pagina + 1, paginas, inicio + 1, inicio + itens.size, vs.size)
            txtPagina.visibility = View.VISIBLE
        } else txtPagina.visibility = View.GONE
    }

    private fun irPara(p: Int) {
        pagina = p
        atualizar()
        rolagem.post { rolagem.smoothScrollTo(0, 0) }
    }

    private fun cartao(inf: android.view.LayoutInflater, p: Pesqueiro, est: EstadoPesqueiro): View {
        val v = inf.inflate(R.layout.item_pesqueiro, lista, false)
        Vistas.circulo(v.findViewById(R.id.bola), if (est.estado == Estado.ERRO) R.color.cp_cinza else R.color.cp_primaria)
        v.findViewById<TextView>(R.id.txtNome).text = p.nome
        v.findViewById<TextView>(R.id.txtRegiao).text = p.regiao
        v.findViewById<TextView>(R.id.txtLicenca).text = Catalogo.tipoLicenca(p.exige.first()).nome +
            if (p.exige.size > 1) " + ${p.exige.size - 1}" else ""
        val tags = v.findViewById<ChipGroup>(R.id.tags)
        p.especies.forEach { tags.addView(Vistas.etiqueta(requireContext(), Catalogo.especie(it).nome)) }
        v.findViewById<TextView>(R.id.txtEstado).comoEtiqueta(est.estado, textoEstado(est))
        v.setOnClickListener { abrirLocal(p) }
        return v
    }

    private fun textoEstado(est: EstadoPesqueiro): String = when (est.estado) {
        Estado.OK -> getString(R.string.pesq_licenca_valida)
        Estado.AVISO -> est.menorPrazo.let { d ->
            if (d == 0L) getString(R.string.pesq_licenca_expira_hoje)
            else getString(R.string.pesq_licenca_expira_em, resources.getQuantityString(R.plurals.n_dias, (d ?: 0L).toInt(), d ?: 0L))
        }
        Estado.ERRO -> getString(if (est.temLicencaEmFalta) R.string.pesq_falta_licenca else R.string.pesq_licenca_expirada)
    }

    private fun legenda(): CharSequence {
        val ctx = requireContext()
        val sb = SpannableStringBuilder()
        listOf(R.color.cp_ok to R.string.legenda_tem, R.color.cp_aviso to R.string.chip_expirar, R.color.cp_erro to R.string.legenda_falta)
            .forEachIndexed { i, (c, t) ->
                if (i > 0) sb.append("\n")
                val ini = sb.length
                sb.append("● ")
                sb.setSpan(ForegroundColorSpan(ctx.cor(c)), ini, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append(ctx.getString(t))
            }
        return sb
    }

    private fun abrirLocal(p: Pesqueiro) {
        startActivity(Intent(requireContext(), LocalActivity::class.java).putExtra(LocalActivity.EXTRA_ID, p.id))
    }
}
